package com.solodev.fleet.modules.users.application.usecases

import com.solodev.fleet.modules.users.application.dto.LoginRequest
import com.solodev.fleet.modules.users.domain.model.*
import com.solodev.fleet.modules.users.domain.repository.UserRepository
import com.solodev.fleet.shared.utils.JwtService
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.*

class LoginUserUseCaseTest {

    private val userRepository = mockk<UserRepository>()
    private val jwtService = mockk<JwtService>()
    private val useCase = LoginUserUseCase(userRepository, jwtService)

    @Test
    fun `generates JWT for verified user with correct password`() = runBlocking {
        val user = sampleUser(isVerified = true)
        coEvery { userRepository.findByEmail("juan@fleet.ph") } returns user
        every { jwtService.generateToken(any(), any(), any()) } returns "jwt.token.here"

        val request = LoginRequest(email = "juan@fleet.ph", passwordRaw = "test-password-raw")
        val result = useCase.execute(request)

        assertEquals("jwt.token.here", result.second)
    }

    @Test
    fun `throws when user not found`(): Unit = runBlocking {
        coEvery { userRepository.findByEmail(any()) } returns null

        val request = LoginRequest(email = "unknown@fleet.ph", passwordRaw = "test-password-raw")
        assertFailsWith<IllegalArgumentException> {
            useCase.execute(request)
        }
    }

    @Test
    fun `throws when user is not verified`(): Unit = runBlocking {
        val user = sampleUser(isVerified = false)
        coEvery { userRepository.findByEmail("juan@fleet.ph") } returns user

        val request = LoginRequest(email = "juan@fleet.ph", passwordRaw = "test-password-raw")
        assertFailsWith<IllegalArgumentException> {
            useCase.execute(request)
        }
    }

    private fun sampleUser(isVerified: Boolean = true) = User(
        id = UserId("user-001"),
        email = "juan@fleet.ph",
        firstName = "Juan",
        lastName = "dela Cruz",
        passwordHash = "hashed_test_password",
        phone = "+63912345678",
        isVerified = isVerified,
        roles = emptyList()
    )
}
