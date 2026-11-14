package com.example.hacklaguna2026.ai

import android.util.Log
import com.example.hacklaguna2026.BuildConfig
import com.example.hacklaguna2026.bible.BiblePassage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

data class GroqChatMessage(val role: String, val content: String)

private val GROQ_SYSTEM_PROMPT = """
    You are Ciel, a warm, thoughtful Christian companion who helps users reflect with compassion and respect.
    Answer the user's actual question directly. Do not claim to be a pastor, counselor, or human.
    Bible quotations and references are allowed only when they come from the supplied local Berean Standard Bible (BSB) passages.
    Never invent, complete from memory, or guess a Bible quotation or reference. If no supplied passage supports a citation, do not cite or quote one; say when you are unsure.
    The references field must contain only references explicitly represented by the supplied passages. Use an empty array when none apply.
    Return a concise, natural answer and the structured references requested by the response schema.
""".trimIndent()
private const val MAX_HISTORY_MESSAGES = 24
private const val MAX_HISTORY_MESSAGE_CHARS = 4_000

/** Network failures which mean Groq could not be reached can use the local Bible fallback. */
class OfflineConnectionException(message: String, cause: Throwable) : IOException(message, cause)

/** HTTP responses and non-connectivity transport failures are surfaced to the user. */
class GroqApiException(message: String) : IOException(message)

class GroqChatService internal constructor(
    private val apiKey: String,
    private val endpoint: String = ENDPOINT,
    private val debugLogging: Boolean = BuildConfig.DEBUG
) : AiProvider {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(75, TimeUnit.SECONDS)
        .build()

    override suspend fun reply(
        question: String,
        history: List<Pair<String, String>>,
        passages: List<BiblePassage>
    ): ChatReply = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            throw IllegalStateException("Groq API key is missing. Add GROQ_API_KEY to the ignored local.properties file and rebuild the app.")
        }
        val messages = buildGroqMessages(question, history, passages)
        val bodyText = buildRequestBody(messages)
        validateSerializedRequest(bodyText, question)
        if (debugLogging) {
            Log.i(LOG_TAG, "POST $endpoint")
            Log.i(LOG_TAG, "Outgoing JSON body (Authorization excluded):")
            logInChunks(bodyText)
            Log.i(LOG_TAG, "Pre-transmission check: messages=${messages.size}, systemPrompt=true, currentUserMessage=true")
        }

        val body = bodyText.toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url(endpoint)
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(body)
            .build()
        val transmittedBody = request.body?.let { outgoing ->
            okio.Buffer().also(outgoing::writeTo).readUtf8()
        }.orEmpty()
        if (transmittedBody != bodyText || request.header("Authorization") != "Bearer $apiKey") {
            throw IllegalStateException("Groq request validation failed before transmission.")
        }
        validateSerializedRequest(transmittedBody, question)

        try {
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string().orEmpty()
                if (debugLogging) {
                    Log.i(LOG_TAG, "HTTP ${response.code} ${response.message}")
                    Log.i(LOG_TAG, "Response body:")
                    logInChunks(responseBody)
                }
                if (!response.isSuccessful) {
                    throw GroqApiException(formatHttpError(response.code, responseBody))
                }
                parseReply(responseBody)
            }
        } catch (error: OfflineConnectionException) {
            throw error
        } catch (error: IOException) {
            if (error is GroqApiException) throw error
            if (error.isConnectivityFailure()) {
                throw OfflineConnectionException(
                    "Could not connect to Groq. Ciel switched to the offline BSB Bible Assistant.",
                    error
                )
            }
            throw IOException("The Groq request failed before a response could be read: ${error.message ?: "network error"}", error)
        }
    }

    private fun buildRequestBody(messages: List<GroqChatMessage>): String = JSONObject()
        .put("model", MODEL)
        .put("messages", JSONArray().apply {
            messages.forEach { message ->
                put(JSONObject().put("role", message.role).put("content", message.content))
            }
        })
        .put("response_format", JSONObject()
            .put("type", "json_schema")
            .put("json_schema", JSONObject()
                .put("name", "ciel_bible_response")
                .put("strict", true)
                .put("schema", JSONObject()
                    .put("type", "object")
                    .put("properties", JSONObject()
                        .put("answer", JSONObject().put("type", "string"))
                        .put("references", JSONObject()
                            .put("type", "array")
                            .put("items", JSONObject()
                                .put("type", "object")
                                .put("properties", JSONObject()
                                    .put("book", JSONObject().put("type", "string"))
                                    .put("chapter", JSONObject().put("type", "integer"))
                                    .put("startVerse", JSONObject().put("type", "integer"))
                                    .put("endVerse", JSONObject().put("type", "integer")))
                                .put("required", JSONArray(listOf("book", "chapter", "startVerse", "endVerse")))
                                .put("additionalProperties", false))))
                    .put("required", JSONArray(listOf("answer", "references")))
                    .put("additionalProperties", false))))
        .toString()

    private fun validateSerializedRequest(body: String, question: String) {
        if (question.isBlank()) throw IllegalArgumentException("Write a message before sending.")
        val json = runCatching { JSONObject(body) }.getOrElse {
            throw IllegalStateException("The Groq request could not be serialized as JSON.", it)
        }
        val messages = json.optJSONArray("messages")
            ?: throw IllegalStateException("The Groq request has no messages array.")
        if (messages.length() < 2) throw IllegalStateException("The Groq request must contain a system prompt and user message.")
        val first = messages.optJSONObject(0)
        val last = messages.optJSONObject(messages.length() - 1)
        if (first?.optString("role") != "system" || first.optString("content") != GROQ_SYSTEM_PROMPT) {
            throw IllegalStateException("The Groq request is missing Ciel's system prompt.")
        }
        if (last?.optString("role") != "user" || last.optString("content") != question) {
            throw IllegalStateException("The Groq request is missing the current user message.")
        }
        for (index in 0 until messages.length()) {
            val message = messages.optJSONObject(index)
            if (message == null || message.optString("content").isBlank()) {
                throw IllegalStateException("The Groq request contains an empty message.")
            }
        }
    }

    private fun parseReply(responseBody: String): ChatReply {
        val json = try {
            JSONObject(responseBody)
        } catch (error: Exception) {
            throw IOException("Groq returned an unreadable response body.", error)
        }
        val choices = json.optJSONArray("choices")
            ?: throw IOException("Groq's response did not include any choices.")
        val content = choices.optJSONObject(0)
            ?.optJSONObject("message")
            ?.optString("content")
            ?.takeIf(String::isNotBlank)
            ?: throw IOException("Groq returned an empty answer.")
        val structured = try {
            JSONObject(content)
        } catch (error: Exception) {
            throw IOException("Groq returned a response that did not match Ciel's answer format.", error)
        }
        val answer = structured.optString("answer").takeIf(String::isNotBlank)
            ?: throw IOException("Groq's response did not include an answer.")
        val references = structured.optJSONArray("references")?.let(ChatReplyCodec::readReferences).orEmpty()
        return ChatReply(answer = answer, references = references, provider = "Ciel")
    }

    private fun formatHttpError(statusCode: Int, responseBody: String): String {
        val detail = runCatching {
            val root = JSONObject(responseBody)
            root.optJSONObject("error")?.optString("message")
                ?.takeIf(String::isNotBlank)
                ?: root.optString("error").takeIf(String::isNotBlank)
        }.getOrNull()
        val guidance = when (statusCode) {
            401 -> " Check the replacement GROQ_API_KEY in local.properties."
            429 -> " Groq rate limit or quota was reached. Try again later."
            else -> ""
        }
        return "Groq returned HTTP $statusCode${detail?.let { ": ${it.take(400)}"}.orEmpty()}.$guidance"
    }

    private fun IOException.isConnectivityFailure(): Boolean {
        var current: Throwable? = this
        while (current != null) {
            if (current is UnknownHostException || current is ConnectException ||
                current is NoRouteToHostException || current is SocketTimeoutException
            ) return true
            if (current is SocketException && current.message.orEmpty().contains("network is unreachable", ignoreCase = true)) {
                return true
            }
            current = current.cause
        }
        return false
    }

    private fun logInChunks(value: String) {
        value.chunked(3_000).forEachIndexed { index, chunk ->
            Log.i(LOG_TAG, "chunk ${index + 1}: $chunk")
        }
    }

    private companion object {
        const val ENDPOINT = "https://api.groq.com/openai/v1/chat/completions"
        const val MODEL = "openai/gpt-oss-20b"
        const val LOG_TAG = "CielGroqHttp"
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

internal fun buildGroqMessages(
    question: String,
    history: List<Pair<String, String>>,
    passages: List<BiblePassage>
): List<GroqChatMessage> {
    require(question.isNotBlank()) { "Write a message before sending." }
    val messages = mutableListOf(GroqChatMessage("system", GROQ_SYSTEM_PROMPT))
    if (passages.isNotEmpty()) {
        val context = buildString {
            appendLine("Local BSB passages retrieved for this question:")
            passages.forEach { passage ->
                appendLine("- ${passage.reference}: ${passage.text}")
            }
            append("Only quote or cite these supplied passages.")
        }
        messages += GroqChatMessage("system", context)
    }
    history.takeLast(MAX_HISTORY_MESSAGES).forEach { (role, content) ->
        if ((role == "user" || role == "assistant") && content.isNotBlank()) {
            messages += GroqChatMessage(role, content.take(MAX_HISTORY_MESSAGE_CHARS))
        }
    }
    messages += GroqChatMessage("user", question)
    return messages
}
