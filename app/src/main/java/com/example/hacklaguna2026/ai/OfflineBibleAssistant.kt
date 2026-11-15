package com.example.hacklaguna2026.ai

import com.example.hacklaguna2026.bible.BiblePassage
import com.example.hacklaguna2026.bible.BibleReference

/** Legacy template-based fallback. Production chat uses [LiteRtLocalAIProvider] for local generation. */
class OfflineBibleAssistant : AiProvider {
    override suspend fun reply(
        question: String,
        history: List<Pair<String, String>>,
        passages: List<BiblePassage>
    ): ChatReply {
        val normalized = question.lowercase()
        val kind = CielConversationPolicy.classify(question)
        if (kind in setOf(CielRequestKind.PROMPT_INJECTION, CielRequestKind.GENERAL_KNOWLEDGE, CielRequestKind.TECHNICAL_REQUEST)) {
            return ChatReply(CielConversationPolicy.REDIRECT, provider = "offline")
        }
        if (kind == CielRequestKind.SAFETY_CRISIS) {
            return ChatReply(CielConversationPolicy.CRISIS_REPLY, provider = "offline", avatarEmotion = AvatarEmotion.WORRIED)
        }
        if (kind == CielRequestKind.GREETING) {
            return ChatReply(choose(
                listOf("Hello! I'm happy to spend some time with you. What's on your mind today?", "Hi! I'm glad you're here. What would you like to talk about?", "Hello, friend. What has been on your heart today?"), history
            ), provider = "offline", avatarEmotion = AvatarEmotion.HAPPY)
        }
        if (kind == CielRequestKind.SMALL_TALK) {
            return ChatReply(choose(
                listOf("My day is peaceful in my little corner of the screen. I'm glad we're talking. How has yours been?", "It's a quiet day here, and I'm glad to keep you company. How are you doing?"), history
            ), provider = "offline", avatarEmotion = AvatarEmotion.HAPPY)
        }
        if (kind == CielRequestKind.CIEL_PERSONALITY) {
            return ChatReply("I don't experience feelings or days like a person does, but I can be a friendly companion while we talk. How are you doing?", provider = "offline", avatarEmotion = AvatarEmotion.HAPPY)
        }

        val answer = when {
            kind == CielRequestKind.EMOTIONAL_SUPPORT -> choose(
                listOf(
                    "That sounds like a lot to carry. I'm here to listen, and you don't have to find the perfect words. What happened?",
                    "I'm sorry this has been weighing on you. It makes sense that you'd feel affected by it. What part has been hardest?"
                ), history
            )
            listOf("distant from god", "far from god", "distant from you", "feel distant", "feel far from").any(normalized::contains) -> choose(
                listOf(
                    "Faith can feel distant when life is heavy, but that feeling doesn't mean you've been forgotten or can't draw near again. Would you like to talk about what has been making faith feel far away?",
                    "Feeling far from God can be painful. It doesn't have to be the end of your connection with Him; small honest steps can matter. What has been weighing on you lately?"
                ), history
            )
            listOf("mistake", "keep failing", "keep sinning", "mess up", "made a mistake").any(normalized::contains) -> choose(
                listOf(
                    "Making mistakes doesn't mean your journey with God is over. You can seek forgiveness, learn, and keep growing one step at a time. Is there something specific you're trying to make right?",
                    "A setback doesn't erase your worth or close the door to growth. Honest regret can become a first step toward repair and a wiser next choice. What feels hardest about it right now?"
                ), history
            )
            listOf("anxious", "anxiety", "afraid", "scared", "fear", "worried").any(normalized::contains) -> choose(
                listOf(
                    "That sounds unsettling. You don't have to solve everything at once; try naming the next small thing you need, and consider who could sit with you through it. What is worrying you most?",
                    "I'm sorry this feels heavy. Many people bring fear and worry honestly into prayer, while also leaning on people they trust. Would it help to talk through what is making you feel unsafe or uncertain?"
                ), history
            )
            listOf("hope", "discouraged", "difficult week", "hard day").any(normalized::contains) ->
                "A difficult week can make hope feel small. You don't have to carry the whole week at once; take one kind next step and let someone you trust walk with you. What has been the hardest part?"
            listOf("grief", "grieving", "died", "death", "loss", "miss them").any(normalized::contains) ->
                "I'm sorry you're facing this loss. Grief can take time, and you don't need to rush yourself into feeling okay. Would you like to tell me about the person or what you miss most?"
            listOf("forgive", "forgiveness").any(normalized::contains) ->
                "Forgiveness can be a process, especially when real hurt is involved. It doesn't mean pretending the harm was okay or removing healthy boundaries. What part of forgiveness feels difficult for you?"
            listOf("pray", "prayer").any(normalized::contains) ->
                "Prayer can be simple and honest; you can begin with exactly what you are feeling and what you need. Would you like help finding words for a short prayer?"
            history.lastOrNull()?.first == "assistant" ->
                "I'm here with you. Could you tell me a little more about what you mean, or what part you'd like to explore through faith or Scripture?"
            else -> choose(
                listOf(
                    "Thank you for sharing that with me. I can explore it with you through faith and Scripture. What part feels most important to you right now?",
                    "That's a thoughtful question. I may not have a complete answer, but we can take it one step at a time. What would be most helpful to understand first?",
                    "I hear that this matters to you. Would you like to share a little more, or look at what the Bible says about it?"
                ), history
            )
        }
        val references = passages.map { BibleReference(it.book, it.chapter, it.verse, it.verse, it.text) }
        return ChatReply(
            answer = answer,
            references = references,
            provider = "offline",
            avatarEmotion = classifyAvatarEmotion(question)
        )
    }

    private fun choose(options: List<String>, history: List<Pair<String, String>>): String {
        val lastReply = history.lastOrNull { it.first == "assistant" }?.second
        return options.firstOrNull { it != lastReply } ?: options.first()
    }
}
