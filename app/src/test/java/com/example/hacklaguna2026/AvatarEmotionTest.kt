package com.example.hacklaguna2026

import com.example.hacklaguna2026.ai.AvatarEmotion
import com.example.hacklaguna2026.ai.ChatReply
import com.example.hacklaguna2026.ai.ChatReplyCodec
import com.example.hacklaguna2026.ai.classifyAvatarEmotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AvatarEmotionTest {
    @Test
    fun localFallbackRecognizesTheRequestedExamples() {
        assertEquals(AvatarEmotion.HAPPY, classifyAvatarEmotion("I'm so happy because I finally understood this Bible passage!"))
        assertEquals(AvatarEmotion.THINKING, classifyAvatarEmotion("Can you explain this verse?"))
        assertEquals(AvatarEmotion.WORRIED, classifyAvatarEmotion("I'm scared and I don't know what to do."))
        assertEquals(AvatarEmotion.WORSHIPPING, classifyAvatarEmotion("Thank You, Lord, for everything!"))
        assertEquals(AvatarEmotion.WORRIED, classifyAvatarEmotion("I'm frustrated because I keep making mistakes."))
        assertEquals(AvatarEmotion.NEUTRAL, classifyAvatarEmotion("Hello there."))
    }

    @Test
    fun savedRepliesRoundTripValidEmotionAndIgnoreUnknownValues() {
        val encoded = ChatReplyCodec.encode(ChatReply("A kind reply.", avatarEmotion = AvatarEmotion.PROUD))
        assertEquals(AvatarEmotion.PROUD, ChatReplyCodec.decode(encoded).avatarEmotion)

        val invalid = ChatReplyCodec.decode("""{"answer":"A reply.","avatarEmotion":"CONFUSED"}""")
        assertNull(invalid.avatarEmotion)
    }
}
