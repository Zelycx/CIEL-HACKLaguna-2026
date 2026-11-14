package com.example.hacklaguna2026.data

import com.example.hacklaguna2026.ai.ChatReplyCodec
import java.util.UUID

class ConversationRepository(private val database: AppDatabase) {
    suspend fun createConversation(title: String = "New conversation"): String {
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        database.conversations().saveConversation(
            ConversationEntity(id, title.take(48).ifBlank { "New conversation" }, now, now)
        )
        return id
    }

    suspend fun conversations(): List<ConversationEntity> = database.conversations().allConversations()

    suspend fun messages(conversationId: String): List<MessageEntity> = database.messages().forConversation(conversationId)

    suspend fun addUserMessage(conversationId: String, content: String) {
        val now = System.currentTimeMillis()
        database.messages().add(MessageEntity(conversationId = conversationId, role = "user", content = content, createdAt = now))
        val old = database.conversations().conversation(conversationId)
        if (old != null) {
            database.conversations().saveConversation(old.copy(
                title = if (old.title == "New conversation") content.take(48) else old.title,
                updatedAt = now
            ))
        }
    }

    suspend fun addAssistantReply(conversationId: String, reply: com.example.hacklaguna2026.ai.ChatReply) {
        val now = System.currentTimeMillis()
        database.messages().add(MessageEntity(
            conversationId = conversationId,
            role = "assistant",
            content = ChatReplyCodec.encode(reply),
            createdAt = now
        ))
        database.conversations().conversation(conversationId)?.let { old ->
            database.conversations().saveConversation(old.copy(updatedAt = now))
        }
    }

    suspend fun deleteConversation(conversationId: String) = database.conversations().deleteConversation(conversationId)
}
