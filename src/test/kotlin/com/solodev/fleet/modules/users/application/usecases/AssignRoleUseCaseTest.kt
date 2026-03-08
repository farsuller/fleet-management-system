package com.solodev.fleet.modules.users.application.usecases

import com.solodev.fleet.modules.users.domain.model.*
import com.solodev.fleet.modules.users.domain.repository.UserRepository
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class AssignRoleUseCaseTest {

    private val userRepository = mockk<UserRepository>()
    private val useCase = AssignRoleUseCase(userRepository)

    @Test
    fun shouldAssignRole_WhenUserAndRoleExist() = runBlocking {
        // Arrange
        val user = sampleUser(roles = emptyList())
        val adminRole = Role(id = RoleId("role-admin"), name = "ADMIN")
        val savedUser = slot<User>()
        coEvery { userRepository.findById(UserId("user-001")) } returns user
        coEvery { userRepository.findRoleByName("ADMIN") } returns adminRole
        coEvery { userRepository.save(capture(savedUser)) } returnsArgument 0

        // Act
        val result = useCase.execute("user-001", "ADMIN")

        // Assert
        assertThat(result).isNotNull()
        assertThat(result!!.roles.first().name).isEqualTo("ADMIN")
        assertThat(savedUser.captured.roles).anyMatch { it.name == "ADMIN" }
    }

    @Test
    fun shouldReturnNull_WhenUserNotFound() = runBlocking {
        // Arrange
        coEvery { userRepository.findById(UserId("unknown")) } returns null

        // Act
        val result = useCase.execute("unknown", "ADMIN")

        // Assert
        assertThat(result).isNull()
    }

    @Test
    fun shouldThrowIllegalArgument_WhenRoleNotFound() {
        // Arrange
        val user = sampleUser(roles = emptyList())
        coEvery { userRepository.findById(UserId("user-001")) } returns user
        coEvery { userRepository.findRoleByName("UNKNOWN_ROLE") } returns null

        // Act & Assert
        assertThatThrownBy { runBlocking { useCase.execute("user-001", "UNKNOWN_ROLE") } }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    private fun sampleUser(roles: List<Role>) = User(
        id = UserId("user-001"),
        email = "juan@fleet.ph",
        firstName = "Juan",
        lastName = "dela Cruz",
        passwordHash = "hashed_pw",
        phone = "+63912345678",
        isVerified = true,
        roles = roles
    )
}
