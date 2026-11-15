package com.example.hacklaguna2026.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.hacklaguna2026.bible.BiblePassage
import com.example.hacklaguna2026.bible.BibleRepository
import com.example.hacklaguna2026.data.ConversationRepository
import com.example.hacklaguna2026.data.MessageEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ChatViewModel(
    private val conversations: ConversationRepository,
    private val bible: BibleRepository,
    private val localProvider: LocalAIProvider,
    private val credentials: GroqCredentialStore,
    private val onlineSettings: OnlineConversationSettings
) : ViewModel() {
    val localModelLoaded: Boolean get() = localProvider.isModelLoaded

    suspend fun unloadLocalModel() = localProvider.unload()

    enum class ChatMode { OFFLINE, ONLINE, SWITCHING_TO_OFFLINE }

    data class SendResult(
        val conversationId: String,
        val messages: List<MessageEntity>,
        val reply: ChatReply,
        val mode: ChatMode,
        val notice: String? = null
    )

    suspend fun send(
        conversationId: String?,
        rawQuestion: String,
        persistUserQuestion: Boolean = true,
        onPartialResponse: (String) -> Unit = {}
    ): SendResult = withContext(Dispatchers.IO) {
        val question = normalizeChatQuestion(rawQuestion)
        val id = conversationId ?: conversations.createConversation(question)
        if (persistUserQuestion) conversations.addUserMessage(id, question)

        val history = conversations.messages(id)
        val turns = buildChatHistory(history, question)
        val kind = CielConversationPolicy.classify(question, turns)
        val passages = if (kind == CielRequestKind.FAITH_AND_BIBLE) {
            val direct = bible.contextualPassages(question)
            if (direct.isNotEmpty()) direct else history.lastOrNull { it.role == "assistant" }
                ?.let { ChatReplyCodec.decode(it.content).references }
                ?.let { bible.resolveSavedReferences(it) }
                .orEmpty()
        } else emptyList()
        val policyReply = when (kind) {
            CielRequestKind.PROMPT_INJECTION, CielRequestKind.GENERAL_KNOWLEDGE, CielRequestKind.TECHNICAL_REQUEST -> ChatReply(
                answer = CielConversationPolicy.REDIRECT,
                provider = "offline"
            )
            CielRequestKind.SAFETY_CRISIS -> ChatReply(
                answer = CielConversationPolicy.CRISIS_REPLY,
                provider = "offline",
                avatarEmotion = AvatarEmotion.WORRIED
            )
            else -> null
        }
        var mode = ChatMode.OFFLINE
        var notice: String? = null
        val generated = policyReply ?: run {
            var credentialReadFailed = false
            val onlineEnabled = onlineSettings.isEnabled()
            val key = if (onlineEnabled) {
                try {
                    credentials.read()
                } catch (_: CredentialStorageException) {
                    credentialReadFailed = true
                    null
                }
            } else null
            if (!shouldUseOnlineChat(onlineEnabled, key != null)) {
                if (credentialReadFailed) notice = "The saved online key couldn't be read. Ciel is continuing offline; check or replace the key in Settings."
                localReply(question, turns, passages, onPartialResponse)
            } else {
                try {
                    GroqChatService(requireNotNull(key)).reply(question, turns, passages).also {
                        mode = ChatMode.ONLINE
                    }
                } catch (error: OfflineConnectionException) {
                    mode = ChatMode.SWITCHING_TO_OFFLINE
                    notice = "The connection dropped, so Ciel is continuing offline."
                    localReply(question, turns, passages, onPartialResponse).copy(fallbackReason = "offline connection")
                } catch (error: GroqApiException) {
                    if (error.statusCode != 429) throw error
                    mode = ChatMode.OFFLINE
                    notice = "Groq returned a rate-limit response (HTTP 429). Ciel is continuing offline for this reply."
                    localReply(question, turns, passages, onPartialResponse)
                        .copy(fallbackReason = "Groq provider rate limit")
                }
            }
        }
        val verified = bible.validateReferences(generated.references, passages)
            .map { it.reference }
        val safeReply = generated.copy(
            answer = ChatReplyCodec.sanitizeBibleClaims(
                CielConversationPolicy.safeOnlineAnswer(generated.answer), passages, bible.bookNames()
            ),
            references = verified,
            avatarEmotion = generated.avatarEmotion ?: classifyAvatarEmotion(question)
        )
        conversations.addAssistantReply(id, safeReply)
        SendResult(id, conversations.messages(id), safeReply, mode, notice)
    }

    private suspend fun localReply(
        question: String,
        history: List<Pair<String, String>>,
        passages: List<com.example.hacklaguna2026.bible.BiblePassage>,
        onPartialResponse: (String) -> Unit
    ): ChatReply = if (localProvider is StreamingAiProvider) {
        localProvider.replyStreaming(question, history, passages, onPartialResponse)
    } else localProvider.reply(question, history, passages)

    class Factory(private val make: () -> ChatViewModel) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ChatViewModel::class.java))
            return make() as T
        }
    }

}

internal fun normalizeChatQuestion(rawQuestion: String): String = rawQuestion.trim().also {
    require(it.isNotEmpty()) { "Write a message before sending." }
}

internal suspend fun replyWithOfflineFallback(
    onlineProvider: AiProvider,
    offlineProvider: AiProvider,
    question: String,
    history: List<Pair<String, String>>,
    passages: List<BiblePassage>
): ChatReply = try {
    onlineProvider.reply(question, history, passages)
} catch (error: OfflineConnectionException) {
    offlineProvider.reply(question, history, passages).copy(
        fallbackReason = error.message?.take(240)
    )
} catch (error: GroqApiException) {
    if (error.statusCode != 429) throw error
    offlineProvider.reply(question, history, passages).copy(
        fallbackReason = "Groq provider rate limit (HTTP 429)"
    )
}

internal fun buildChatHistory(history: List<MessageEntity>, currentQuestion: String): List<Pair<String, String>> {
    val turns = history.asSequence()
        .filter { it.role == "user" || it.role == "assistant" }
        .filter { it.role != "user" || CielConversationPolicy.shouldIncludeUserTurnInHistory(it.content) }
        .map { message ->
            message.role to if (message.role == "assistant") {
                ChatReplyCodec.decode(message.content).answer
            } else message.content
        }
        .filter { it.second.isNotBlank() }
        .toMutableList()
    if (turns.lastOrNull()?.let { it.first == "user" && it.second.trim() == currentQuestion } == true) {
        turns.removeAt(turns.lastIndex)
    }
    return turns.takeLast(24)
}

class OnlineConversationSettings(context: android.content.Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, android.content.Context.MODE_PRIVATE)

    fun isEnabled(): Boolean = preferences.getBoolean(KEY_ENABLED, false)
    fun setEnabled(enabled: Boolean) { preferences.edit().putBoolean(KEY_ENABLED, enabled).apply() }

    companion object {
        const val PREFERENCES = "ciel_online_conversations"
        private const val KEY_ENABLED = "enabled"
    }
}
