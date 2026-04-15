package com.solodev.fleet.modules.rentals.application.usecases

import com.solodev.fleet.modules.rentals.domain.model.CustomerId
import com.solodev.fleet.modules.rentals.domain.model.Rental
import com.solodev.fleet.modules.rentals.domain.model.RentalId
import com.solodev.fleet.modules.rentals.domain.model.RentalStatus
import com.solodev.fleet.modules.rentals.domain.repository.RentalRepository
import com.solodev.fleet.modules.rentals.domain.repository.RentalWithDetails
import com.solodev.fleet.modules.vehicles.domain.model.VehicleId
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class ListRentalsUseCaseTest {
    private val repository = mockk<RentalRepository>()
    private val useCase = ListRentalsUseCase(repository)

    private val start = Instant.parse("2026-03-10T00:00:00Z")
    private val end = Instant.parse("2026-03-17T00:00:00Z")

    @Test
    fun shouldReturnPagedRentals_WhenNoFiltersApplied() =
        runBlocking {
            // Arrange
            val rentals = listOf(rentalWithDetails("rental-001"), rentalWithDetails("rental-002"))
            coEvery { repository.findAllPaged(1, 10, null, null, null) } returns rentals
            coEvery { repository.count(null, null, null) } returns 2L

            // Act
            val (results, total) = useCase.execute()

            // Assert
            assertThat(results).hasSize(2)
            assertThat(total).isEqualTo(2L)
        }

    @Test
    fun shouldFilterByStatus_WhenStatusProvided() =
        runBlocking {
            // Arrange
            val activeRental = listOf(rentalWithDetails("rental-003", RentalStatus.ACTIVE))
            coEvery { repository.findAllPaged(1, 10, RentalStatus.ACTIVE, null, null) } returns activeRental
            coEvery { repository.count(RentalStatus.ACTIVE, null, null) } returns 1L

            // Act
            val (results, total) = useCase.execute(status = RentalStatus.ACTIVE)

            // Assert
            assertThat(results).hasSize(1)
            assertThat(total).isEqualTo(1L)
        }

    @Test
    fun shouldFilterByVehicleId_WhenVehicleIdProvided() =
        runBlocking {
            // Arrange
            val vehicleId = "veh-001"
            val vehicleRentals = listOf(rentalWithDetails("rental-004"))
            coEvery { repository.findAllPaged(1, 10, null, VehicleId(vehicleId), null) } returns vehicleRentals
            coEvery { repository.count(null, VehicleId(vehicleId), null) } returns 1L

            // Act
            val (results, total) = useCase.execute(vehicleId = vehicleId)

            // Assert
            assertThat(results).hasSize(1)
            assertThat(total).isEqualTo(1L)
        }

    @Test
    fun shouldFilterByCustomerId_WhenCustomerIdProvided() =
        runBlocking {
            // Arrange
            val customerId = "cust-001"
            val customerRentals = listOf(rentalWithDetails("rental-005"), rentalWithDetails("rental-006"))
            coEvery { repository.findAllPaged(1, 10, null, null, CustomerId(customerId)) } returns customerRentals
            coEvery { repository.count(null, null, CustomerId(customerId)) } returns 2L

            // Act
            val (results, total) = useCase.execute(customerId = customerId)

            // Assert
            assertThat(results).hasSize(2)
            assertThat(total).isEqualTo(2L)
        }

    @Test
    fun shouldReturnEmptyList_WhenNoRentalsMatch() =
        runBlocking {
            // Arrange
            coEvery { repository.findAllPaged(1, 10, RentalStatus.COMPLETED, null, null) } returns emptyList()
            coEvery { repository.count(RentalStatus.COMPLETED, null, null) } returns 0L

            // Act
            val (results, total) = useCase.execute(status = RentalStatus.COMPLETED)

            // Assert
            assertThat(results).isEmpty()
            assertThat(total).isEqualTo(0L)
        }

    @Test
    fun shouldRespectPageAndLimit_WhenProvided() =
        runBlocking {
            // Arrange
            val page2 = listOf(rentalWithDetails("rental-011"))
            coEvery { repository.findAllPaged(2, 5, null, null, null) } returns page2
            coEvery { repository.count(null, null, null) } returns 6L

            // Act
            val (results, total) = useCase.execute(page = 2, limit = 5)

            // Assert
            assertThat(results).hasSize(1)
            assertThat(total).isEqualTo(6L)
        }

    private fun rentalWithDetails(
        id: String,
        status: RentalStatus = RentalStatus.RESERVED,
    ) = RentalWithDetails(
        rental =
            Rental(
                id = RentalId(id),
                rentalNumber = "RNT-$id",
                vehicleId = VehicleId("veh-001"),
                customerId = CustomerId("cust-001"),
                status = status,
                startDate = start,
                endDate = end,
                dailyRateAmount = 1000,
                totalAmount = 7000,
            ),
        vehiclePlateNumber = "ABC-123",
        vehicleMake = "Toyota",
        vehicleModel = "HiAce",
        customerName = "Juan dela Cruz",
    )
}
