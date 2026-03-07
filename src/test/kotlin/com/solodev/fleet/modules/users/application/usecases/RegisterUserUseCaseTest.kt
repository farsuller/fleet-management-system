package com.solodev.fleet.modules.users.application.usecases

import com.solodev.fleet.modules.users.application.dto.UserRegistrationRequest
import com.solodev.fleet.modules.users.domain.model.*
import com.solodev.fleet.modules.users.domain.repository.UserRepository
import com.solodev.fleet.modules.users.domain.repository.VerificationTokenRepository
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.*

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
    fun `registers new user successfully`() = runBlocking {
        coEvery { userRepository.findByEmail(any()) } returns null
        coEvery { userRepository.findRoleByName("CUSTOMER") } returns customerRole
        coEvery { userRepository.save(any()) } returnsArgument 0
        coEvery { tokenRepository.save(any()) } returnsArgument 0

        val result = useCase.execute(validRequest)

        assertEquals("juan@fleet.ph", result.email)
        assertFalse(result.isVerified)
        coVerify { userRepository.save(any()) }
    }

    @Test
    fun `throws when email is already registered`(): Unit = runBlocking {
        coEvery { userRepository.findByEmail("juan@fleet.ph") } returns mockk()

        assertFailsWith<IllegalStateException> {
            useCase.execute(validRequest)
        }
    }

    @Test
    fun `new user is not verified by default`() = runBlocking {
        coEvery { userRepository.findByEmail(any()) } returns null
        coEvery { userRepository.findRoleByName("CUSTOMER") } returns customerRole
        coEvery { userRepository.save(any()) } returnsArgument 0
        coEvery { tokenRepository.save(any()) } returnsArgument 0

        val result = useCase.execute(validRequest)

        assertFalse(result.isVerified)
    }
}
