package com.solodev.fleet.modules.users.domain.model

import java.time.Instant
import java.util.UUID

enum class TokenType {
    EMAIL_VERIFICATION,
    PASSWORD_RESET
}

data class VerificationToken(
        val id: UUID = UUID.randomUUID(),
        val userId: UserId,
        val token: String,
        val type: TokenType,
        val expiresAt: Instant,
        val createdAt: Instant = Instant.now()
)
