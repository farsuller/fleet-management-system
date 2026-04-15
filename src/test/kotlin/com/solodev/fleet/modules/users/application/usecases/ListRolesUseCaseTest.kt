package com.solodev.fleet.modules.users.application.usecases

import com.solodev.fleet.modules.users.domain.model.Role
import com.solodev.fleet.modules.users.domain.model.RoleId
import com.solodev.fleet.modules.users.domain.repository.UserRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ListRolesUseCaseTest {
    private val repository = mockk<UserRepository>()
    private val useCase = ListRolesUseCase(repository)

    @Test
    fun shouldReturnAllRoles_WhenRolesExist() =
        runBlocking {
            // Arrange
            val roles =
                listOf(
                    Role(RoleId("role-001"), "ADMIN", "System administrator"),
                    Role(RoleId("role-002"), "STAFF", "Regular staff"),
                    Role(RoleId("role-003"), "DRIVER", "Vehicle driver"),
                )
            coEvery { repository.findAllRoles() } returns roles

            // Act
            val result = useCase.execute()

            // Assert
            assertThat(result).hasSize(3)
            assertThat(result.map { it.name }).containsExactly("ADMIN", "STAFF", "DRIVER")
        }

    @Test
    fun shouldReturnEmptyList_WhenNoRolesExist() =
        runBlocking {
            // Arrange
            coEvery { repository.findAllRoles() } returns emptyList()

            // Act
            val result = useCase.execute()

            // Assert
            assertThat(result).isEmpty()
        }
}
