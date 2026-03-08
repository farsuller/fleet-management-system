package com.solodev.fleet.modules.users.application.usecases

import com.solodev.fleet.modules.users.domain.model.*
import com.solodev.fleet.modules.users.domain.repository.UserRepository
import com.solodev.fleet.modules.users.domain.repository.VerificationTokenRepository
import io.mockk.*
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.time.temporal.ChronoUnit
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class VerifyEmailUseCaseTest {

    private val userRepository = mockk<UserRepository>()
    private val tokenRepository = mockk<VerificationTokenRepository>()
    private val useCase = VerifyEmailUseCase(userRepository, tokenRepository)

    @Test
    fun shouldVerifyEmail_WhenTokenIsValid() = runBlocking {
        // Arrange
        val token = VerificationToken(
            userId = UserId("user-001"),
            token = "valid-token",
            type = TokenType.EMAIL_VERIFICATION,
            expiresAt = Instant.now().plus(1, ChronoUnit.HOURS)
        )
        val user = sampleUser(isVerified = false)
        val savedUser = slot<User>()
        coEvery { tokenRepository.findByToken("valid-token", TokenType.EMAIL_VERIFICATION) } returns token
        coEvery { userRepository.findById(UserId("user-001")) } returns user
        coEvery { userRepository.save(capture(savedUser)) } returnsArgument 0
        coEvery { tokenRepository.deleteByToken("valid-token") } just Runs

        // Act
        useCase.execute("valid-token")

        // Assert
        assertThat(savedUser.captured.isVerified).isTrue()
    }

    @Test
    fun shouldThrowIllegalArgument_WhenTokenIsExpired() {
        // Arrange
        val token = VerificationToken(
            userId = UserId("user-001"),
            token = "expired-token",
            type = TokenType.EMAIL_VERIFICATION,
            expiresAt = Instant.now().minus(1, ChronoUnit.HOURS)
        )
        coEvery { tokenRepository.findByToken("expired-token", TokenType.EMAIL_VERIFICATION) } returns token

        // Act & Assert
        assertThatThrownBy { runBlocking { useCase.execute("expired-token") } }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun shouldThrowIllegalArgument_WhenTokenNotFound() {
        // Arrange
        coEvery { tokenRepository.findByToken("unknown-token", TokenType.EMAIL_VERIFICATION) } returns null

        // Act & Assert
        assertThatThrownBy { runBlocking { useCase.execute("unknown-token") } }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    private fun sampleUser(isVerified: Boolean) = User(
        id = UserId("user-001"),
        email = "juan@fleet.ph",
        firstName = "Juan",
        lastName = "dela Cruz",
        passwordHash = "hashed_pw",
        phone = "+63912345678",
        isVerified = isVerified,
        roles = emptyList()
    )
}
