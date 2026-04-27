package com.solodev.fleet.modules.users.application.usecases

import com.solodev.fleet.modules.users.application.dto.UserRegistrationRequest
import com.solodev.fleet.modules.users.domain.model.Role
import com.solodev.fleet.modules.users.domain.model.RoleId
import com.solodev.fleet.modules.users.domain.model.User
import com.solodev.fleet.modules.users.domain.repository.UserRepository
import com.solodev.fleet.modules.users.domain.repository.VerificationTokenRepository
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class RegisterUserUseCaseTest {
    private val userRepository = mockk<UserRepository>()
    private val tokenRepository = mockk<VerificationTokenRepository>()
    private val emailService = mockk<com.solodev.fleet.shared.infrastructure.email.EmailService>(relaxed = true)
    private val useCase = RegisterUserUseCase(userRepository, tokenRepository, emailService)

    private val validRequest =
        UserRegistrationRequest(
            email = "juan@fleet.ph",
            firstName = "Juan",
            lastName = "dela Cruz",
            passwordRaw = "T3st_P@ss",
            phone = "+639123456789",
        )

    private val customerSupportRole = Role(id = RoleId("role-cs"), name = "CUSTOMER_SUPPORT")

    @Test
    fun shouldRegisterUser_WhenEmailIsNew(): Unit =
        runBlocking {
            // Arrange
            val savedUser = slot<User>()
            coEvery { userRepository.findByEmail("juan@fleet.ph") } returns null
            coEvery { userRepository.findRoleByName("CUSTOMER_SUPPORT") } returns customerSupportRole
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
    fun shouldReturnUnverifiedUser_WhenRegistrationSucceeds(): Unit =
        runBlocking {
            // Arrange
            coEvery { userRepository.findByEmail("juan@fleet.ph") } returns null
            coEvery { userRepository.findRoleByName("CUSTOMER_SUPPORT") } returns customerSupportRole
            coEvery { userRepository.save(any()) } returnsArgument 0
            coEvery { tokenRepository.save(any()) } returnsArgument 0

            // Act
            val result = useCase.execute(validRequest)

            // Assert
            assertThat(result.isVerified).isFalse()
        }
}
