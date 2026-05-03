package com.solodev.fleet.modules.users.application.usecases

import com.solodev.fleet.modules.users.application.dto.LoginRequest
import com.solodev.fleet.modules.users.domain.model.Role
import com.solodev.fleet.modules.users.domain.model.RoleId
import com.solodev.fleet.modules.users.domain.model.User
import com.solodev.fleet.modules.users.domain.model.UserId
import com.solodev.fleet.modules.users.domain.repository.UserRepository
import com.solodev.fleet.shared.utils.JwtService
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
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

    @Test
    fun shouldThrowIllegalArgument_WhenPasswordIsWrong() {
        // Arrange - PasswordHasher.verify returns false for "hashed_correct" vs "wrongpass"
        val user = sampleUser(isVerified = true, passwordHash = "hashed_correct")
        coEvery { userRepository.findByEmail("juan@fleet.ph") } returns user
        val request = LoginRequest(email = "juan@fleet.ph", passwordRaw = "wrongpass")

        // Act & Assert
        assertThatThrownBy { runBlocking { useCase.execute(request) } }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Invalid email or password")
    }

    @Test
    fun shouldReturnUserAndToken_WhenCredentialsAreValid() =
        runBlocking {
            // Arrange - PasswordHasher.verify returns true when hash == "hashed_$password"
            val user =
                sampleUser(
                    isVerified = true,
                    passwordHash = "hashed_Test123Pass!",
                    roles = listOf(Role(RoleId("role-001"), "STAFF")),
                )
            coEvery { userRepository.findByEmail("juan@fleet.ph") } returns user
            every {
                jwtService.generateToken(
                    id = "user-001",
                    email = "juan@fleet.ph",
                    roles = listOf("STAFF"),
                )
            } returns "jwt-token-abc"
            val request = LoginRequest(email = "juan@fleet.ph", passwordRaw = "Test123Pass!")

            // Act
            val (returnedUser, token) = useCase.execute(request)

            // Assert
            assertThat(returnedUser.id).isEqualTo(UserId("user-001"))
            assertThat(token).isEqualTo("jwt-token-abc")
        }

    private fun sampleUser(
        isVerified: Boolean,
        passwordHash: String = "hashed_pass",
        roles: List<Role> = emptyList(),
    ) = User(
        id = UserId("user-001"),
        email = "juan@fleet.ph",
        firstName = "Juan",
        lastName = "dela Cruz",
        passwordHash = passwordHash,
        phone = "+63912345678",
        isVerified = isVerified,
        roles = roles,
    )
}
