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
    val week: List<WeekDayActivity>,
    val recentChapters: List<com.example.hacklaguna2026.data.ReadingProgressEntity>
)

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

    suspend fun snapshot(now: Long = System.currentTimeMillis()): StatisticsSnapshot {
        val day = dayKey(now)
        val statsDao = database.dailyStatistics()
        val activityDao = database.activitySessions()
        val daily = statsDao.forDay(day)
        val weekStart = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, -6)
        }
        val activity = activityDao.weeklyActivity(weekStart.timeInMillis).associateBy { it.dayKey }
        val week = (6 downTo 0).map { offset ->
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
            readingStreak = streak(statsDao.readingDays(), now),
            week = week,
            recentChapters = database.readingProgress().recent()
        )
    }

    private fun streak(readingDays: List<DailyStatisticsEntity>, now: Long): Int {
        if (readingDays.isEmpty()) return 0
        val activity = readingDays.mapTo(HashSet()) { it.dayKey }
        val today = Calendar.getInstance().apply { timeInMillis = now }
        if (dayKey(today.timeInMillis) !in activity) today.add(Calendar.DAY_OF_YEAR, -1)
        var streak = 0
        while (dayKey(today.timeInMillis) in activity) {
            streak++
            today.add(Calendar.DAY_OF_YEAR, -1)
        }
        return streak
    }

    private fun dayKey(time: Long): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(time))
}
