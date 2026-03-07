package com.solodev.fleet.modules.users.application.usecases

import com.solodev.fleet.modules.users.domain.model.*
import com.solodev.fleet.modules.users.domain.repository.UserRepository
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.*

class AssignRoleUseCaseTest {

    private val userRepository = mockk<UserRepository>()
    private val useCase = AssignRoleUseCase(userRepository)

    @Test
    fun `assigns role to user`() = runBlocking {
        val user = sampleUser(roles = emptyList())
        val adminRole = Role(id = RoleId("role-admin"), name = "ADMIN")

        coEvery { userRepository.findById(any()) } returns user
        coEvery { userRepository.findRoleByName("ADMIN") } returns adminRole
        coEvery { userRepository.save(any()) } returnsArgument 0

        val result = useCase.execute("user-001", "ADMIN")

        assertNotNull(result)
        assertEquals("ADMIN", result.roles.first().name)
        coVerify { userRepository.save(any()) }
    }

    @Test
    fun `returns null when user not found`() = runBlocking {
        coEvery { userRepository.findById(any()) } returns null

        val result = useCase.execute("unknown", "ADMIN")

        assertNull(result)
    }

    @Test
    fun `throws when role not found`(): Unit = runBlocking {
        val user = sampleUser(roles = emptyList())
        coEvery { userRepository.findById(any()) } returns user
        coEvery { userRepository.findRoleByName(any()) } returns null

        assertFailsWith<IllegalArgumentException> {
            useCase.execute("user-001", "UNKNOWN_ROLE")
        }
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
