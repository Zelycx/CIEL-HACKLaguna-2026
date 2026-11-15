package com.example.hacklaguna2026

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.content.FileProvider
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.hacklaguna2026.bible.BibleRepository
import com.example.hacklaguna2026.bible.DailyVerseSelector
import com.example.hacklaguna2026.data.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class BibleAndVerseImageTest {
    @Test
    fun everyCuratedReferenceExistsInBundledBsbAndDailySelectionLoadsExactText() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val bible = BibleRepository(context, database)
            bible.ensureBundledBibleLoaded()
            DailyVerseSelector.curatedEntries.forEach { entry ->
                val stored = requireNotNull(database.bible().verse(entry.book, entry.chapter, entry.verse))
                assertTrue(entry.reflection.isNotBlank())
                val expression = VerseShareActivity.EXPRESSIONS.first {
                    it.id.equals(entry.defaultExpression, ignoreCase = true)
                }
                val image = VerseImageRenderer.render(
                    context,
                    stored.text,
                    entry.book + " " + entry.chapter + ":" + entry.verse,
                    entry.theme,
                    entry.reflection,
                    expression.drawable,
                    VerseShareActivity.THEMES.first()
                )
                assertEquals(1080, image.width)
                assertEquals(1350, image.height)
                image.recycle()
            }
            val now = System.currentTimeMillis()
            val selected = DailyVerseSelector.select(database, now)!!
            val curated = DailyVerseSelector.curatedEntries.single {
                it.book == selected.passage.book && it.chapter == selected.passage.chapter && it.verse == selected.passage.verse
            }
            assertEquals(curated.theme, selected.theme)
            assertEquals(database.bible().verse(curated.book, curated.chapter, curated.verse)!!.text, selected.passage.text)
            assertEquals(
                DailyVerseSelector.indexForLocalDate(now, DailyVerseSelector.curatedEntries.size),
                DailyVerseSelector.indexForLocalDate(now + 60 * 60 * 1000L, DailyVerseSelector.curatedEntries.size)
            )
        } finally {
            database.close()
        }
    }

    @Test
    fun allNineExpressionsAndFourThemesRenderPortraitShareImages() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals(9, VerseShareActivity.EXPRESSIONS.size)
        assertFalse(VerseShareActivity.EXPRESSIONS.any { it.drawable == R.drawable.ciel })
        assertEquals(4, VerseShareActivity.THEMES.size)
        VerseShareActivity.EXPRESSIONS.forEach { expression ->
            val bitmap = VerseImageRenderer.render(
                context,
                "The LORD is my shepherd; I shall not want.",
                "Psalms 23:1",
                "Trust",
                "The shepherd image frames the psalm's trust in God's care.",
                expression.drawable,
                VerseShareActivity.THEMES.first()
            )
            assertEquals(1080, bitmap.width)
            assertEquals(1350, bitmap.height)
            bitmap.recycle()
        }
        VerseShareActivity.THEMES.forEach { theme ->
            val bitmap = VerseImageRenderer.render(
                context,
                "Let the peace of Christ rule in your hearts, for to this you were called as members of one body. And be thankful.",
                "Colossians 3:15",
                "Peace",
                null,
                VerseShareActivity.EXPRESSIONS.first().drawable,
                theme
            )
            assertEquals(theme.background, bitmap.getPixel(5, 5))
            bitmap.recycle()
        }
    }

    @Test
    fun fileProviderExposesGeneratedPngOnlyThroughReadableContentUri() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val directory = File(context.cacheDir, "verse-shares").apply { mkdirs() }
        val output = File(directory, "instrumentation-test.png")
        val bitmap = VerseImageRenderer.render(
            context,
            "Be still, and know that I am God.",
            "Psalms 46:10",
            "Peace",
            null,
            VerseShareActivity.EXPRESSIONS.first().drawable,
            VerseShareActivity.THEMES.first()
        )
        FileOutputStream(output).use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
        try {
            val uri = FileProvider.getUriForFile(context, context.packageName + ".files", output)
            assertEquals("content", uri.scheme)
            context.contentResolver.openInputStream(uri).use { stream ->
                assertNotNull(stream)
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeStream(stream, null, bounds)
                assertEquals(1080, bounds.outWidth)
                assertEquals(1350, bounds.outHeight)
            }
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            assertEquals("image/png", send.type)
            assertEquals(uri, send.getParcelableExtra(Intent.EXTRA_STREAM))
        } finally {
            output.delete()
        }
    }

    @Test
    fun oversizedVerseFailsInsteadOfBeingTruncatedOrOverflowing() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val error = runCatching {
            VerseImageRenderer.render(
                context,
                "word ".repeat(20_000),
                "Psalms 23:1",
                "Trust",
                "A short reflection.",
                VerseShareActivity.EXPRESSIONS.first().drawable,
                VerseShareActivity.THEMES.first()
            )
        }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }
}
