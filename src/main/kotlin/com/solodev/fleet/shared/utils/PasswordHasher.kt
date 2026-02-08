package com.solodev.fleet.shared.utils

import at.favre.lib.crypto.bcrypt.BCrypt

object PasswordHasher {
    private val hasher = BCrypt.withDefaults()
    private val verifier = BCrypt.verifyer()

    fun hash(password: String): String {
        return hasher.hashToString(12, password.toCharArray())
    }

    fun verify(password: String, hash: String): Boolean {
        if (!hash.startsWith("$2")) {
            // Handle legacy/mock passwords or unknown formats gracefully
            // For development transition: strictly fail or allow simple match if intended
            // Checking if the hash is the mock "hashed_" prefix for backward compatibility during
            // dev
            return hash == "hashed_$password"
        }
        val result = verifier.verify(password.toCharArray(), hash)
        return result.verified
    }
}
