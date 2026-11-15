package com.example.hacklaguna2026.ai

import android.app.ActivityManager
import android.content.Context
import com.example.hacklaguna2026.ai.CielRequestKind.*
import com.example.hacklaguna2026.bible.BiblePassage
import com.example.hacklaguna2026.bible.BibleReference
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import java.util.concurrent.Executors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

interface LocalAIProvider : AiProvider {
    val isModelLoaded: Boolean get() = false
    suspend fun unload()
}

interface StreamingAiProvider : AiProvider {
    suspend fun replyStreaming(
        question: String,
        history: List<Pair<String, String>>,
        passages: List<BiblePassage>,
        onPartialResponse: (String) -> Unit
    ): ChatReply

    override suspend fun reply(
        question: String,
        history: List<Pair<String, String>>,
        passages: List<BiblePassage>
    ): ChatReply = replyStreaming(question, history, passages) {}
}

/** Runs Qwen3-0.6B in the Android process with LiteRT-LM; it never sends data over a network. */
class LiteRtLocalAIProvider(context: Context, private val modelManager: LocalModelManager) : LocalAIProvider, StreamingAiProvider {
    private val appContext = context.applicationContext
    private val runtimeDispatcher = Executors.newSingleThreadExecutor { task ->
        Thread(task, "Ciel-LiteRT").apply { isDaemon = true }
    }.asCoroutineDispatcher()
    private val engineMutex = Mutex()
    @Volatile private var loadedEngine: Engine? = null
    override val isModelLoaded: Boolean get() = loadedEngine != null

    override suspend fun unload() = withContext(runtimeDispatcher) {
        engineMutex.withLock { closeLoadedEngine() }
    }

    override suspend fun replyStreaming(
        question: String,
        history: List<Pair<String, String>>,
        passages: List<BiblePassage>,
        onPartialResponse: (String) -> Unit
    ): ChatReply = withContext(runtimeDispatcher) {
        when (CielConversationPolicy.classify(question, history)) {
            PROMPT_INJECTION, GENERAL_KNOWLEDGE, TECHNICAL_REQUEST ->
                return@withContext ChatReply(CielConversationPolicy.REDIRECT, provider = "offline")
            SAFETY_CRISIS ->
                return@withContext ChatReply(CielConversationPolicy.CRISIS_REPLY, provider = "offline", avatarEmotion = AvatarEmotion.WORRIED)
            else -> Unit
        }

        engineMutex.withLock {
            currentCoroutineContext().ensureActive()
            val activeEngine = engine()
            val configuration = ConversationConfig(
                systemInstruction = Contents.of(CielConversationPolicy.systemInstructions(passages)),
                initialMessages = trimHistory(history).map { (role, text) ->
                    if (role == "assistant") Message.model(text) else Message.user(text)
                }
            )
            var conversation: Conversation? = null
            var unloadAfterResponse = false
            try {
                conversation = activeEngine.createConversation(configuration)
                val answer = StringBuilder()
                conversation.sendMessageAsync(
                    question,
                    extraContext = mapOf("enable_thinking" to false),
                    maxOutputToken = MAX_OUTPUT_TOKENS
                ).collect { chunk ->
                    val piece = chunk.toString()
                    if (piece.isNotEmpty()) {
                        answer.append(piece)
                        val visible = visiblePartialResponse(answer.toString())
                        if (visible.isNotBlank()) onPartialResponse(visible)
                    }
                }
                val cleanedAnswer = removePrivateReasoning(answer.toString()).trim()
                if (cleanedAnswer.isBlank()) {
                    throw LocalModelInferenceException("The offline companion couldn't finish a response. Please try again.")
                }
                ChatReply(
                    answer = cleanedAnswer,
                    references = passages.map { BibleReference(it.book, it.chapter, it.verse, it.verse, it.text) },
                    provider = "offline",
                    avatarEmotion = classifyAvatarEmotion(question)
                )
            } catch (error: CancellationException) {
                runCatching { conversation?.cancelProcess() }
                throw error
            } catch (error: OutOfMemoryError) {
                unloadAfterResponse = true
                throw LocalModelInferenceException(
                    "There isn't enough free memory to run Ciel's offline companion right now. Close some other apps and try again.",
                    error
                )
            } catch (error: LocalModelInferenceException) {
                throw error
            } catch (error: Throwable) {
                throw LocalModelInferenceException(
                    "Ciel's offline companion couldn't run on this device. Try restarting the app or reinstalling the offline model.",
                    error
                )
            } finally {
                runCatching { conversation?.close() }
                if (unloadAfterResponse) runCatching { closeLoadedEngine() }
            }
        }
    }

    private fun engine(): Engine {
        loadedEngine?.let { return it }
        if (modelManager.status() != LocalModelStatus.READY) {
            throw LocalModelUnavailableException(
                "Ciel's offline companion needs its ${LocalModelManager.MODEL_DISPLAY_SIZE} model. Open Settings to download it."
            )
        }
        val manager = appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memory = ActivityManager.MemoryInfo().also(manager::getMemoryInfo)
        if (memory.availMem < MINIMUM_AVAILABLE_MEMORY_BYTES) {
            throw LocalModelInferenceException(
                "There isn't enough free memory to start Ciel's offline companion. Close some other apps and try again."
            )
        }
        try {
            return Engine(EngineConfig(
                modelPath = modelManager.modelFile.absolutePath,
                backend = Backend.CPU(),
                cacheDir = appContext.cacheDir.absolutePath
            )).also { it.initialize(); loadedEngine = it }
        } catch (error: OutOfMemoryError) {
            throw LocalModelInferenceException(
                "There isn't enough free memory to start Ciel's offline companion. Close some other apps and try again.",
                error
            )
        } catch (error: Throwable) {
            throw LocalModelInferenceException("Ciel's offline model is unavailable or isn't compatible with this device.", error)
        }
    }

    private fun closeLoadedEngine() {
        loadedEngine?.let { engine ->
            engine.close()
            loadedEngine = null
        }
    }

    private fun trimHistory(history: List<Pair<String, String>>): List<Pair<String, String>> {
        var remainingChars = MAX_HISTORY_CHARS
        return history.asReversed().asSequence()
            .filter { it.first == "user" || it.first == "assistant" }
            .take(MAX_HISTORY_MESSAGES)
            .mapNotNull { (role, content) ->
                val text = content.takeLast(minOf(content.length, MAX_HISTORY_MESSAGE_CHARS, remainingChars))
                remainingChars -= text.length
                if (text.isBlank() || remainingChars < 0) null else role to text
            }
            .toList()
            .asReversed()
    }

    private fun removePrivateReasoning(value: String): String = value
        .replace(Regex("(?is)<think>.*?</think>"), "")
        .replace("<|im_end|>", "")
        .replace("<|endoftext|>", "")

    private fun visiblePartialResponse(value: String): String {
        val withoutClosedReasoning = removePrivateReasoning(value)
        val openReasoning = Regex("(?i)<think>").find(withoutClosedReasoning)?.range?.first
        val visible = if (openReasoning == null) withoutClosedReasoning else withoutClosedReasoning.substring(0, openReasoning)
        return ChatReplyCodec.sanitize(visible)
    }

    companion object {
        private const val MAX_HISTORY_MESSAGES = 8
        private const val MAX_HISTORY_CHARS = 3_000
        private const val MAX_HISTORY_MESSAGE_CHARS = 900
        private const val MINIMUM_AVAILABLE_MEMORY_BYTES = 1_536L * 1024 * 1024
        private const val MAX_OUTPUT_TOKENS = 160
    }
}

class LocalModelUnavailableException(message: String, cause: Throwable? = null) : Exception(message, cause)
class LocalModelInferenceException(message: String, cause: Throwable? = null) : Exception(message, cause)
