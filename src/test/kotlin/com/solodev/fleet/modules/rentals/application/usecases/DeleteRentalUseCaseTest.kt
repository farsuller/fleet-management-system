package com.solodev.fleet.modules.rentals.application.usecases

import com.solodev.fleet.modules.rentals.domain.model.RentalId
import com.solodev.fleet.modules.rentals.domain.repository.RentalRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DeleteRentalUseCaseTest {

    private val repository = mockk<RentalRepository>()
    private val useCase = DeleteRentalUseCase(repository)

    @Test
    fun shouldReturnTrue_WhenRentalDeletedSuccessfully() = runBlocking {
        // Arrange
        val rentalId = "rent-123"
        coEvery { repository.deleteById(RentalId(rentalId)) } returns true

        // Act
        val result = useCase.execute(rentalId)

        // Assert
        assertThat(result).isTrue()
    }

    @Test
    fun shouldReturnFalse_WhenRentalNotFound() = runBlocking {
        // Arrange
        val rentalId = "rent-non-existent"
        coEvery { repository.deleteById(RentalId(rentalId)) } returns false

        // Act
        val result = useCase.execute(rentalId)

        // Assert
        assertThat(result).isFalse()
    }
}
