package com.example.hacklaguna2026.data

import android.util.Base64
import androidx.room.withTransaction
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class KumpisalEntry(
    val id: Long,
    val content: String,
    val selectedPrompts: String,
    val createdAt: Long,
    val updatedAt: Long
)

enum class KumpisalAccessState { SETUP_REQUIRED, LOCKED, UNLOCKED }

/** Password-derived AES-GCM encryption for Kumpisal content and selected prompts. */
class KumpisalSecurityManager(private val database: AppDatabase) {
    private val sessionLock = Any()
    @Volatile private var sessionKey: ByteArray? = null
    private var sessionGeneration = 0L

    fun isUnlocked(): Boolean = sessionKey != null

    suspend fun accessState(): KumpisalAccessState =
        if (database.kumpisalSecurity().get() == null) KumpisalAccessState.SETUP_REQUIRED
        else if (isUnlocked()) KumpisalAccessState.UNLOCKED else KumpisalAccessState.LOCKED

    fun lock() {
        synchronized(sessionLock) {
            sessionGeneration++
            sessionKey?.fill(0)
            sessionKey = null
        }
    }

    suspend fun createPassword(password: CharArray) {
        val salt = ByteArray(SALT_BYTES)
        var derivedKey: ByteArray? = null
        try {
            validatePassword(password)
            val generation = generation()
            random.nextBytes(salt)
            val key = deriveKey(password, salt, PBKDF2_ITERATIONS)
            derivedKey = key
            val verifier = encryptVerifier(key)
            database.openHelper.writableDatabase.query("PRAGMA secure_delete=ON").close()
            database.withTransaction {
                check(database.kumpisalSecurity().get() == null) { "Kumpisal is already password protected." }
                val reflections = database.reflections()
                val legacyEntries = reflections.all()
                check(legacyEntries.all { !it.isEncrypted }) { "Kumpisal migration needs attention before setup can continue." }
                val originals = legacyEntries.associate { entry ->
                    entry.id to listOf(entry.content, entry.selectedPrompts, entry.createdAt.toString(), entry.updatedAt.toString())
                }
                legacyEntries.forEach { entry ->
                    reflections.update(entry.copy(
                        content = encryptField(key, entry.id, FIELD_CONTENT, entry.content),
                        selectedPrompts = encryptField(key, entry.id, FIELD_PROMPTS, entry.selectedPrompts),
                        isEncrypted = true
                    ))
                }
                val migrated = reflections.all()
                check(migrated.size == legacyEntries.size)
                migrated.forEach { entry ->
                    check(entry.isEncrypted)
                    val original = originals[entry.id] ?: error("A Kumpisal entry changed during migration.")
                    check(decryptField(key, entry.id, FIELD_CONTENT, entry.content) == original[0])
                    check(decryptField(key, entry.id, FIELD_PROMPTS, entry.selectedPrompts) == original[1])
                    check(entry.createdAt.toString() == original[2] && entry.updatedAt.toString() == original[3])
                }
                database.kumpisalSecurity().save(KumpisalSecurityEntity(
                    salt = Base64.encodeToString(salt, Base64.NO_WRAP),
                    verifier = verifier,
                    iterations = PBKDF2_ITERATIONS
                ))
            }
            compactLegacyPages()
            installKeyIfCurrent(key, generation)
        } finally {
            derivedKey?.fill(0)
            password.fill(Char.MIN_VALUE)
            salt.fill(0)
        }
    }

    suspend fun unlock(password: CharArray) {
        var salt: ByteArray? = null
        var derivedKey: ByteArray? = null
        try {
            val generation = generation()
            val security = database.kumpisalSecurity().get()
                ?: throw KumpisalPasswordException("Create a password to protect your private entries.")
            val decodedSalt = try {
                Base64.decode(security.salt, Base64.NO_WRAP)
            } catch (_: IllegalArgumentException) {
                throw KumpisalPasswordException("Kumpisal's password data could not be read.")
            }
            salt = decodedSalt
            val iterations = security.iterations
            if (iterations !in MIN_PBKDF2_ITERATIONS..MAX_PBKDF2_ITERATIONS || decodedSalt.size != SALT_BYTES) {
                throw KumpisalPasswordException("Kumpisal's password data could not be read.")
            }
            val key = deriveKey(password, decodedSalt, iterations)
            derivedKey = key
            if (!verifyKey(key, security.verifier)) {
                throw KumpisalPasswordException("That password did not match. Your entries remain locked.")
            }
            val reflections = database.reflections().all()
            if (reflections.any { !it.isEncrypted }) {
                throw KumpisalPasswordException("A private entry could not be verified, so Kumpisal remains locked.")
            }
            reflections.forEach { entry ->
                decryptField(key, entry.id, FIELD_CONTENT, entry.content)
                decryptField(key, entry.id, FIELD_PROMPTS, entry.selectedPrompts)
            }
            installKeyIfCurrent(key, generation)
        } finally {
            derivedKey?.fill(0)
            salt?.fill(0)
            password.fill(Char.MIN_VALUE)
        }
    }

    suspend fun entries(): List<KumpisalEntry> {
        val key = sessionKeyCopy()
        return try {
            database.reflections().all().map { entry ->
                check(entry.isEncrypted) { "An entry is not encrypted." }
                KumpisalEntry(
                    id = entry.id,
                    content = decryptField(key, entry.id, FIELD_CONTENT, entry.content),
                    selectedPrompts = decryptField(key, entry.id, FIELD_PROMPTS, entry.selectedPrompts),
                    createdAt = entry.createdAt,
                    updatedAt = entry.updatedAt
                )
            }
        } finally {
            key.fill(0)
        }
    }

    suspend fun save(content: String, selectedPrompts: String, now: Long = System.currentTimeMillis()): Long {
        val key = sessionKeyCopy()
        return try {
            database.withTransaction {
                val id = database.reflections().nextId()
                database.reflections().save(ReflectionEntity(
                    id = id,
                    content = encryptField(key, id, FIELD_CONTENT, content),
                    selectedPrompts = encryptField(key, id, FIELD_PROMPTS, selectedPrompts),
                    createdAt = now,
                    updatedAt = now,
                    isEncrypted = true
                ))
            }
        } finally {
            key.fill(0)
        }
    }

    suspend fun update(id: Long, content: String, selectedPrompts: String, now: Long = System.currentTimeMillis()) {
        val key = sessionKeyCopy()
        try {
            database.withTransaction {
                val existing = database.reflections().byId(id) ?: return@withTransaction
                check(existing.isEncrypted) { "An entry is not encrypted." }
                database.reflections().update(existing.copy(
                    content = encryptField(key, id, FIELD_CONTENT, content),
                    selectedPrompts = encryptField(key, id, FIELD_PROMPTS, selectedPrompts),
                    updatedAt = now
                ))
            }
        } finally {
            key.fill(0)
        }
    }

    suspend fun delete(id: Long) {
        sessionKeyCopy().fill(0)
        database.reflections().delete(id)
    }

    /** Destructive reset is only called after the UI's explicit confirmation. */
    suspend fun resetAndDeleteEntries() {
        lock()
        database.withTransaction {
            database.reflections().all().forEach { database.reflections().delete(it.id) }
            database.kumpisalSecurity().delete()
        }
        compactLegacyPages()
    }

    private fun validatePassword(password: CharArray) {
        if (password.size < MIN_PASSWORD_LENGTH) {
            password.fill(Char.MIN_VALUE)
            throw KumpisalPasswordException("Use a password or passphrase with at least 8 characters.")
        }
        if (password.size > MAX_PASSWORD_LENGTH) {
            password.fill(Char.MIN_VALUE)
            throw KumpisalPasswordException("That passphrase is too long. Use 128 characters or fewer.")
        }
    }

    private fun generation(): Long = synchronized(sessionLock) { sessionGeneration }

    private fun installKeyIfCurrent(key: ByteArray, generation: Long) {
        synchronized(sessionLock) {
            if (generation != sessionGeneration) {
                throw KumpisalPasswordException("Kumpisal locked while the password was being checked. Try again.")
            }
            sessionKey?.fill(0)
            sessionKey = key.copyOf()
        }
    }

    private fun sessionKeyCopy(): ByteArray = synchronized(sessionLock) {
        sessionKey?.copyOf() ?: throw KumpisalPasswordException("Unlock Kumpisal before opening an entry.")
    }

    private fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password, salt, iterations, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun encryptField(key: ByteArray, entryId: Long, field: String, plainText: String): String {
        val nonce = ByteArray(GCM_NONCE_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance(AES_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, AES_ALGORITHM), GCMParameterSpec(GCM_TAG_BITS, nonce))
        cipher.updateAAD(aad(entryId, field))
        val encrypted = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
        return CIPHERTEXT_PREFIX + Base64.encodeToString(nonce + encrypted, Base64.NO_WRAP)
    }

    private fun decryptField(key: ByteArray, entryId: Long, field: String, encryptedText: String): String {
        if (!encryptedText.startsWith(CIPHERTEXT_PREFIX)) {
            throw KumpisalPasswordException("A private entry could not be decrypted. It remains unchanged.")
        }
        return try {
            val packed = Base64.decode(encryptedText.removePrefix(CIPHERTEXT_PREFIX), Base64.NO_WRAP)
            require(packed.size > GCM_NONCE_BYTES)
            val nonce = packed.copyOfRange(0, GCM_NONCE_BYTES)
            val cipher = Cipher.getInstance(AES_TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, AES_ALGORITHM), GCMParameterSpec(GCM_TAG_BITS, nonce))
            cipher.updateAAD(aad(entryId, field))
            String(cipher.doFinal(packed.copyOfRange(GCM_NONCE_BYTES, packed.size)), StandardCharsets.UTF_8)
        } catch (_: AEADBadTagException) {
            throw KumpisalPasswordException("A private entry could not be verified. It remains locked.")
        } catch (_: Exception) {
            throw KumpisalPasswordException("A private entry could not be decrypted. It remains unchanged.")
        }
    }

    private fun encryptVerifier(key: ByteArray): String {
        val nonce = ByteArray(GCM_NONCE_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance(AES_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, AES_ALGORITHM), GCMParameterSpec(GCM_TAG_BITS, nonce))
        cipher.updateAAD(VERIFIER_AAD)
        val encrypted = cipher.doFinal(VERIFIER_TEXT)
        return Base64.encodeToString(nonce + encrypted, Base64.NO_WRAP)
    }

    private fun verifyKey(key: ByteArray, verifier: String): Boolean = try {
        val packed = Base64.decode(verifier, Base64.NO_WRAP)
        require(packed.size > GCM_NONCE_BYTES)
        val cipher = Cipher.getInstance(AES_TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(key, AES_ALGORITHM),
            GCMParameterSpec(GCM_TAG_BITS, packed.copyOfRange(0, GCM_NONCE_BYTES))
        )
        cipher.updateAAD(VERIFIER_AAD)
        MessageDigest.isEqual(
            cipher.doFinal(packed.copyOfRange(GCM_NONCE_BYTES, packed.size)),
            VERIFIER_TEXT
        )
    } catch (_: Exception) {
        false
    }

    private fun aad(entryId: Long, field: String): ByteArray =
        ("Ciel Kumpisal v1|$entryId|$field").toByteArray(StandardCharsets.UTF_8)

    private fun compactLegacyPages() {
        runCatching {
            val sql = database.openHelper.writableDatabase
            sql.query("PRAGMA secure_delete=ON").close()
            sql.query("PRAGMA wal_checkpoint(TRUNCATE)").close()
            sql.execSQL("VACUUM")
            sql.query("PRAGMA wal_checkpoint(TRUNCATE)").close()
        }
    }

    companion object {
        const val MIN_PASSWORD_LENGTH = 8
        private const val MAX_PASSWORD_LENGTH = 128
        private const val PBKDF2_ITERATIONS = 600_000
        private const val MIN_PBKDF2_ITERATIONS = 100_000
        private const val MAX_PBKDF2_ITERATIONS = 1_000_000
        private const val KEY_BITS = 256
        private const val SALT_BYTES = 16
        private const val GCM_NONCE_BYTES = 12
        private const val GCM_TAG_BITS = 128
        private const val AES_ALGORITHM = "AES"
        private const val AES_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val CIPHERTEXT_PREFIX = "kumpisal:v1:"
        private const val FIELD_CONTENT = "content"
        private const val FIELD_PROMPTS = "selectedPrompts"
        private val VERIFIER_AAD = "Ciel Kumpisal password verifier v1".toByteArray(StandardCharsets.UTF_8)
        private val VERIFIER_TEXT = "Kumpisal password verified".toByteArray(StandardCharsets.UTF_8)
        private val random = SecureRandom()
    }
}

class KumpisalPasswordException(message: String) : Exception(message)
