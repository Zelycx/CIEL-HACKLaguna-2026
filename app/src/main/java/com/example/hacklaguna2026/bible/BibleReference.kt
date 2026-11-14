package com.example.hacklaguna2026.bible

data class BibleReference(
    val book: String,
    val chapter: Int,
    val startVerse: Int,
    val endVerse: Int,
    val preview: String = ""
) {
    val label: String
        get() = "$book $chapter:$startVerse${if (endVerse > startVerse) "–$endVerse" else ""}"
}
