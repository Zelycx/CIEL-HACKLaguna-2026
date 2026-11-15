package com.example.hacklaguna2026.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.Upsert
import androidx.room.Update

@Entity
data class ConversationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    indices = [Index("conversationId")],
    foreignKeys = [ForeignKey(
        entity = ConversationEntity::class,
        parentColumns = ["id"],
        childColumns = ["conversationId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: String,
    val role: String,
    val content: String,
    val createdAt: Long
)

@Entity(indices = [Index(value = ["book", "chapter", "verse"], unique = true)])
data class BibleVerseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookOrder: Int,
    val book: String,
    val chapter: Int,
    val verse: Int,
    val text: String
)

@Entity(indices = [Index(value = ["book", "chapter", "verse"], unique = true)])
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val book: String,
    val chapter: Int,
    val verse: Int,
    val text: String,
    val createdAt: Long
)

@Entity(primaryKeys = ["book", "chapter"])
data class ReadingProgressEntity(
    val book: String,
    val chapter: Int,
    val lastVerse: Int,
    val openedAt: Long,
    val completedAt: Long?,
    val openCount: Int
)

@Entity(indices = [Index("startedAt"), Index(value = ["dayKey", "module"])])
data class ActivitySessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dayKey: String,
    val module: String,
    val startedAt: Long,
    val durationMs: Long
)

@Entity
data class DailyStatisticsEntity(
    @PrimaryKey val dayKey: String,
    val sessions: Int = 0,
    val completedChapters: Int = 0,
    val completedVerses: Int = 0
)

@Entity
data class ReflectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val content: String,
    val selectedPrompts: String,
    val createdAt: Long,
    val updatedAt: Long,
    @ColumnInfo(defaultValue = "0") val isEncrypted: Boolean = false
)

@Entity
data class KumpisalSecurityEntity(
    @PrimaryKey val id: Int = 1,
    val salt: String,
    val verifier: String,
    val iterations: Int
)

@Entity
data class UserSessionEntity(
    @PrimaryKey val id: Int = 1,
    val username: String,
    val passwordSalt: String,
    val passwordHash: String,
    val isLoggedIn: Boolean
)

@Dao
interface ConversationDao {
    @Upsert
    suspend fun saveConversation(conversation: ConversationEntity)

    @Query("SELECT * FROM ConversationEntity ORDER BY updatedAt DESC")
    suspend fun allConversations(): List<ConversationEntity>

    @Query("SELECT * FROM ConversationEntity WHERE id = :id LIMIT 1")
    suspend fun conversation(id: String): ConversationEntity?

    @Query("SELECT * FROM ConversationEntity ORDER BY updatedAt DESC LIMIT 1")
    suspend fun latestConversation(): ConversationEntity?

    @Query("UPDATE ConversationEntity SET title = :title, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateTitle(id: String, title: String, updatedAt: Long)

    @Query("DELETE FROM ConversationEntity WHERE id = :id")
    suspend fun deleteConversation(id: String)
}

@Dao
interface MessageDao {
    @Insert
    suspend fun add(message: MessageEntity)

    @Query("SELECT * FROM MessageEntity WHERE conversationId = :conversationId ORDER BY id ASC")
    suspend fun forConversation(conversationId: String): List<MessageEntity>
}

@Dao
interface BibleDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun importVerses(verses: List<BibleVerseEntity>)

    @Query("DELETE FROM BibleVerseEntity")
    suspend fun clearVerses()

    @Query("SELECT COUNT(*) FROM BibleVerseEntity")
    suspend fun verseCount(): Int

    @Query("SELECT COUNT(*) FROM (SELECT book, chapter FROM BibleVerseEntity GROUP BY book, chapter)")
    suspend fun chapterCount(): Int

    @Query("SELECT book FROM BibleVerseEntity GROUP BY book ORDER BY MIN(bookOrder)")
    suspend fun books(): List<String>

    @Query("SELECT chapter FROM BibleVerseEntity WHERE book = :book GROUP BY chapter ORDER BY chapter")
    suspend fun chapters(book: String): List<Int>

    @Query("SELECT * FROM BibleVerseEntity WHERE book = :book AND chapter = :chapter ORDER BY verse")
    suspend fun chapter(book: String, chapter: Int): List<BibleVerseEntity>

    @Query("SELECT * FROM BibleVerseEntity ORDER BY bookOrder, chapter, verse")
    suspend fun allVerses(): List<BibleVerseEntity>

    @Query("SELECT * FROM BibleVerseEntity WHERE book = :book AND chapter = :chapter AND verse = :verse LIMIT 1")
    suspend fun verse(book: String, chapter: Int, verse: Int): BibleVerseEntity?
}

@Dao
interface BookmarkDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun add(bookmark: BookmarkEntity)

    @Query("SELECT * FROM BookmarkEntity ORDER BY createdAt DESC")
    suspend fun all(): List<BookmarkEntity>

    @Query("DELETE FROM BookmarkEntity WHERE book = :book AND chapter = :chapter AND verse = :verse")
    suspend fun remove(book: String, chapter: Int, verse: Int)

    @Query("SELECT EXISTS(SELECT 1 FROM BookmarkEntity WHERE book = :book AND chapter = :chapter AND verse = :verse)")
    suspend fun contains(book: String, chapter: Int, verse: Int): Boolean
}

@Dao
interface ReadingProgressDao {
    @Query("SELECT * FROM ReadingProgressEntity WHERE book = :book AND chapter = :chapter LIMIT 1")
    suspend fun progress(book: String, chapter: Int): ReadingProgressEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertOpened(progress: ReadingProgressEntity): Long

    @Query("UPDATE ReadingProgressEntity SET openedAt = :openedAt, openCount = openCount + 1 WHERE book = :book AND chapter = :chapter")
    suspend fun updateOpened(book: String, chapter: Int, openedAt: Long)

    @Query("UPDATE ReadingProgressEntity SET lastVerse = :lastVerse, completedAt = :completedAt WHERE book = :book AND chapter = :chapter")
    suspend fun markCompleted(book: String, chapter: Int, lastVerse: Int, completedAt: Long)

    @Query("UPDATE ReadingProgressEntity SET lastVerse = :lastVerse WHERE book = :book AND chapter = :chapter")
    suspend fun savePosition(book: String, chapter: Int, lastVerse: Int)

    @Query("SELECT COUNT(*) FROM ReadingProgressEntity")
    suspend fun openedChapterCount(): Int

    @Query("SELECT COUNT(DISTINCT book) FROM ReadingProgressEntity")
    suspend fun visitedBookCount(): Int

    @Query("SELECT COUNT(*) FROM ReadingProgressEntity WHERE completedAt IS NOT NULL")
    suspend fun completedChapterCount(): Int

    @Query("SELECT * FROM ReadingProgressEntity ORDER BY openedAt DESC LIMIT 6")
    suspend fun recent(): List<ReadingProgressEntity>

    @Query("SELECT * FROM ReadingProgressEntity")
    suspend fun allProgress(): List<ReadingProgressEntity>

    @Query("SELECT * FROM ReadingProgressEntity WHERE completedAt IS NOT NULL ORDER BY completedAt DESC")
    suspend fun completed(): List<ReadingProgressEntity>
}

@Dao
interface ActivitySessionDao {
    @Insert
    suspend fun add(session: ActivitySessionEntity)

    @Query("SELECT COALESCE(SUM(durationMs), 0) FROM ActivitySessionEntity WHERE dayKey = :dayKey")
    suspend fun totalTime(dayKey: String): Long

    @Query("SELECT COALESCE(SUM(durationMs), 0) FROM ActivitySessionEntity WHERE dayKey = :dayKey AND module = :module")
    suspend fun timeForModule(dayKey: String, module: String): Long

    @Query("SELECT dayKey, SUM(durationMs) AS durationMs FROM ActivitySessionEntity WHERE startedAt >= :since GROUP BY dayKey")
    suspend fun weeklyActivity(since: Long): List<DayActivity>
}

data class DayActivity(val dayKey: String, val durationMs: Long)

@Dao
interface DailyStatisticsDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun ensure(day: DailyStatisticsEntity)

    @Query("UPDATE DailyStatisticsEntity SET sessions = sessions + 1 WHERE dayKey = :dayKey")
    suspend fun incrementSessions(dayKey: String)

    @Query("UPDATE DailyStatisticsEntity SET completedChapters = completedChapters + 1, completedVerses = completedVerses + :verses WHERE dayKey = :dayKey")
    suspend fun addCompletedChapter(dayKey: String, verses: Int)

    @Query("SELECT * FROM DailyStatisticsEntity WHERE dayKey = :dayKey LIMIT 1")
    suspend fun forDay(dayKey: String): DailyStatisticsEntity?

    @Query("SELECT COALESCE(SUM(completedVerses), 0) FROM DailyStatisticsEntity")
    suspend fun totalCompletedVerses(): Int

    @Query("SELECT * FROM DailyStatisticsEntity WHERE completedChapters > 0 ORDER BY dayKey DESC")
    suspend fun readingDays(): List<DailyStatisticsEntity>

    @Query("SELECT * FROM DailyStatisticsEntity WHERE dayKey >= :sinceDay ORDER BY dayKey ASC")
    suspend fun recentDays(sinceDay: String): List<DailyStatisticsEntity>
}

@Dao
interface ReflectionDao {
    @Insert
    suspend fun save(reflection: ReflectionEntity): Long

    @Update
    suspend fun update(reflection: ReflectionEntity)

    @Query("SELECT * FROM ReflectionEntity ORDER BY updatedAt DESC")
    suspend fun all(): List<ReflectionEntity>

    @Query("SELECT * FROM ReflectionEntity WHERE id = :id LIMIT 1")
    suspend fun byId(id: Long): ReflectionEntity?

    @Query("DELETE FROM ReflectionEntity WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COALESCE(MAX(id), 0) + 1 FROM ReflectionEntity")
    suspend fun nextId(): Long
}

@Dao
interface KumpisalSecurityDao {
    @Query("SELECT * FROM KumpisalSecurityEntity WHERE id = 1 LIMIT 1")
    suspend fun get(): KumpisalSecurityEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(value: KumpisalSecurityEntity)

    @Query("DELETE FROM KumpisalSecurityEntity WHERE id = 1")
    suspend fun delete()
}

@Dao
interface UserSessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(account: UserSessionEntity)

    @Query("SELECT * FROM UserSessionEntity WHERE id = 1 LIMIT 1")
    suspend fun account(): UserSessionEntity?

    @Query("UPDATE UserSessionEntity SET isLoggedIn = :loggedIn WHERE id = 1")
    suspend fun setLoggedIn(loggedIn: Boolean)
}

@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        BibleVerseEntity::class,
        BookmarkEntity::class,
        ReadingProgressEntity::class,
        ActivitySessionEntity::class,
        DailyStatisticsEntity::class,
        ReflectionEntity::class,
        KumpisalSecurityEntity::class,
        UserSessionEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun conversations(): ConversationDao
    abstract fun messages(): MessageDao
    abstract fun bible(): BibleDao
    abstract fun bookmarks(): BookmarkDao
    abstract fun readingProgress(): ReadingProgressDao
    abstract fun activitySessions(): ActivitySessionDao
    abstract fun dailyStatistics(): DailyStatisticsDao
    abstract fun reflections(): ReflectionDao
    abstract fun kumpisalSecurity(): KumpisalSecurityDao
    abstract fun userSession(): UserSessionDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE ReflectionEntity ADD COLUMN isEncrypted INTEGER NOT NULL DEFAULT 0")
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS KumpisalSecurityEntity (" +
                        "id INTEGER NOT NULL, salt TEXT NOT NULL, verifier TEXT NOT NULL, " +
                        "iterations INTEGER NOT NULL, PRIMARY KEY(id))"
                )
            }
        }

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "chrchatbot.db"
            ).addMigrations(MIGRATION_1_2).build().also { instance = it }
        }
    }
}
