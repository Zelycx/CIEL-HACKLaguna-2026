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
    private val onlineProvider: AiProvider,
    private val offlineProvider: AiProvider
) : ViewModel() {
    data class SendResult(
        val conversationId: String,
        val messages: List<MessageEntity>,
        val reply: ChatReply
    )

    suspend fun send(
        conversationId: String?,
        rawQuestion: String,
        persistUserQuestion: Boolean = true
    ): SendResult = withContext(Dispatchers.IO) {
        val question = normalizeChatQuestion(rawQuestion)
        val id = conversationId ?: conversations.createConversation(question)
        if (persistUserQuestion) conversations.addUserMessage(id, question)

        val history = conversations.messages(id)
        val passages = bible.searchRelevantPassages(question)
        val turns = buildChatHistory(history, question)
        val generated = replyWithOfflineFallback(onlineProvider, offlineProvider, question, turns, passages)
        val verified = bible.validateReferences(generated.references, passages)
            .map { it.reference }
        val safeReply = generated.copy(
            answer = ChatReplyCodec.sanitizeBibleClaims(generated.answer, passages, bible.bookNames()),
            references = verified
        )
        conversations.addAssistantReply(id, safeReply)
        SendResult(id, conversations.messages(id), safeReply)
    }

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
}

internal fun buildChatHistory(history: List<MessageEntity>, currentQuestion: String): List<Pair<String, String>> {
    val turns = history.asSequence()
        .filter { it.role == "user" || it.role == "assistant" }
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
