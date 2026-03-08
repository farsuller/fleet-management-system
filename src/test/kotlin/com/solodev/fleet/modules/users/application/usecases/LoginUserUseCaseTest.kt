package com.solodev.fleet.modules.users.application.usecases

import com.solodev.fleet.modules.users.application.dto.LoginRequest
import com.solodev.fleet.modules.users.domain.model.*
import com.solodev.fleet.modules.users.domain.repository.UserRepository
import com.solodev.fleet.shared.utils.JwtService
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class LoginUserUseCaseTest {

    private val userRepository = mockk<UserRepository>()
    private val jwtService = mockk<JwtService>()
    private val useCase = LoginUserUseCase(userRepository, jwtService)

    @Test
    fun shouldThrowIllegalArgument_WhenUserEmailNotFound() {
        // Arrange
        coEvery { userRepository.findByEmail("unknown@fleet.ph") } returns null
        val request = LoginRequest(email = "unknown@fleet.ph", passwordRaw = "irrelevant")

        // Act & Assert
        assertThatThrownBy { runBlocking { useCase.execute(request) } }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun shouldThrowIllegalArgument_WhenUserIsNotVerified() {
        // Arrange
        val user = sampleUser(isVerified = false)
        coEvery { userRepository.findByEmail("juan@fleet.ph") } returns user
        val request = LoginRequest(email = "juan@fleet.ph", passwordRaw = "irrelevant")

        // Act & Assert
        assertThatThrownBy { runBlocking { useCase.execute(request) } }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    private fun sampleUser(isVerified: Boolean) = User(
        id = UserId("user-001"),
        email = "juan@fleet.ph",
        firstName = "Juan",
        lastName = "dela Cruz",
        passwordHash = "not-checked",
        phone = "+63912345678",
        isVerified = isVerified,
        roles = emptyList()
    )
}
