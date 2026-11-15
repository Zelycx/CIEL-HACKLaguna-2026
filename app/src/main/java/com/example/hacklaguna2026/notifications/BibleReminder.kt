package com.example.hacklaguna2026.notifications

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.hacklaguna2026.MainActivity
import com.example.hacklaguna2026.R
import com.example.hacklaguna2026.data.AppDatabase
import com.example.hacklaguna2026.stats.AppStatsRepository
import com.example.hacklaguna2026.stats.ReadingStreaks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Calendar

object BibleReminderScheduler {
    const val PREFERENCES = "bible_reading_reminder"
    const val KEY_ENABLED = "enabled"
    const val KEY_HOUR = "hour"
    const val KEY_MINUTE = "minute"
    const val ACTION_REMINDER = "com.example.hacklaguna2026.action.BIBLE_REMINDER"
    const val EXTRA_OPEN_BIBLE = "open_bible_from_notification"
    const val EXTRA_BOOK = "notification_book"
    const val EXTRA_CHAPTER = "notification_chapter"
    const val KEY_LAST_STREAK_WARNING_DAY = "last_streak_warning_day"
    private const val CHANNEL_ID = "daily_bible_reading"
    private const val REQUEST_CODE = 4217

    fun schedule(context: Context, hour: Int, minute: Int, now: Long = System.currentTimeMillis()) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val firstTime = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
            set(Calendar.MINUTE, minute.coerceIn(0, 59))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= now) add(Calendar.DAY_OF_YEAR, 1)
        }.timeInMillis
        val pending = reminderPendingIntent(context)
        alarmManager.cancel(pending)
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, firstTime, pending)
    }

    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        reminderPendingIntent(context).also(alarmManager::cancel)
    }

    fun scheduleFromSavedPreferences(context: Context) {
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        if (!preferences.getBoolean(KEY_ENABLED, false)) {
            cancel(context)
            return
        }
        schedule(
            context,
            preferences.getInt(KEY_HOUR, 8),
            preferences.getInt(KEY_MINUTE, 0)
        )
    }

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(NotificationChannel(
            CHANNEL_ID,
            "Daily Bible Reminder",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "A gentle reminder to spend a little time reading the Bible."
        })
    }

    private fun reminderPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, BibleReminderReceiver::class.java).setAction(ACTION_REMINDER)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    internal fun showNotification(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createChannel(context)
        val openBible = Intent(context, MainActivity::class.java).apply {
            putExtra(EXTRA_OPEN_BIBLE, true)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE + 1,
            openBible,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("A Moment with God")
            .setContentText("Take a little time today to read His Word. Your Bible is waiting.")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("Take a little time today to read His Word. Your daily Bible journey is waiting."))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()

        val allowed = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (allowed) runCatching { manager.notify(REQUEST_CODE, notification) }
    }

    internal fun showStreakWarning(context: Context, book: String, chapter: Int) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createChannel(context)
        val openBible = Intent(context, MainActivity::class.java).apply {
            putExtra(EXTRA_OPEN_BIBLE, true)
            putExtra(EXTRA_BOOK, book)
            putExtra(EXTRA_CHAPTER, chapter)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE + 2,
            openBible,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val message = "You haven't completed today's Bible reading yet. Spend a little time with God's Word and keep your journey going."
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Your reading streak is waiting!")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        val allowed = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (allowed) runCatching { manager.notify(REQUEST_CODE + 1, notification) }
    }
}

class BibleReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val preferences = context.getSharedPreferences(BibleReminderScheduler.PREFERENCES, Context.MODE_PRIVATE)
        if (!preferences.getBoolean(BibleReminderScheduler.KEY_ENABLED, false)) return
        // One alarm per day keeps the selected local clock time stable across time-zone changes.
        BibleReminderScheduler.scheduleFromSavedPreferences(context)
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val database = AppDatabase.get(context)
                val now = System.currentTimeMillis()
                val today = ReadingStreaks.key(now)
                val readingDays = database.dailyStatistics().readingDays().map { it.dayKey }.toSet()
                val completedToday = (database.dailyStatistics().forDay(today)?.completedChapters ?: 0) > 0
                val streakAtRisk = !completedToday && ReadingStreaks.isAtRisk(readingDays, now)
                val lastWarning = preferences.getString(BibleReminderScheduler.KEY_LAST_STREAK_WARNING_DAY, null)
                if (streakAtRisk) {
                    if (!ReadingStreaks.shouldWarnToday(readingDays, completedToday, lastWarning, now)) return@launch
                    val stats = AppStatsRepository(database)
                    val target = stats.continueReadingTarget() ?: run {
                        val books = database.bible().books()
                        val lastBook = books.lastOrNull()
                        val lastChapter = lastBook?.let { database.bible().chapters(it).lastOrNull() }
                        if (lastBook != null && lastChapter != null) com.example.hacklaguna2026.stats.ContinueReadingTarget(lastBook, lastChapter) else null
                    }
                    preferences.edit().putString(BibleReminderScheduler.KEY_LAST_STREAK_WARNING_DAY, today).apply()
                    target?.let { BibleReminderScheduler.showStreakWarning(context, it.book, it.chapter) }
                } else {
                    BibleReminderScheduler.showNotification(context)
                }
            } catch (error: Exception) {
                android.util.Log.w("BibleReminder", "Could not prepare the daily reading reminder", error)
                BibleReminderScheduler.showNotification(context)
            } finally {
                pendingResult.finish()
            }
        }
    }
}

class BibleReminderRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        BibleReminderScheduler.scheduleFromSavedPreferences(context)
    }
}
