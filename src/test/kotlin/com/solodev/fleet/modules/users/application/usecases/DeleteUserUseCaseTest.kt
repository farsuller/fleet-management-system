package com.solodev.fleet.modules.users.application.usecases

import com.solodev.fleet.modules.users.domain.model.UserId
import com.solodev.fleet.modules.users.domain.repository.UserRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DeleteUserUseCaseTest {
    private val repository = mockk<UserRepository>()
    private val useCase = DeleteUserUseCase(repository)

    @Test
    fun shouldReturnTrue_WhenUserExistsAndIsDeleted() =
        runBlocking {
            // Arrange
            coEvery { repository.deleteById(UserId("user-001")) } returns true

            // Act
            val result = useCase.execute("user-001")

            // Assert
            assertThat(result).isTrue()
        }

    @Test
    fun shouldReturnFalse_WhenUserDoesNotExist() =
        runBlocking {
            // Arrange
            coEvery { repository.deleteById(UserId("unknown-user")) } returns false

            // Act
            val result = useCase.execute("unknown-user")

            // Assert
            assertThat(result).isFalse()
        }
}
