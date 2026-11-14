package com.example.hacklaguna2026

import com.example.hacklaguna2026.ai.buildChatHistory
import com.example.hacklaguna2026.ai.buildGroqMessages
import com.example.hacklaguna2026.ai.AiProvider
import com.example.hacklaguna2026.ai.ChatReplyCodec
import com.example.hacklaguna2026.ai.ChatReply
import com.example.hacklaguna2026.ai.GroqApiException
import com.example.hacklaguna2026.ai.GroqChatService
import com.example.hacklaguna2026.ai.OfflineConnectionException
import com.example.hacklaguna2026.ai.normalizeChatQuestion
import com.example.hacklaguna2026.ai.replyWithOfflineFallback
import com.example.hacklaguna2026.bible.BiblePassage
import com.example.hacklaguna2026.data.MessageEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.charset.StandardCharsets

class ChatRequestMessagesTest {
    @Test
    fun consecutiveMessagesKeepConversationHistoryAndExcludeCurrentTurnFromHistory() {
        val conversationId = "conversation"
        val history = mutableListOf<MessageEntity>()
        val firstQuestion = normalizeChatQuestion("  First question?  ")
        assertEquals("First question?", firstQuestion)
        assertEquals(emptyList<Pair<String, String>>(), buildChatHistory(history, firstQuestion))
        history += MessageEntity(conversationId = conversationId, role = "user", content = firstQuestion, createdAt = 2)
        history += MessageEntity(conversationId = conversationId, role = "assistant", content = "First answer.", createdAt = 3)

        val second = buildChatHistory(history, "Second question?")
        assertEquals(listOf("user", "assistant"), second.map { it.first })
        assertEquals(listOf("First question?", "First answer."), second.map { it.second })
        val secondRequest = buildGroqMessages("Second question?", second, emptyList())
        assertEquals("system", secondRequest.first().role)
        assertEquals("Second question?", secondRequest.last().content)
        assertEquals(
            listOf("First question?", "First answer.", "Second question?"),
            secondRequest.filter { it.role == "user" || it.role == "assistant" }.map { it.content }
        )
        history += MessageEntity(conversationId = conversationId, role = "user", content = "Second question?", createdAt = 4)
        history += MessageEntity(conversationId = conversationId, role = "assistant", content = "Second answer.", createdAt = 5)

        val third = buildChatHistory(history, "Third question?")
        assertEquals(listOf("user", "assistant", "user", "assistant"), third.map { it.first })
        assertEquals(listOf("First question?", "First answer.", "Second question?", "Second answer."), third.map { it.second })
    }

    @Test
    fun blankMessageCannotCreateAnEmptyApiRequest() {
        assertThrows(IllegalArgumentException::class.java) {
            normalizeChatQuestion("  ")
        }
        assertThrows(IllegalArgumentException::class.java) {
            buildGroqMessages("  ", emptyList(), emptyList())
        }
    }

    @Test
    fun groqMessagesContainSystemPromptCurrentQuestionAndRetrievedBsbContext() {
        val passage = BiblePassage("John", 3, 16, "For God so loved the world.")
        val messages = buildGroqMessages(
            question = "How can I find hope?",
            history = listOf("user" to "Earlier question", "assistant" to "Earlier answer"),
            passages = listOf(passage)
        )

        assertEquals("system", messages.first().role)
        assertEquals("How can I find hope?", messages.last().content)
        assertTrue(messages.first().content.contains("Never invent"))
        assertTrue(messages.any { it.role == "system" && it.content.contains("John 3:16") && it.content.contains(passage.text) })
        assertEquals(listOf("user", "assistant", "user"), messages.filter { it.role != "system" }.map { it.role })
        assertTrue(messages.all { it.content.isNotBlank() })
    }

    @Test
    fun groqServicePostsOpenAiChatCompletionBodyAndParsesAnswer() = runBlocking {
        val answer = JSONObject()
            .put("answer", "A hopeful answer.")
            .put("references", org.json.JSONArray())
        val response = JSONObject().put("choices", org.json.JSONArray().put(
            JSONObject().put("message", JSONObject().put("content", answer.toString()))
        )).toString()
        LocalHttpStub(200, response).use { server ->
            val service = GroqChatService("test-only-key", server.endpoint, debugLogging = false)
            val result = service.reply(
                question = "How can I find hope?",
                history = listOf("user" to "Earlier question", "assistant" to "Earlier answer"),
                passages = listOf(BiblePassage("John", 3, 16, "For God so loved the world."))
            )

            assertEquals("A hopeful answer.", result.answer)
            val request = server.awaitRequest()
            assertTrue(request.startsWith("POST /openai/v1/chat/completions HTTP/1.1"))
            assertTrue(request.contains("Authorization: Bearer test-only-key"))
            val requestBody = request.substringAfter("\r\n\r\n")
            assertTrue("Authorization must not be serialized into JSON", !requestBody.contains("test-only-key"))
            val json = JSONObject(requestBody)
            assertEquals("openai/gpt-oss-20b", json.getString("model"))
            val messages = json.getJSONArray("messages")
            assertEquals("system", messages.getJSONObject(0).getString("role"))
            assertTrue(messages.getJSONObject(1).getString("content").contains("John 3:16"))
            assertEquals("Earlier question", messages.getJSONObject(2).getString("content"))
            assertEquals("How can I find hope?", messages.getJSONObject(messages.length() - 1).getString("content"))
        }
    }

    @Test
    fun apiErrorsAreShownAndDoNotSwitchToTheOfflineProvider() = runBlocking {
        LocalHttpStub(401, """{"error":{"message":"invalid api key"}}""").use { server ->
            val online = GroqChatService("test-only-key", server.endpoint, debugLogging = false)
            val offline = RecordingProvider(ChatReply("Offline fallback"))
            val error = runCatching {
                replyWithOfflineFallback(online, offline, "New question", emptyList(), emptyList())
            }.exceptionOrNull()

            assertTrue(error is GroqApiException)
            assertTrue(error?.message.orEmpty().contains("HTTP 401"))
            assertEquals(0, offline.calls)
            server.awaitRequest()
            Unit
        }
    }

    @Test
    fun offlineConnectionUsesBsbFallbackWhileOnlineReplyStaysOnline() = runBlocking {
        val local = RecordingProvider(ChatReply("Local BSB passages", provider = "Offline Bible Assistant"))
        val offlineResult = replyWithOfflineFallback(
            RecordingProvider(failure = OfflineConnectionException("No connection", java.io.IOException())),
            local,
            "New question",
            emptyList(),
            emptyList()
        )
        assertEquals("Offline Bible Assistant", offlineResult.provider)
        assertTrue(offlineResult.fallbackReason.orEmpty().contains("No connection"))
        assertEquals(1, local.calls)

        val online = RecordingProvider(ChatReply("Groq response", provider = "Ciel"))
        val unusedLocal = RecordingProvider(ChatReply("Must not be used"))
        val onlineResult = replyWithOfflineFallback(online, unusedLocal, "Next question", emptyList(), emptyList())
        assertEquals("Ciel", onlineResult.provider)
        assertEquals(0, unusedLocal.calls)
    }

    @Test
    fun markdownHeadersAndBoldMarkersAreRemovedBeforeDisplay() {
        assertEquals(
            "A heading\n\nA response\n• A point",
            ChatReplyCodec.sanitize("## A heading\n---\n> **A response**\n- A point")
        )
    }

    @Test
    fun unverifiedBibleCitationsAndQuotesAreRemovedFromAnswerText() {
        val supplied = listOf(BiblePassage("John", 3, 16, "For God so loved the world that He gave His one and only Son."))
        val answer = ChatReplyCodec.sanitizeBibleClaims(
            "John 99:1 says, “A fabricated Bible quote.” The supplied text says, “For God so loved the world.”",
            supplied,
            listOf("John")
        )
        assertEquals("the passage says, The supplied text says, “For God so loved the world.”", answer)
    }
}

private class RecordingProvider(
    private val value: ChatReply? = null,
    private val failure: Exception? = null
) : AiProvider {
    var calls = 0
        private set

    override suspend fun reply(
        question: String,
        history: List<Pair<String, String>>,
        passages: List<BiblePassage>
    ): ChatReply {
        calls++
        failure?.let { throw it }
        return requireNotNull(value)
    }
}

private class LocalHttpStub(status: Int, body: String) : AutoCloseable {
    private val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
    val endpoint: String = "http://127.0.0.1:${server.localPort}/openai/v1/chat/completions"
    @Volatile private var requestText: String? = null
    private val worker = Thread {
        server.accept().use { socket ->
            val input = DataInputStream(BufferedInputStream(socket.getInputStream()))
            val requestLine = readLine(input)
            val headers = mutableListOf<String>()
            while (true) {
                val line = readLine(input)
                if (line.isEmpty()) break
                headers += line
            }
            val contentLength = headers.firstOrNull { it.startsWith("Content-Length:", ignoreCase = true) }
                ?.substringAfter(':')?.trim()?.toIntOrNull() ?: 0
            val bytes = ByteArray(contentLength)
            input.readFully(bytes)
            requestText = buildString {
                append(requestLine).append("\r\n")
                headers.forEach { append(it).append("\r\n") }
                append("\r\n").append(bytes.toString(StandardCharsets.UTF_8))
            }
            val responseBytes = body.toByteArray(StandardCharsets.UTF_8)
            val reason = if (status in 200..299) "OK" else "Unauthorized"
            val output = DataOutputStream(socket.getOutputStream())
            output.write("HTTP/1.1 $status $reason\r\nContent-Type: application/json\r\nContent-Length: ${responseBytes.size}\r\nConnection: close\r\n\r\n".toByteArray(StandardCharsets.US_ASCII))
            output.write(responseBytes)
            output.flush()
        }
    }.apply {
        isDaemon = true
        start()
    }

    fun awaitRequest(): String {
        worker.join(5_000)
        check(!worker.isAlive) { "Timed out waiting for the HTTP request." }
        return checkNotNull(requestText) { "No HTTP request was captured." }
    }

    override fun close() {
        server.close()
        worker.join(1_000)
    }

    private fun readLine(input: DataInputStream): String {
        val line = ByteArrayOutputStream()
        while (true) {
            val next = input.readUnsignedByte()
            if (next == '\r'.code) {
                input.readUnsignedByte()
                break
            }
            line.write(next)
        }
        return line.toByteArray().toString(StandardCharsets.US_ASCII)
    }
}
