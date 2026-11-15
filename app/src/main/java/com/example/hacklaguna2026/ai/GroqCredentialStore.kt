package com.example.hacklaguna2026.ai

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.system.Os
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Stores the user's Groq key as AES-GCM ciphertext under noBackupFilesDir. */
class GroqCredentialStore(context: Context) {
    private val file = File(File(context.applicationContext.noBackupFilesDir, "credentials"), FILE_NAME)

    fun hasKey(): Boolean = file.isFile

    @Synchronized
    fun read(): String? {
        if (!file.isFile) return null
        return runCatching {
            val encrypted = FileInputStream(file).use { stream ->
                require(file.length() in (MAGIC.size + IV_SIZE + TAG_SIZE_BYTES)..MAX_CREDENTIAL_FILE_BYTES) {
                    "The saved online key could not be read. Remove it and add it again."
                }
                stream.readBytes()
            }
            require(encrypted.copyOfRange(0, MAGIC.size).contentEquals(MAGIC))
            val ivStart = MAGIC.size
            val ivEnd = ivStart + IV_SIZE
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getKey(), GCMParameterSpec(TAG_SIZE_BITS, encrypted.copyOfRange(ivStart, ivEnd)))
            cipher.updateAAD(MAGIC)
            String(cipher.doFinal(encrypted.copyOfRange(ivEnd, encrypted.size)), StandardCharsets.UTF_8)
        }.getOrElse { throw CredentialStorageException("The saved online key could not be read. Remove it and add it again.", it) }
    }

    @Synchronized
    fun save(apiKey: String) {
        require(apiKey.isNotBlank())
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        cipher.updateAAD(MAGIC)
        val encrypted = MAGIC + cipher.iv + cipher.doFinal(apiKey.toByteArray(StandardCharsets.UTF_8))
        val directory = file.parentFile ?: throw CredentialStorageException("Secure storage is unavailable.")
        if (!directory.exists() && !directory.mkdirs()) throw CredentialStorageException("Secure storage is unavailable.")
        val temporary = File(directory, "$FILE_NAME.tmp")
        try {
            FileOutputStream(temporary).use { output ->
                output.write(encrypted)
                output.fd.sync()
            }
            Os.rename(temporary.absolutePath, file.absolutePath)
        } catch (error: Exception) {
            temporary.delete()
            throw CredentialStorageException("The online key could not be saved securely.", error)
        } finally {
            encrypted.fill(0)
        }
    }

    @Synchronized
    fun delete() {
        file.delete()
        File(file.parentFile, "$FILE_NAME.tmp").delete()
        runCatching {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (keyStore.containsAlias(KEY_ALIAS)) keyStore.deleteEntry(KEY_ALIAS)
        }.getOrElse { throw CredentialStorageException("The online key could not be removed from secure storage.", it) }
    }

    private fun getKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        return (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)
            ?: throw IllegalStateException("The encryption key is unavailable.")
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .setUserAuthenticationRequired(false)
                .build())
            generateKey()
        }
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "ciel_groq_api_key_v1"
        private const val FILE_NAME = "groq-key.aesgcm"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_SIZE = 12
        private const val TAG_SIZE_BITS = 128
        private const val TAG_SIZE_BYTES = TAG_SIZE_BITS / 8
        private const val MAX_CREDENTIAL_FILE_BYTES = 4_096L
        private val MAGIC = "CIELKEY1".toByteArray(StandardCharsets.US_ASCII)
    }
}

class CredentialStorageException(message: String, cause: Throwable? = null) : Exception(message, cause)
