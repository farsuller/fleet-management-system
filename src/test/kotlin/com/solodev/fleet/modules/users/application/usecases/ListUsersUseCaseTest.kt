package com.solodev.fleet.modules.users.application.usecases

import com.solodev.fleet.modules.users.domain.model.User
import com.solodev.fleet.modules.users.domain.model.UserId
import com.solodev.fleet.modules.users.domain.repository.UserRepository
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ListUsersUseCaseTest {

    private val repository = mockk<UserRepository>()
    private val useCase = ListUsersUseCase(repository)

    @Test
    fun shouldReturnAllUsers_WhenUsersExist() = runBlocking {
        // Arrange
        val users = listOf(
            User(UserId("u1"), "alice@fleet.ph", "hash1", "Alice", "Smith"),
            User(UserId("u2"), "bob@fleet.ph", "hash2", "Bob", "Jones")
        )
        coEvery { repository.findAll() } returns users

        // Act
        val result = useCase.execute()

        // Assert
        assertThat(result).hasSize(2)
        assertThat(result.map { it.email }).containsExactly("alice@fleet.ph", "bob@fleet.ph")
    }

    @Test
    fun shouldReturnEmptyList_WhenNoUsersExist() = runBlocking {
        // Arrange
        coEvery { repository.findAll() } returns emptyList()

        // Act
        val result = useCase.execute()

        // Assert
        assertThat(result).isEmpty()
    }
}
