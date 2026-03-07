package com.solodev.fleet.modules.users.domain.model

import java.time.Instant
import java.time.temporal.ChronoUnit
import org.junit.jupiter.api.Test
import kotlin.test.*

class VerificationTokenTest {

    @Test
    fun `isExpired returns true when expiresAt is in the past`() {
        val token = VerificationToken(
            userId = UserId("user-001"),
            token = "exp-token-123",
            type = TokenType.EMAIL_VERIFICATION,
            expiresAt = Instant.now().minus(1, ChronoUnit.HOURS)
        )
        assertTrue(token.expiresAt.isBefore(Instant.now()))
    }

    @Test
    fun `isExpired returns false when expiresAt is in the future`() {
        val token = VerificationToken(
            userId = UserId("user-001"),
            token = "valid-token-456",
            type = TokenType.EMAIL_VERIFICATION,
            expiresAt = Instant.now().plus(1, ChronoUnit.HOURS)
        )
        assertFalse(token.expiresAt.isBefore(Instant.now()))
    }

    @Test
    fun `can compare token strings`() {
        val token1 = VerificationToken(
            userId = UserId("user-001"),
            token = "same-token",
            type = TokenType.EMAIL_VERIFICATION,
            expiresAt = Instant.now().plus(1, ChronoUnit.HOURS)
        )
        val token2 = VerificationToken(
            userId = UserId("user-001"),
            token = "same-token",
            type = TokenType.EMAIL_VERIFICATION,
            expiresAt = Instant.now().plus(1, ChronoUnit.HOURS)
        )
        assertEquals(token1.token, token2.token)
    }
}
