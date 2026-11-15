package com.example.hacklaguna2026

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.hacklaguna2026.data.AppDatabase
import com.example.hacklaguna2026.data.KumpisalAccessState
import com.example.hacklaguna2026.data.KumpisalPasswordException
import com.example.hacklaguna2026.data.KumpisalSecurityManager
import com.example.hacklaguna2026.data.ReflectionEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONObject
import java.io.File

@RunWith(AndroidJUnit4::class)
class KumpisalEncryptionTest {
    @Test
    fun legacyRoomDatabaseMigrationPreservesJournalRowsAndThenEncryptsThem() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "kumpisal-migration-test.db"
        context.deleteDatabase(databaseName)
        createLegacyV1Database(context, databaseName)

        val roomDatabase = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .allowMainThreadQueries()
            .build()
        try {
            runBlocking {
                val manager = KumpisalSecurityManager(roomDatabase)
                assertEquals(KumpisalAccessState.SETUP_REQUIRED, manager.accessState())
                val beforeEncryption = roomDatabase.reflections().byId(41)!!
                assertFalse(beforeEncryption.isEncrypted)
                assertEquals("private legacy note", beforeEncryption.content)
                assertEquals("prompt one", beforeEncryption.selectedPrompts)
                manager.createPassword("a memorable passphrase".toCharArray())
                val stored = roomDatabase.reflections().byId(41)!!
                assertTrue(stored.isEncrypted)
                assertFalse(stored.content.contains("private legacy note"))
                assertFalse(stored.selectedPrompts.contains("prompt one"))
                assertEquals(101L, stored.createdAt)
                assertEquals(202L, stored.updatedAt)
                manager.lock()
                manager.unlock("a memorable passphrase".toCharArray())
                val entry = manager.entries().single()
                assertEquals(41L, entry.id)
                assertEquals("private legacy note", entry.content)
                assertEquals("prompt one", entry.selectedPrompts)
            }
        } finally {
            roomDatabase.close()
            context.deleteDatabase(databaseName)
        }
    }

    private fun createLegacyV1Database(context: Context, databaseName: String) {
        val schemaPath = "com.example.hacklaguna2026.data.AppDatabase/1.json"
        val schemaText = InstrumentationRegistry.getInstrumentation().context.assets
            .open(schemaPath).bufferedReader().use { it.readText() }
        val databaseSchema = JSONObject(schemaText).getJSONObject("database")
        val placeholder = "$" + "{TABLE_NAME}"
        val file: File = context.getDatabasePath(databaseName).apply { parentFile?.mkdirs() }
        val sqlite = SQLiteDatabase.openOrCreateDatabase(file, null)
        try {
            val entities = databaseSchema.getJSONArray("entities")
            for (entityIndex in 0 until entities.length()) {
                val entity = entities.getJSONObject(entityIndex)
                val tableName = entity.getString("tableName")
                sqlite.execSQL(entity.getString("createSql").replace(placeholder, tableName))
                val indices = entity.optJSONArray("indices")
                if (indices != null) {
                    for (index in 0 until indices.length()) {
                        val indexBundle = indices.getJSONObject(index)
                        val indexPlaceholder = "$" + "{INDEX_NAME}"
                        val indexSql = indexBundle.getString("createSql")
                            .replace(placeholder, tableName)
                            .replace(indexPlaceholder, indexBundle.getString("name"))
                        sqlite.execSQL(indexSql)
                    }
                }
            }
            sqlite.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
            sqlite.execSQL(
                "INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES (42, ?)",
                arrayOf(databaseSchema.getString("identityHash"))
            )
            sqlite.execSQL(
                "INSERT INTO ReflectionEntity (id, content, selectedPrompts, createdAt, updatedAt) " +
                    "VALUES (41, 'private legacy note', 'prompt one', 101, 202)"
            )
            sqlite.version = 1
        } finally {
            sqlite.close()
        }
    }

    @Test
    fun passwordProtectsExistingAndNewEntriesAndRejectsWrongPasswords() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            database.reflections().save(
                ReflectionEntity(
                    id = 7,
                    content = "legacy confession",
                    selectedPrompts = "forgiveness",
                    createdAt = 111,
                    updatedAt = 222
                )
            )
            val manager = KumpisalSecurityManager(database)
            manager.createPassword("local-only password".toCharArray())
            val encryptedLegacy = database.reflections().byId(7)!!
            assertTrue(encryptedLegacy.isEncrypted)
            assertFalse(encryptedLegacy.content.contains("legacy confession"))
            assertEquals(111L, encryptedLegacy.createdAt)
            assertEquals(222L, encryptedLegacy.updatedAt)

            manager.lock()
            try {
                manager.unlock("incorrect password".toCharArray())
                fail("A wrong password must not unlock Kumpisal.")
            } catch (_: KumpisalPasswordException) {
                assertFalse(manager.isUnlocked())
            }
            manager.unlock("local-only password".toCharArray())
            assertEquals("legacy confession", manager.entries().single().content)
            val id = manager.save("new private note", "prayer", 300)
            val storedNew = database.reflections().byId(id)!!
            assertTrue(storedNew.isEncrypted)
            assertFalse(storedNew.content.contains("new private note"))
            manager.update(7, "edited private confession", "repair", 400)
            assertEquals(111L, database.reflections().byId(7)!!.createdAt)
            assertEquals(400L, database.reflections().byId(7)!!.updatedAt)
            assertEquals("edited private confession", manager.entries().first { it.id == 7L }.content)
            manager.delete(id)
            assertEquals(listOf(7L), manager.entries().map { it.id })
            manager.lock()
            assertFalse(manager.isUnlocked())
            manager.resetAndDeleteEntries()
            assertEquals(KumpisalAccessState.SETUP_REQUIRED, manager.accessState())
            assertTrue(database.reflections().all().isEmpty())
        } finally {
            database.close()
        }
    }
}
