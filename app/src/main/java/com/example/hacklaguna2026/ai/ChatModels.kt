package com.example.hacklaguna2026.ai

import org.json.JSONArray
import org.json.JSONObject
import com.example.hacklaguna2026.bible.BiblePassage
import com.example.hacklaguna2026.bible.BibleReference

data class ChatReply(
    val answer: String,
    val references: List<BibleReference> = emptyList(),
    val provider: String = "Ciel",
    val fallbackReason: String? = null,
    val avatarEmotion: AvatarEmotion? = null
)

object ChatReplyCodec {
    fun encode(reply: ChatReply): String = JSONObject()
        .put("answer", sanitize(reply.answer))
        .put("provider", reply.provider)
        .put("fallbackReason", reply.fallbackReason ?: JSONObject.NULL)
        .put("avatarEmotion", reply.avatarEmotion?.name ?: JSONObject.NULL)
        .put("references", JSONArray().apply {
            reply.references.forEach { reference ->
                put(JSONObject()
                    .put("book", reference.book)
                    .put("chapter", reference.chapter)
                    .put("startVerse", reference.startVerse)
                    .put("endVerse", reference.endVerse)
                    .put("preview", reference.preview))
            }
        })
        .toString()

    fun decode(value: String): ChatReply {
        return runCatching {
            val json = JSONObject(value)
            val answer = json.optString("answer").takeIf(String::isNotBlank)
                ?: return@runCatching ChatReply(answer = sanitize(value))
            val references = json.optJSONArray("references")?.let(::readReferences).orEmpty()
            ChatReply(
                answer = sanitize(answer),
                references = references,
                provider = json.optString("provider", "Ciel").ifBlank { "Ciel" },
                fallbackReason = json.optString("fallbackReason")
                    .takeIf { it.isNotBlank() && it != "null" },
                avatarEmotion = AvatarEmotion.fromWire(json.optString("avatarEmotion").takeIf { it != "null" })
            )
        }.getOrElse { ChatReply(answer = sanitize(value)) }
    }

    fun readReferences(items: JSONArray): List<BibleReference> = buildList {
        for (index in 0 until items.length()) {
            val item = items.optJSONObject(index) ?: continue
            val book = item.optString("book").trim()
            val chapter = item.optInt("chapter")
            val startVerse = item.optInt("startVerse")
            val endVerse = item.optInt("endVerse", startVerse)
            if (book.isNotBlank() && chapter > 0 && startVerse > 0 && endVerse >= startVerse) {
                add(BibleReference(book, chapter, startVerse, endVerse, item.optString("preview")))
            }
        }
    }

    fun sanitize(value: String): String = value
        .replace(Regex("(?m)^[ \\t]{0,3}#{1,6}[ \\t]*"), "")
        .replace(Regex("(?m)^[ \\t]*[-*_]{3,}[ \\t]*$"), "")
        .replace(Regex("(?m)^[ \\t]*>[ \\t]?"), "")
        .replace(Regex("(?m)^[ \\t]*[-*+][ \\t]+"), "• ")
        .replace(Regex("\\*\\*(.*?)\\*\\*|__(.*?)__"), "$1$2")
        .replace(Regex("(?<!\\*)\\*(?!\\*)(.*?)\\*(?!\\*)"), "$1")
        .replace(Regex("(?<!_)_(?!_)(.*?)_(?!_)"), "$1")
        .replace(Regex("~~(.*?)~~"), "$1")
        .replace(Regex("`{1,3}"), "")
        .replace(Regex("!\\[([^]]*)]\\(([^)]+)\\)"), "$1")
        .replace(Regex("\\[([^]]+)]\\(([^)]+)\\)"), "$1")
        .trim()

    fun sanitizeBibleClaims(answer: String, supplied: List<BiblePassage>, books: List<String>): String {
        var safe = sanitize(answer)
        val bookAlternation = books.sortedByDescending(String::length)
            .joinToString("|") { Regex.escape(it) }
        if (bookAlternation.isNotBlank()) {
            val citations = Regex(
                "(?i)(?<![\\p{L}\\p{N}])(?:[1-3]\\s+)?(?:$bookAlternation)\\s+\\d{1,3}(?:\\s*:\\s*\\d{1,3}(?:\\s*[-–]\\s*\\d{1,3})?)?(?!\\d)"
            )
            safe = citations.replace(safe, "the passage")
        }

        val verifiedText = supplied.map { normalizeQuotation(it.text) }.filter(String::isNotBlank)
        val quotePatterns = listOf(
            Regex("“([^”]{4,})”"), Regex("\"([^\"]{4,})\""),
            Regex("‘([^’]{4,})’"), Regex("'([^']{4,})'")
        )
        quotePatterns.forEach { pattern ->
            safe = pattern.replace(safe) { match ->
                val quote = normalizeQuotation(match.groupValues[1])
                if (quote.isNotBlank() && verifiedText.any { it.contains(quote) }) match.value else ""
            }
        }
        return safe
            .replace(Regex("[ \\t]{2,}"), " ")
            .replace(Regex("\\s+([,;:.!?])"), "$1")
            .replace(Regex("\\(\\s*\\)"), "")
            .trim()
    }

    private fun normalizeQuotation(value: String): String = value
        .lowercase()
        .filter(Char::isLetterOrDigit)
}
