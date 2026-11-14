package com.example.hacklaguna2026.ai

import com.example.hacklaguna2026.bible.BiblePassage
import com.example.hacklaguna2026.bible.BibleReference

/** A local passage guide. It is intentionally not described as generative AI. */
class OfflineBibleAssistant : AiProvider {
    override suspend fun reply(
        question: String,
        history: List<Pair<String, String>>,
        passages: List<BiblePassage>
    ): ChatReply {
        val answer = if (passages.isEmpty()) {
            "I’m the Offline Bible Assistant, so this reply isn’t generated AI. I couldn’t find a sufficiently relevant passage in the local BSB for that message. You can search the Bible tab, or reconnect to continue with Ciel."
        } else {
            "I’m the Offline Bible Assistant, so this isn’t a generated conversation. These local BSB passages may help you reflect on your question. Open a verse below to read it with the surrounding chapter."
        }
        return ChatReply(
            answer = answer,
            references = passages.map { BibleReference(it.book, it.chapter, it.verse, it.verse, it.text) },
            provider = "Offline Bible Assistant"
        )
    }
}
