package com.example.hacklaguna2026.ai

/** Ciel's friendly expression. This is not an assessment of the user's mental health. */
enum class AvatarEmotion {
    HAPPY,
    NEUTRAL,
    THINKING,
    WORRIED,
    SLEEPY,
    SURPRISED,
    PROUD,
    ANGRY,
    WORSHIPPING;

    companion object {
        fun fromWire(value: String?): AvatarEmotion? = value
            ?.trim()
            ?.uppercase()
            ?.let { runCatching { valueOf(it) }.getOrNull() }
    }
}

/** Small on-device fallback used when a saved/online reply has no valid expression. */
fun classifyAvatarEmotion(message: String): AvatarEmotion {
    val value = message.lowercase().trim()
    if (value.isBlank()) return AvatarEmotion.NEUTRAL

    return when {
        listOf("thank you, lord", "thank you lord", "praise the lord", "praise god", "hallelujah", "worship", "i thank god", "thank you god")
            .any(value::contains) -> AvatarEmotion.WORSHIPPING
        listOf("so sleepy", "i'm sleepy", "im sleepy", "tired", "exhausted", "need to sleep", "can't stay awake")
            .any(value::contains) -> AvatarEmotion.SLEEPY
        listOf("shocked", "i can't believe", "i cant believe", "surprised", "what?!", "wow!")
            .any(value::contains) -> AvatarEmotion.SURPRISED
        listOf("proud of", "i did it", "i made progress", "i overcame", "i finished", "i succeeded")
            .any(value::contains) -> AvatarEmotion.PROUD
        listOf("furious", "so angry", "i'm angry", "im angry", "i hate", "annoyed")
            .any(value::contains) -> AvatarEmotion.ANGRY
        listOf("scared", "afraid", "anxious", "worried", "nervous", "don't know what to do", "dont know what to do", "frustrated", "struggling", "suffering", "hurt", "lonely", "sad", "grief")
            .any(value::contains) -> AvatarEmotion.WORRIED
        listOf("happy", "joyful", "excited", "grateful", "blessed", "understood this", "i love")
            .any(value::contains) -> AvatarEmotion.HAPPY
        listOf("why ", "how ", "what does", "can you explain", "could you explain", "help me understand", "tell me about", "?")
            .any(value::contains) -> AvatarEmotion.THINKING
        else -> AvatarEmotion.NEUTRAL
    }
}
