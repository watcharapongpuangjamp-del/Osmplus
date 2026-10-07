package com.example.util

import java.security.MessageDigest

object SecurityUtils {
    /**
     * Hashes the given input string using SHA-256.
     * In a production environment, use PBKDF2 or Argon2 with salt.
     */
    fun hashPin(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(pin.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Verifies if the raw PIN matches the hashed PIN.
     */
    fun verifyPin(rawPin: String, hashedPin: String): Boolean {
        return hashPin(rawPin) == hashedPin
    }
}
