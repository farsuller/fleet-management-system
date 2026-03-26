package com.solodev.fleet.modules.rentals.application.usecases

import com.solodev.fleet.modules.rentals.domain.model.CustomerId
import com.solodev.fleet.modules.rentals.domain.model.Rental
import com.solodev.fleet.modules.rentals.domain.model.RentalId
import com.solodev.fleet.modules.rentals.domain.model.RentalStatus
import com.solodev.fleet.modules.rentals.domain.repository.RentalRepository
import com.solodev.fleet.modules.rentals.domain.repository.RentalWithDetails
import com.solodev.fleet.modules.vehicles.domain.model.VehicleId
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class GetRentalUseCaseTest {

    private val repository = mockk<RentalRepository>()
    private val useCase = GetRentalUseCase(repository)

    private val rental = Rental(
        id = RentalId("rental-1"),
        rentalNumber = "RNT-001",
        customerId = CustomerId("cust-1"),
        vehicleId = VehicleId("vehicle-1"),
        status = RentalStatus.RESERVED,
        startDate = Instant.parse("2026-03-10T00:00:00Z"),
        endDate = Instant.parse("2026-03-20T00:00:00Z"),
        dailyRateAmount = 150000,
        totalAmount = 1500000
    )

    private val rentalWithDetails = RentalWithDetails(
        rental = rental,
        vehiclePlateNumber = "ABC-1234",
        vehicleMake = "Toyota",
        vehicleModel = "Corolla",
        customerName = "John Doe"
    )

    @Test
    fun shouldReturnRental_WhenRentalExists() = runBlocking {
        // Arrange
        coEvery { repository.findByIdWithDetails(RentalId("rental-1")) } returns rentalWithDetails

        // Act
        val result = useCase.execute("rental-1")

        // Assert
        assertThat(result).isNotNull()
        assertThat(result!!.rental.id.value).isEqualTo("rental-1")
        assertThat(result.rental.rentalNumber).isEqualTo("RNT-001")
        assertThat(result.rental.status).isEqualTo(RentalStatus.RESERVED)
        assertThat(result.rental.vehicleId.value).isEqualTo("vehicle-1")
    }

    @Test
    fun shouldReturnNull_WhenRentalNotFound() = runBlocking {
        // Arrange
        coEvery { repository.findByIdWithDetails(RentalId("unknown")) } returns null

        // Act
        val result = useCase.execute("unknown")

        // Assert
        assertThat(result).isNull()
    }
}
