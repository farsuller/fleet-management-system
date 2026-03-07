package com.solodev.fleet.modules.users.application.usecases

import com.solodev.fleet.modules.users.domain.model.*
import com.solodev.fleet.modules.users.domain.repository.UserRepository
import com.solodev.fleet.modules.users.domain.repository.VerificationTokenRepository
import io.mockk.*
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.time.temporal.ChronoUnit
import org.junit.jupiter.api.Test
import kotlin.test.*

class VerifyEmailUseCaseTest {

    private val userRepository = mockk<UserRepository>()
    private val tokenRepository = mockk<VerificationTokenRepository>()
    private val useCase = VerifyEmailUseCase(userRepository, tokenRepository)

    @Test
    fun `verifies email with valid token`() = runBlocking {
        val token = VerificationToken(
            userId = UserId("user-001"),
            token = "valid-token",
            type = TokenType.EMAIL_VERIFICATION,
            expiresAt = Instant.now().plus(1, ChronoUnit.HOURS)
        )
        val user = sampleUser(isVerified = false)

        coEvery { tokenRepository.findByToken("valid-token", TokenType.EMAIL_VERIFICATION) } returns token
        coEvery { userRepository.findById(UserId("user-001")) } returns user
        coEvery { userRepository.save(any()) } returnsArgument 0
        coEvery { tokenRepository.deleteByToken("valid-token") } just Runs

        useCase.execute("valid-token")

        coVerify { userRepository.save(match { it.isVerified }) }
    }

    @Test
    fun `throws when token is expired`(): Unit = runBlocking {
        val token = VerificationToken(
            userId = UserId("user-001"),
            token = "expired-token",
            type = TokenType.EMAIL_VERIFICATION,
            expiresAt = Instant.now().minus(1, ChronoUnit.HOURS)
        )

        coEvery { tokenRepository.findByToken("expired-token", TokenType.EMAIL_VERIFICATION) } returns token

        assertFailsWith<IllegalArgumentException> {
            useCase.execute("expired-token")
        }
    }

    @Test
    fun `throws when token not found`(): Unit = runBlocking {
        coEvery { tokenRepository.findByToken(any(), any()) } returns null

        assertFailsWith<IllegalArgumentException> {
            useCase.execute("unknown-token")
        }
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
