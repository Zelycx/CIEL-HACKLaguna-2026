package com.example.hacklaguna2026.ai

import com.example.hacklaguna2026.bible.BiblePassage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
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

private const val MAX_HISTORY_MESSAGES = 24
private const val MAX_HISTORY_MESSAGE_CHARS = 4_000

/** Network failures which mean Groq could not be reached can use the local Bible fallback. */
class OfflineConnectionException(message: String, cause: Throwable) : IOException(message, cause)

/** HTTP responses and non-connectivity transport failures are surfaced to the user. */
class GroqApiException(message: String, val statusCode: Int? = null) : IOException(message)

class GroqChatService internal constructor(
    private val apiKey: String,
    private val endpoint: String = ENDPOINT,
    @Suppress("UNUSED_PARAMETER") debugLogging: Boolean = false
) : GroqAIProvider {
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
            throw OfflineConnectionException(
                "Ciel is offline.",
                IOException("No online chat key is configured.")
            )
        }
        val messages = buildGroqMessages(question, history, passages)
        val bodyText = buildRequestBody(messages)
        validateSerializedRequest(bodyText, question)
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

        val call = client.newCall(request)
        val cancellationHandler = currentCoroutineContext().job.invokeOnCompletion { cause ->
            if (cause != null) call.cancel()
        }
        try {
            call.execute().use { response ->
                val responseBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw GroqApiException(formatHttpError(response.code, responseBody), response.code)
                }
                parseReply(responseBody)
            }
        } catch (error: OfflineConnectionException) {
            throw error
        } catch (error: IOException) {
            currentCoroutineContext().ensureActive()
            if (error is GroqApiException) throw error
            if (error.isConnectivityFailure()) {
                throw OfflineConnectionException(
                    "Ciel is offline.",
                    error
                )
            }
            throw IOException("The Groq request failed before a response could be read: ${error.message ?: "network error"}", error)
        } finally {
            cancellationHandler.dispose()
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
                        .put("avatarEmotion", JSONObject()
                            .put("type", "string")
                            .put("enum", JSONArray(AvatarEmotion.values().map { it.name })))
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
                    .put("required", JSONArray(listOf("answer", "avatarEmotion", "references")))
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
        if (first?.optString("role") != "system" || first.optString("content") != CielConversationPolicy.systemInstructions(emptyList())) {
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
        val emotion = AvatarEmotion.fromWire(structured.optString("avatarEmotion"))
        return ChatReply(answer = answer, references = references, provider = "Ciel", avatarEmotion = emotion)
    }

    private fun formatHttpError(statusCode: Int, @Suppress("UNUSED_PARAMETER") responseBody: String): String = when (statusCode) {
        400 -> "The online service couldn't use that request (HTTP 400). Please try a shorter message."
        401, 403 -> "Online access couldn't verify this key (HTTP $statusCode). Check the key or replace it in Settings."
        404 -> "The online conversation model is unavailable (HTTP 404). Check your connection status in Settings."
        408 -> "The online service timed out (HTTP 408). Please try again."
        429 -> "Groq returned a rate-limit response (HTTP 429). Try again later; Ciel has no separate daily online-message quota."
        in 500..599 -> "The online service is temporarily unavailable (HTTP $statusCode). Please try again later."
        else -> "The online service couldn't complete the request (HTTP $statusCode). Please try again."
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

    private companion object {
        const val ENDPOINT = "https://api.groq.com/openai/v1/chat/completions"
        const val MODEL = "openai/gpt-oss-20b"
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

enum class GroqKeyValidation { CONNECTED, INVALID_KEY, RATE_LIMITED, MODEL_UNAVAILABLE, NETWORK_UNAVAILABLE, SERVICE_UNAVAILABLE, INVALID_FORMAT }

internal const val GROQ_MODEL_ID = "openai/gpt-oss-20b"
private const val GROQ_MODELS_URL = "https://api.groq.com/openai/v1/models"

fun isBasicGroqKeyFormatValid(value: String): Boolean =
    value.startsWith("gsk_") && value.length in 24..256 && value.none(Char::isWhitespace)

suspend fun validateGroqKey(apiKey: String, modelsUrl: String = GROQ_MODELS_URL): GroqKeyValidation = withContext(Dispatchers.IO) {
    if (!isBasicGroqKeyFormatValid(apiKey)) return@withContext GroqKeyValidation.INVALID_FORMAT
    val request = Request.Builder()
        .url(modelsUrl)
        .header("Authorization", "Bearer $apiKey")
        .header("Accept", "application/json")
        .get()
        .build()
    try {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(25, TimeUnit.SECONDS)
            .build()
            .newCall(request)
            .execute()
            .use { response ->
                when {
                    response.code == 401 || response.code == 403 -> GroqKeyValidation.INVALID_KEY
                    response.code == 429 -> GroqKeyValidation.RATE_LIMITED
                    response.code in 500..599 -> GroqKeyValidation.SERVICE_UNAVAILABLE
                    !response.isSuccessful -> GroqKeyValidation.SERVICE_UNAVAILABLE
                    else -> {
                        val models = runCatching {
                            JSONObject(response.body?.string().orEmpty()).optJSONArray("data")
                        }.getOrNull() ?: return@use GroqKeyValidation.SERVICE_UNAVAILABLE
                        if ((0 until models.length()).any { models.optJSONObject(it)?.optString("id") == GROQ_MODEL_ID }) {
                            GroqKeyValidation.CONNECTED
                        } else GroqKeyValidation.MODEL_UNAVAILABLE
                    }
                }
            }
    } catch (error: IOException) {
        if (error.isConnectivityFailure()) GroqKeyValidation.NETWORK_UNAVAILABLE else GroqKeyValidation.SERVICE_UNAVAILABLE
    } catch (_: Exception) {
        GroqKeyValidation.SERVICE_UNAVAILABLE
    }
}

private fun IOException.isConnectivityFailure(): Boolean {
    var current: Throwable? = this
    while (current != null) {
        if (current is UnknownHostException || current is ConnectException || current is NoRouteToHostException || current is SocketTimeoutException) return true
        if (current is SocketException && current.message.orEmpty().contains("network is unreachable", ignoreCase = true)) return true
        current = current.cause
    }
    return false
}

internal fun buildGroqMessages(
    question: String,
    history: List<Pair<String, String>>,
    passages: List<BiblePassage>
): List<GroqChatMessage> {
    require(question.isNotBlank()) { "Write a message before sending." }
    val messages = mutableListOf(GroqChatMessage("system", CielConversationPolicy.systemInstructions(emptyList())))
    if (passages.isNotEmpty()) {
        val context = buildString {
            appendLine("Verified local BSB passages retrieved for this question:")
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
