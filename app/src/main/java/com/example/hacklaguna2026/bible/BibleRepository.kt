package com.example.hacklaguna2026.bible

import android.content.Context
import com.example.hacklaguna2026.data.AppDatabase
import com.example.hacklaguna2026.data.BibleVerseEntity
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.Locale

data class BiblePassage(
    val book: String,
    val chapter: Int,
    val verse: Int,
    val text: String
) {
    val reference: String get() = "$book $chapter:$verse"
}

data class VerifiedBibleReference(
    val reference: BibleReference,
    val verses: List<BibleVerseEntity>
)

class BibleRepository(context: Context, private val database: AppDatabase) {
    private val appContext = context.applicationContext
    @Volatile private var cachedVerses: List<BibleVerseEntity>? = null

    suspend fun bookNames(): List<String> = database.bible().books()

    suspend fun ensureBundledBibleLoaded() {
        val storedCount = database.bible().verseCount()
        val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        if (storedCount == EXPECTED_VERSES && preferences.getString(PREFERENCE_SOURCE_HASH, null) == BSB_SOURCE_HASH) return

        val json = try {
            appContext.assets.open(ASSET_NAME).bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (error: Exception) {
            throw IOException("The bundled BSB file '$ASSET_NAME' is missing or unreadable.", error)
        }
        val parsed = parseBsb(json)
        if (parsed.size != EXPECTED_VERSES) {
            throw IOException("The bundled BSB is incomplete: expected $EXPECTED_VERSES verses, found ${parsed.size}.")
        }

        if (storedCount > 0) database.bible().clearVerses()
        database.bible().importVerses(parsed)
        val savedCount = database.bible().verseCount()
        if (savedCount != EXPECTED_VERSES) {
            throw IOException("The bundled BSB could not be saved completely: expected $EXPECTED_VERSES verses, found $savedCount.")
        }
        preferences.edit().putString(PREFERENCE_SOURCE_HASH, BSB_SOURCE_HASH).apply()
        cachedVerses = parsed
    }

    suspend fun searchRelevantPassages(question: String, limit: Int = MAX_PASSAGES): List<BiblePassage> {
        val storedCount = database.bible().verseCount()
        if (storedCount != EXPECTED_VERSES) {
            throw IOException("The bundled BSB is unavailable or incomplete: expected $EXPECTED_VERSES verses, found $storedCount.")
        }
        val terms = question.lowercase(Locale.ROOT)
            .split(WORD_BOUNDARY)
            .filter { it.length >= 4 && it !in STOP_WORDS && !it.all(Char::isDigit) }
            .distinct()
            .take(MAX_QUERY_TERMS)
        if (terms.isEmpty()) return emptyList()

        val verses = cachedVerses ?: database.bible().allVerses().also { cachedVerses = it }
        val patterns = terms.map { term ->
            Regex("(?<![\\p{L}\\p{N}])${Regex.escape(term)}(?![\\p{L}\\p{N}])")
        }
        return verses.asSequence()
            .mapNotNull { verse ->
                val haystack = verse.text.lowercase(Locale.ROOT)
                val score = patterns.sumOf { pattern ->
                    val occurrences = pattern.findAll(haystack).count()
                    minOf(occurrences, 3) * 2
                }
                if (score == 0) null else score to BiblePassage(verse.book, verse.chapter, verse.verse, verse.text)
            }
            .sortedWith(compareByDescending<Pair<Int, BiblePassage>> { it.first }
                .thenBy { it.second.book }
                .thenBy { it.second.chapter }
                .thenBy { it.second.verse })
            .take(limit)
            .map { it.second }
            .toList()
    }

    suspend fun contextualPassages(question: String): List<BiblePassage> {
        val value = question.lowercase(Locale.ROOT)
        val curated = when {
            listOf("distant from god", "far from god", "feel distant", "feel far from").any(value::contains) ->
                listOf("James" to (4 to 8), "Psalms" to (34 to 18), "Psalms" to (145 to 18))
            listOf("mistake", "keep failing", "keep sinning", "mess up", "made a mistake").any(value::contains) ->
                listOf("1 John" to (1 to 9), "Psalms" to (103 to 12), "Romans" to (8 to 1))
            listOf("anxious", "anxiety", "afraid", "scared", "fear", "worried").any(value::contains) ->
                listOf("1 Peter" to (5 to 7), "Philippians" to (4 to 6), "Isaiah" to (41 to 10))
            listOf("grief", "grieving", "died", "death", "loss", "miss them").any(value::contains) ->
                listOf("Psalms" to (34 to 18), "Matthew" to (5 to 4))
            listOf("forgive", "forgiveness").any(value::contains) ->
                listOf("1 John" to (1 to 9), "Matthew" to (6 to 14))
            listOf("pray", "prayer").any(value::contains) ->
                listOf("Matthew" to (6 to 6), "Philippians" to (4 to 6))
            listOf("hope", "discouraged", "difficult week", "hard day").any(value::contains) ->
                listOf("Romans" to (15 to 13), "Psalms" to (42 to 11), "Isaiah" to (41 to 10))
            else -> emptyList()
        }
        if (curated.isNotEmpty()) {
            val actual = curated.mapNotNull { (book, location) ->
                database.bible().verse(book, location.first, location.second)?.let {
                    BiblePassage(it.book, it.chapter, it.verse, it.text)
                }
            }
            if (actual.isNotEmpty()) return actual
        }
        return searchRelevantPassages(question)
    }

    suspend fun validateReferences(
        references: List<BibleReference>,
        suppliedPassages: List<BiblePassage>
    ): List<VerifiedBibleReference> {
        if (suppliedPassages.isEmpty()) return emptyList()
        val supplied = suppliedPassages.map { "${it.book.lowercase(Locale.ROOT)}|${it.chapter}|${it.verse}" }.toSet()
        val books = database.bible().books()
        return references.distinctBy { "${normalize(it.book)}|${it.chapter}|${it.startVerse}|${it.endVerse}" }
            .take(MAX_PASSAGES)
            .mapNotNull { reference ->
                val book = books.firstOrNull { normalize(it) == normalize(reference.book) } ?: return@mapNotNull null
                if (reference.chapter < 1 || reference.startVerse < 1 || reference.endVerse < reference.startVerse ||
                    reference.endVerse - reference.startVerse > MAX_REFERENCE_RANGE
                ) return@mapNotNull null
                val chapterVerses = database.bible().chapter(book, reference.chapter)
                val selected = (reference.startVerse..reference.endVerse).mapNotNull { verseNumber ->
                    chapterVerses.firstOrNull { it.verse == verseNumber }
                }
                if (selected.size != reference.endVerse - reference.startVerse + 1) return@mapNotNull null
                val allWereSupplied = selected.all {
                    "${it.book.lowercase(Locale.ROOT)}|${it.chapter}|${it.verse}" in supplied
                }
                if (!allWereSupplied) return@mapNotNull null
                VerifiedBibleReference(
                    reference.copy(book = book, preview = selected.first().text),
                    selected
                )
            }
    }

    suspend fun resolveSavedReferences(references: List<BibleReference>): List<BiblePassage> {
        val books = database.bible().books()
        return references.distinctBy { "${normalize(it.book)}|${it.chapter}|${it.startVerse}" }
            .take(MAX_PASSAGES)
            .flatMap { reference ->
                val book = books.firstOrNull { normalize(it) == normalize(reference.book) } ?: return@flatMap emptyList()
                if (reference.chapter < 1 || reference.startVerse < 1 || reference.endVerse < reference.startVerse ||
                    reference.endVerse - reference.startVerse > MAX_REFERENCE_RANGE
                ) return@flatMap emptyList()
                val verses = database.bible().chapter(book, reference.chapter)
                (reference.startVerse..reference.endVerse).mapNotNull { number ->
                    verses.firstOrNull { it.verse == number }?.let { BiblePassage(it.book, it.chapter, it.verse, it.text) }
                }
            }
    }

    private fun normalize(value: String): String = value.lowercase(Locale.ROOT).filter(Char::isLetterOrDigit)

    private fun parseBsb(json: String): List<BibleVerseEntity> {
        val root = try {
            JSONObject(json)
        } catch (error: Exception) {
            throw IOException("The bundled BSB file is malformed JSON.", error)
        }
        val books = root.optJSONArray("books")
            ?: throw IOException("The bundled BSB JSON does not contain a books array.")
        val translation = root.optJSONObject("translation")
            ?: throw IOException("The bundled BSB JSON does not contain translation metadata.")
        if (translation.optString("sha256") != BSB_SOURCE_HASH) {
            throw IOException("The bundled Bible is not the expected Berean Standard Bible source.")
        }
        val expectedBookCount = translation.optInt("numberOfBooks", 66)
        val expectedVerseCount = translation.optInt("totalNumberOfVerses", EXPECTED_VERSES)
            ?: EXPECTED_VERSES
        if (books.length() != expectedBookCount) {
            throw IOException("The bundled BSB has ${books.length()} books; expected $expectedBookCount.")
        }

        val verses = ArrayList<BibleVerseEntity>(expectedVerseCount)
        for (bookIndex in 0 until books.length()) {
            val bookObject = books.optJSONObject(bookIndex)
                ?: throw IOException("The bundled BSB contains an invalid book at index $bookIndex.")
            val bookName = bookObject.optString("commonName")
                .ifBlank { bookObject.optString("name") }
                .ifBlank { bookObject.optString("abbreviation") }
                .trim()
            val chapters = bookObject.optJSONArray("chapters")
                ?: throw IOException("The bundled BSB book '${bookName.ifBlank { bookIndex.toString() }}' has no chapters array.")
            if (bookName.isBlank()) throw IOException("The bundled BSB contains a book with no name.")

            for (chapterIndex in 0 until chapters.length()) {
                val wrapper = chapters.optJSONObject(chapterIndex)
                    ?: throw IOException("The bundled BSB contains an invalid chapter in $bookName.")
                // HelloAO BSB stores chapter number/content one level below each chapter metadata wrapper.
                val chapterObject = wrapper.optJSONObject("chapter") ?: wrapper
                val chapterNumber = chapterObject.optInt("number", chapterIndex + 1)
                val directVerses = chapterObject.optJSONArray("verses")
                if (directVerses != null) {
                    for (verseIndex in 0 until directVerses.length()) {
                        val verseObject = directVerses.optJSONObject(verseIndex) ?: continue
                        addVerse(verses, bookIndex, bookName, chapterNumber,
                            verseObject.optInt("number", verseObject.optInt("verse", verseIndex + 1)),
                            textFrom(verseObject))
                    }
                } else {
                    collectNestedVerses(
                        chapterObject.optJSONArray("content"), bookIndex, bookName, chapterNumber, verses
                    )
                }
            }
        }
        if (verses.size != expectedVerseCount) {
            throw IOException("The bundled BSB parse found ${verses.size} verses; expected $expectedVerseCount.")
        }
        return verses
    }

    private fun collectNestedVerses(
        items: JSONArray?,
        bookOrder: Int,
        book: String,
        chapter: Int,
        output: MutableList<BibleVerseEntity>
    ) {
        if (items == null) return
        for (index in 0 until items.length()) {
            val item = items.optJSONObject(index) ?: continue
            if (item.optString("type").equals("verse", ignoreCase = true)) {
                addVerse(output, bookOrder, book, chapter,
                    item.optInt("number", item.optInt("verse", 0)), textFrom(item))
            }
        }
    }

    private fun addVerse(
        output: MutableList<BibleVerseEntity>,
        bookOrder: Int,
        book: String,
        chapter: Int,
        verse: Int,
        text: String
    ) {
        if (chapter > 0 && verse > 0 && text.isNotBlank()) {
            output += BibleVerseEntity(
                bookOrder = bookOrder,
                book = book,
                chapter = chapter,
                verse = verse,
                text = text.trim()
            )
        }
    }

    private fun textFrom(value: JSONObject): String {
        if (value.optBoolean("lineBreak")) return "\n"
        return when (val content = value.opt("content")) {
            is String -> content
            is JSONArray -> textFrom(content)
            is JSONObject -> textFrom(content)
            else -> (value.opt("text") as? String).orEmpty()
        }
    }

    private fun textFrom(items: JSONArray): String = buildString {
        for (index in 0 until items.length()) {
            when (val item = items.opt(index)) {
                is JSONObject -> append(textFrom(item))
                is JSONArray -> append(textFrom(item))
                is String -> append(item)
            }
        }
    }

    companion object {
        const val ASSET_NAME = "bsb.json"
        const val EXPECTED_VERSES = 31_086
        private const val BSB_SOURCE_HASH = "788081b7dabf8ec026509b367254d8f2ba112314f2e8eaaeec8d96fa23a00fc9"
        private const val PREFERENCES_NAME = "bundled_bible_source"
        private const val PREFERENCE_SOURCE_HASH = "translation_hash"
        private const val MAX_PASSAGES = 6
        private const val MAX_REFERENCE_RANGE = 4
        private const val MAX_QUERY_TERMS = 8
        private val WORD_BOUNDARY = Regex("[^\\p{L}\\p{N}]+")
        private val STOP_WORDS = setOf(
            "about", "after", "also", "been", "being", "could", "does", "from", "have", "into",
            "just", "more", "most", "only", "other", "over", "same", "some", "such", "than",
            "that", "their", "them", "then", "there", "these", "they", "this", "those", "through",
            "under", "very", "what", "when", "where", "which", "while", "with", "would", "your"
        )
    }
}
