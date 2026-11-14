package com.example.hacklaguna2026.auth

import android.util.Base64
import com.example.hacklaguna2026.data.UserSessionEntity
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object DemoAuthenticator {
    fun createAccount(username: String, password: String): UserSessionEntity {
        val salt = ByteArray(16).also(SecureRandom()::nextBytes)
        return UserSessionEntity(
            username = username.trim(),
            passwordSalt = Base64.encodeToString(salt, Base64.NO_WRAP),
            passwordHash = hash(password, salt),
            isLoggedIn = true
        )
    }

    fun verify(account: UserSessionEntity, password: String): Boolean {
        val salt = runCatching { Base64.decode(account.passwordSalt, Base64.NO_WRAP) }.getOrNull() ?: return false
        val expected = runCatching { Base64.decode(account.passwordHash, Base64.NO_WRAP) }.getOrNull() ?: return false
        val candidate = runCatching { Base64.decode(hash(password, salt), Base64.NO_WRAP) }.getOrNull() ?: return false
        return MessageDigest.isEqual(expected, candidate)
    }

    private fun hash(password: String, salt: ByteArray): String {
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_BITS)
        return try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec).encoded
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } finally {
            spec.clearPassword()
        }
    }

    private const val ITERATIONS = 160_000
    private const val KEY_BITS = 256
}
