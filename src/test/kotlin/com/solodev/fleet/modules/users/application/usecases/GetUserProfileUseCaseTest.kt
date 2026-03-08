package com.solodev.fleet.modules.users.application.usecases

import com.solodev.fleet.modules.users.domain.model.User
import com.solodev.fleet.modules.users.domain.model.UserId
import com.solodev.fleet.modules.users.domain.repository.UserRepository
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class GetUserProfileUseCaseTest {

    private val repository = mockk<UserRepository>()
    private val useCase = GetUserProfileUseCase(repository)

    private val user = User(
        id = UserId("user-1"),
        email = "juan@fleet.ph",
        passwordHash = "hashed-password",
        firstName = "Juan",
        lastName = "dela Cruz",
        isVerified = true
    )

    @Test
    fun shouldReturnUser_WhenUserExists() = runBlocking {
        // Arrange
        coEvery { repository.findById(UserId("user-1")) } returns user

        // Act
        val result = useCase.execute("user-1")

        // Assert
        assertThat(result).isNotNull()
        assertThat(result!!.id.value).isEqualTo("user-1")
        assertThat(result.email).isEqualTo("juan@fleet.ph")
        assertThat(result.firstName).isEqualTo("Juan")
    }

    @Test
    fun shouldReturnNull_WhenUserNotFound() = runBlocking {
        // Arrange
        coEvery { repository.findById(UserId("unknown")) } returns null

        // Act
        val result = useCase.execute("unknown")

        // Assert
        assertThat(result).isNull()
    }
}
