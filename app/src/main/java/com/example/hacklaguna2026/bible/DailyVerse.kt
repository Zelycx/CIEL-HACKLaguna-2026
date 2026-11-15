package com.example.hacklaguna2026.bible

import com.example.hacklaguna2026.data.AppDatabase
import com.example.hacklaguna2026.data.BibleVerseEntity
import java.util.Calendar

data class DailyVerse(
    val passage: BibleVerseEntity,
    val theme: String,
    val reflection: String,
    val defaultExpression: String
) {
    val reference: String get() = "${passage.book} ${passage.chapter}:${passage.verse}"
}

data class CuratedVerseEntry(
    val theme: String,
    val book: String,
    val chapter: Int,
    val verse: Int,
    val reflection: String,
    val defaultExpression: String = "NEUTRAL"
)

object DailyVerseSelector {
    /** References only; Scripture text is always loaded from the bundled BSB database. */
    val curatedEntries = listOf(
        CuratedVerseEntry("Hope", "Romans", 15, 13, "Paul's blessing connects hope with joy, peace, and trust in God.", "HAPPY"),
        CuratedVerseEntry("Hope", "Psalms", 42, 11, "The psalmist names discouragement honestly and turns toward hope in God.", "PROUD"),
        CuratedVerseEntry("Hope", "1 Peter", 1, 3, "Peter grounds living hope in the resurrection of Jesus.", "HAPPY"),
        CuratedVerseEntry("Strength", "Isaiah", 41, 10, "God's presence and help are the focus of this reassurance.", "PROUD"),
        CuratedVerseEntry("Strength", "Psalms", 46, 1, "This psalm describes God as a refuge amid trouble, not a promise that trouble never comes.", "PROUD"),
        CuratedVerseEntry("Strength", "Philippians", 4, 13, "In context, Paul speaks of learning contentment through both scarcity and plenty.", "PROUD"),
        CuratedVerseEntry("God's love", "John", 3, 16, "John points to God's love in the gift of His Son.", "HAPPY"),
        CuratedVerseEntry("God's love", "1 John", 4, 9, "John describes God's love as shown through Jesus and the life He gives.", "HAPPY"),
        CuratedVerseEntry("God's love", "Romans", 8, 39, "Paul concludes that nothing in creation can separate believers from God's love in Christ.", "HAPPY"),
        CuratedVerseEntry("Forgiveness", "1 John", 1, 9, "John connects confession with God's faithfulness to forgive and cleanse.", "NEUTRAL"),
        CuratedVerseEntry("Forgiveness", "Psalms", 103, 12, "The psalm praises God's generous removal of transgressions.", "HAPPY"),
        CuratedVerseEntry("Forgiveness", "Ephesians", 1, 7, "Paul describes redemption and forgiveness as gifts of God's grace.", "NEUTRAL"),
        CuratedVerseEntry("Peace", "John", 14, 27, "Jesus offers His peace to His disciples as He prepares them for His departure.", "NEUTRAL"),
        CuratedVerseEntry("Peace", "Philippians", 4, 7, "Paul describes God's peace in the context of prayer, thanksgiving, and concern.", "NEUTRAL"),
        CuratedVerseEntry("Courage", "Joshua", 1, 9, "Joshua is encouraged to be courageous as he takes up a difficult responsibility.", "PROUD"),
        CuratedVerseEntry("Courage", "Deuteronomy", 31, 8, "Moses encourages Israel's leaders with God's presence as they prepare to enter the land.", "PROUD"),
        CuratedVerseEntry("Wisdom", "James", 1, 5, "James encourages asking God for wisdom while facing trials.", "THINKING"),
        CuratedVerseEntry("Wisdom", "Proverbs", 3, 5, "This proverb invites trust in God rather than treating our own understanding as complete.", "THINKING"),
        CuratedVerseEntry("Guidance", "Psalms", 32, 8, "The psalm presents God as one who instructs and counsels.", "THINKING"),
        CuratedVerseEntry("Guidance", "Proverbs", 3, 6, "The surrounding proverb calls readers to acknowledge God throughout their ways.", "THINKING"),
        CuratedVerseEntry("Faith", "Hebrews", 11, 1, "Hebrews introduces a chapter of examples of trust in what God has promised.", "WORSHIPPING"),
        CuratedVerseEntry("Faith", "Mark", 9, 24, "A father brings both belief and doubt honestly to Jesus.", "THINKING"),
        CuratedVerseEntry("Gratitude", "1 Thessalonians", 5, 18, "Paul calls the church to give thanks in every circumstance, without calling every circumstance good.", "HAPPY"),
        CuratedVerseEntry("Gratitude", "Psalms", 107, 1, "This psalm opens by giving thanks for God's goodness and enduring love.", "HAPPY"),
        CuratedVerseEntry("Compassion", "Matthew", 5, 7, "Jesus names mercy as a mark of life in the kingdom of heaven.", "HAPPY"),
        CuratedVerseEntry("Compassion", "Colossians", 3, 12, "Paul urges the church to clothe itself with compassion, kindness, humility, gentleness, and patience.", "NEUTRAL"),
        CuratedVerseEntry("Perseverance", "James", 1, 12, "James speaks of steadfastness while enduring trials.", "PROUD"),
        CuratedVerseEntry("Perseverance", "Galatians", 6, 9, "Paul encourages continuing to do good without growing weary.", "PROUD"),
        CuratedVerseEntry("Comfort", "Matthew", 11, 28, "Jesus invites the weary and burdened to come to Him for rest.", "NEUTRAL"),
        CuratedVerseEntry("Comfort", "Psalms", 34, 18, "The psalm speaks of God's nearness to the brokenhearted.", "WORRIED"),
        CuratedVerseEntry("Trust", "Psalms", 23, 1, "The shepherd image frames the psalm's trust in God's care.", "NEUTRAL"),
        CuratedVerseEntry("Trust", "Psalms", 121, 1, "The pilgrim looks to God for help in this song of trust.", "THINKING"),
        CuratedVerseEntry("Spiritual growth", "Romans", 12, 2, "Paul calls believers toward a renewed way of thinking and discerning God's will.", "THINKING"),
        CuratedVerseEntry("Spiritual growth", "2 Peter", 3, 18, "Peter closes his letter by urging growth in grace and knowledge of Christ.", "PROUD"),
        CuratedVerseEntry("Joy", "Psalms", 16, 11, "The psalm celebrates the joy found in God's presence.", "HAPPY"),
        CuratedVerseEntry("Joy", "Nehemiah", 8, 10, "In context, leaders comfort the people during a day of communal worship and celebration.", "HAPPY"),
        CuratedVerseEntry("Patience", "Romans", 12, 12, "Paul's short instruction holds hope, endurance, and prayer together.", "NEUTRAL"),
        CuratedVerseEntry("Patience", "Galatians", 5, 22, "Paul lists patience among the fruit of the Spirit.", "NEUTRAL"),
        CuratedVerseEntry("Love of neighbor", "1 Corinthians", 13, 4, "Paul describes love while correcting divisions in the church.", "HAPPY"),
        CuratedVerseEntry("Love of neighbor", "John", 13, 34, "Jesus gives His disciples a command to love one another as He has loved them.", "HAPPY"),
        CuratedVerseEntry("Grace", "2 Corinthians", 12, 9, "Paul recalls Christ's answer amid his own weakness and hardship.", "NEUTRAL"),
        CuratedVerseEntry("Grace", "Ephesians", 2, 8, "Paul describes salvation as God's gift, received through faith.", "WORSHIPPING"),
        CuratedVerseEntry("Worth and dignity", "Psalms", 139, 14, "The psalmist responds with praise while reflecting on God's intimate knowledge.", "HAPPY"),
        CuratedVerseEntry("Goodness", "Psalms", 34, 8, "The psalm invites readers to seek refuge in the LORD and recognize His goodness.", "HAPPY"),
        CuratedVerseEntry("Hope in hardship", "Jeremiah", 29, 11, "This promise was spoken to exiles about God's purpose for the community over time, not a guarantee of an easy personal future.", "NEUTRAL"),
        CuratedVerseEntry("Peace in hardship", "Philippians", 4, 6, "Paul encourages prayer and thanksgiving amid concern, rather than denying that concerns exist.", "NEUTRAL")
    )

    suspend fun select(database: AppDatabase, now: Long = System.currentTimeMillis()): DailyVerse? {
        val verified = curatedEntries.mapNotNull { entry ->
            database.bible().verse(entry.book, entry.chapter, entry.verse)?.let { passage ->
                DailyVerse(passage, entry.theme, entry.reflection, entry.defaultExpression)
            }
        }
        if (verified.isNotEmpty()) return verified[indexForLocalDate(now, verified.size)]

        val fallback = database.bible().allVerses().firstOrNull() ?: return null
        return DailyVerse(
            fallback,
            "A moment in Scripture",
            "Read this verse with the surrounding passage to consider its place in the chapter.",
            "NEUTRAL"
        )
    }

    fun indexForLocalDate(now: Long, itemCount: Int): Int {
        require(itemCount > 0)
        val calendar = Calendar.getInstance().apply { timeInMillis = now }
        val daySequence = calendar.get(Calendar.YEAR).toLong() * 366L + calendar.get(Calendar.DAY_OF_YEAR)
        return Math.floorMod(daySequence, itemCount.toLong()).toInt()
    }
}
