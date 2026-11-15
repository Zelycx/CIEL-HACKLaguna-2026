package com.example.hacklaguna2026.stats

import com.example.hacklaguna2026.data.ActivitySessionEntity
import com.example.hacklaguna2026.data.AppDatabase
import com.example.hacklaguna2026.data.DailyStatisticsEntity
import androidx.room.withTransaction
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class WeekDayActivity(val dayKey: String, val label: String, val durationMs: Long, val chapters: Int)

data class StatisticsSnapshot(
    val sessions: Int,
    val totalTimeMs: Long,
    val bibleTimeMs: Long,
    val chatTimeMs: Long,
    val otherTimeMs: Long,
    val booksVisited: Int,
    val chaptersOpened: Int,
    val chaptersCompleted: Int,
    val versesRead: Int,
    val totalBibleChapters: Int,
    val readingStreak: Int,
    val longestReadingStreak: Int,
    val totalReadingDays: Int,
    val lastReadingDay: String?,
    val week: List<WeekDayActivity>,
    val recentChapters: List<com.example.hacklaguna2026.data.ReadingProgressEntity>
)

data class ContinueReadingTarget(val book: String, val chapter: Int)

object ContinueReadingSelector {
    fun select(
        orderedChapters: List<ContinueReadingTarget>,
        progress: List<com.example.hacklaguna2026.data.ReadingProgressEntity>
    ): ContinueReadingTarget? {
        if (orderedChapters.isEmpty()) return null
        val indexByChapter = orderedChapters.withIndex().associate { (index, target) ->
            (target.book to target.chapter) to index
        }
        val completedIndex = progress.asSequence().filter { it.completedAt != null }
            .mapNotNull { indexByChapter[it.book to it.chapter] }.maxOrNull() ?: -1
        val currentlyReading = progress.asSequence().filter { it.completedAt == null }
            .filter { (indexByChapter[it.book to it.chapter] ?: -1) > completedIndex }
            .maxByOrNull { it.openedAt }
        if (currentlyReading != null) return ContinueReadingTarget(currentlyReading.book, currentlyReading.chapter)
        return orderedChapters.getOrNull(completedIndex + 1)
            ?: if (completedIndex < 0) progress.maxByOrNull { it.openedAt }?.let {
                ContinueReadingTarget(it.book, it.chapter)
            } else null
    }
}

object ReadingStreaks {
    fun shouldWarnToday(
        dayKeys: Collection<String>,
        completedToday: Boolean,
        lastWarningDay: String?,
        now: Long = System.currentTimeMillis()
    ): Boolean {
        val today = key(now)
        return !completedToday && lastWarningDay != today && isAtRisk(dayKeys, now)
    }

    fun isAtRisk(dayKeys: Collection<String>, now: Long = System.currentTimeMillis()): Boolean {
        val active = dayKeys.toSet()
        val today = Calendar.getInstance().apply { timeInMillis = now }
        if (key(today.timeInMillis) in active) return false
        today.add(Calendar.DAY_OF_YEAR, -1)
        return key(today.timeInMillis) in active
    }

    fun current(dayKeys: Collection<String>, now: Long = System.currentTimeMillis()): Int {
        val active = dayKeys.toSet()
        if (active.isEmpty()) return 0
        val day = Calendar.getInstance().apply { timeInMillis = now }
        if (key(day.timeInMillis) !in active) day.add(Calendar.DAY_OF_YEAR, -1)
        var count = 0
        while (key(day.timeInMillis) in active) {
            count++
            day.add(Calendar.DAY_OF_YEAR, -1)
        }
        return count
    }

    fun longest(dayKeys: Collection<String>): Int {
        if (dayKeys.isEmpty()) return 0
        val sorted = dayKeys.toSortedSet()
        var best = 0
        var run = 0
        var previous: String? = null
        sorted.forEach { value ->
            if (previous == null || nextDay(previous!!) == value) run++ else run = 1
            best = maxOf(best, run)
            previous = value
        }
        return best
    }

    fun key(time: Long): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(time))

    private fun nextDay(value: String): String? {
        val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
        val date = runCatching { parser.parse(value) }.getOrNull() ?: return null
        val tomorrow = Calendar.getInstance().apply { time = date; add(Calendar.DAY_OF_YEAR, 1) }
        return key(tomorrow.timeInMillis)
    }
}

class AppStatsRepository(private val database: AppDatabase) {
    suspend fun beginSession(now: Long = System.currentTimeMillis()) {
        val key = dayKey(now)
        database.withTransaction {
            database.dailyStatistics().ensure(DailyStatisticsEntity(dayKey = key))
            database.dailyStatistics().incrementSessions(key)
        }
    }

    suspend fun recordModuleTime(module: String, startedAt: Long, durationMs: Long) {
        if (durationMs < 1_000) return
        database.activitySessions().add(
            ActivitySessionEntity(
                dayKey = dayKey(startedAt),
                module = module,
                startedAt = startedAt,
                durationMs = durationMs
            )
        )
    }

    suspend fun recordChapterOpened(book: String, chapter: Int, now: Long = System.currentTimeMillis()) {
        val row = com.example.hacklaguna2026.data.ReadingProgressEntity(
            book = book,
            chapter = chapter,
            lastVerse = 0,
            openedAt = now,
            completedAt = null,
            openCount = 1
        )
        database.withTransaction {
            val dao = database.readingProgress()
            if (dao.insertOpened(row) == -1L) dao.updateOpened(book, chapter, now)
        }
    }

    suspend fun markChapterCompleted(book: String, chapter: Int, lastVerse: Int, now: Long = System.currentTimeMillis()): Boolean {
        return database.withTransaction {
            val progressDao = database.readingProgress()
            val current = progressDao.progress(book, chapter) ?: return@withTransaction false
            if (current.completedAt?.let(::dayKey) == dayKey(now)) return@withTransaction false
            progressDao.markCompleted(book, chapter, lastVerse, now)
            val day = dayKey(now)
            database.dailyStatistics().ensure(DailyStatisticsEntity(dayKey = day))
            database.dailyStatistics().addCompletedChapter(day, lastVerse)
            true
        }
    }

    suspend fun continueReadingTarget(): ContinueReadingTarget? {
        val books = database.bible().books()
        val ordered = books.flatMap { book -> database.bible().chapters(book).map { chapter -> ContinueReadingTarget(book, chapter) } }
        return ContinueReadingSelector.select(ordered, database.readingProgress().allProgress())
    }

    suspend fun snapshot(now: Long = System.currentTimeMillis()): StatisticsSnapshot {
        val day = dayKey(now)
        val statsDao = database.dailyStatistics()
        val activityDao = database.activitySessions()
        val daily = statsDao.forDay(day)
        val readingDays = statsDao.readingDays()
        val weekStart = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            while (get(Calendar.DAY_OF_WEEK) != firstDayOfWeek) add(Calendar.DAY_OF_YEAR, -1)
        }
        val activity = activityDao.weeklyActivity(weekStart.timeInMillis).associateBy { it.dayKey }
        val week = (0..6).map { offset ->
            val cal = weekStart.clone() as Calendar
            cal.add(Calendar.DAY_OF_YEAR, offset)
            val key = dayKey(cal.timeInMillis)
            val dayStats = statsDao.forDay(key)
            WeekDayActivity(
                dayKey = key,
                label = SimpleDateFormat("EE", Locale.getDefault()).format(cal.time),
                durationMs = activity[key]?.durationMs ?: 0L,
                chapters = dayStats?.completedChapters ?: 0
            )
        }
        return StatisticsSnapshot(
            sessions = daily?.sessions ?: 0,
            totalTimeMs = activityDao.totalTime(day),
            bibleTimeMs = activityDao.timeForModule(day, "Bible"),
            chatTimeMs = activityDao.timeForModule(day, "Ciel"),
            otherTimeMs = activityDao.timeForModule(day, "Other"),
            booksVisited = database.readingProgress().visitedBookCount(),
            chaptersOpened = database.readingProgress().openedChapterCount(),
            chaptersCompleted = database.readingProgress().completedChapterCount(),
            versesRead = statsDao.totalCompletedVerses(),
            totalBibleChapters = database.bible().chapterCount(),
            readingStreak = ReadingStreaks.current(readingDays.map { it.dayKey }, now),
            longestReadingStreak = ReadingStreaks.longest(readingDays.map { it.dayKey }),
            totalReadingDays = readingDays.size,
            lastReadingDay = readingDays.firstOrNull()?.dayKey,
            week = week,
            recentChapters = database.readingProgress().recent()
        )
    }

    private fun dayKey(time: Long): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(time))
}
