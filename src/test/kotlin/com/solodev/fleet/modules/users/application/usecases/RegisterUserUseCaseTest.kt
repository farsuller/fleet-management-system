package com.solodev.fleet.modules.users.application.usecases

import com.solodev.fleet.modules.users.application.dto.UserRegistrationRequest
import com.solodev.fleet.modules.users.domain.model.*
import com.solodev.fleet.modules.users.domain.repository.UserRepository
import com.solodev.fleet.modules.users.domain.repository.VerificationTokenRepository
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class RegisterUserUseCaseTest {

    private val userRepository = mockk<UserRepository>()
    private val tokenRepository = mockk<VerificationTokenRepository>()
    private val useCase = RegisterUserUseCase(userRepository, tokenRepository)

    private val validRequest = UserRegistrationRequest(
        email = "juan@fleet.ph",
        firstName = "Juan",
        lastName = "dela Cruz",
        passwordRaw = "test-password-raw",
        phone = "+63912345678"
    )

    private val customerRole = Role(id = RoleId("role-cust"), name = "CUSTOMER")

    @Test
    fun shouldRegisterUser_WhenEmailIsNew() = runBlocking {
        // Arrange
        val savedUser = slot<User>()
        coEvery { userRepository.findByEmail("juan@fleet.ph") } returns null
        coEvery { userRepository.findRoleByName("CUSTOMER") } returns customerRole
        coEvery { userRepository.save(capture(savedUser)) } returnsArgument 0
        coEvery { tokenRepository.save(any()) } returnsArgument 0

        // Act
        val result = useCase.execute(validRequest)

        // Assert
        assertThat(result.email).isEqualTo("juan@fleet.ph")
        assertThat(result.isVerified).isFalse()
        assertThat(savedUser.captured.email).isEqualTo("juan@fleet.ph")
    }

    @Test
    fun shouldThrowIllegalState_WhenEmailAlreadyRegistered() {
        // Arrange
        coEvery { userRepository.findByEmail("juan@fleet.ph") } returns mockk()

        // Act & Assert
        assertThatThrownBy { runBlocking { useCase.execute(validRequest) } }
            .isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun shouldReturnUnverifiedUser_WhenRegistrationSucceeds() = runBlocking {
        // Arrange
        coEvery { userRepository.findByEmail("juan@fleet.ph") } returns null
        coEvery { userRepository.findRoleByName("CUSTOMER") } returns customerRole
        coEvery { userRepository.save(any()) } returnsArgument 0
        coEvery { tokenRepository.save(any()) } returnsArgument 0

        // Act
        val result = useCase.execute(validRequest)

        // Assert
        assertThat(result.isVerified).isFalse()
    }
}
