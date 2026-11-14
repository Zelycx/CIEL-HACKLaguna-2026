package com.example.hacklaguna2026.ai

import com.example.hacklaguna2026.bible.BiblePassage

interface AiProvider {
    suspend fun reply(
        question: String,
        history: List<Pair<String, String>>,
        passages: List<BiblePassage>
    ): ChatReply
}
