package com.solodev.fleet.modules.rentals.application.usecases

import com.solodev.fleet.modules.accounts.application.usecases.IssueInvoiceUseCase
import com.solodev.fleet.modules.accounts.domain.model.*
import com.solodev.fleet.modules.accounts.domain.repository.InvoiceRepository
import com.solodev.fleet.modules.rentals.domain.model.*
import com.solodev.fleet.modules.rentals.domain.repository.RentalRepository
import com.solodev.fleet.modules.vehicles.domain.model.*
import com.solodev.fleet.modules.vehicles.domain.repository.VehicleRepository
import io.mockk.*
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class CompleteRentalUseCaseTest {

    private val rentalRepository = mockk<RentalRepository>()
    private val vehicleRepository = mockk<VehicleRepository>()
    private val issueInvoiceUseCase = mockk<IssueInvoiceUseCase>()
    private val invoiceRepository = mockk<InvoiceRepository>()
    private val useCase = CompleteRentalUseCase(
        rentalRepository,
        vehicleRepository,
        issueInvoiceUseCase,
        invoiceRepository
    )

    // Use valid UUIDs — CompleteRentalUseCase calls UUID.fromString(id) internally
    private val rentalId = "00000000-0000-0000-0000-000000000001"
    private val vehicleId = "00000000-0000-0000-0000-000000000002"
    private val customerId = "00000000-0000-0000-0000-000000000003"

    @Test
    fun shouldCompleteRentalAndReturnVehicleToAvailable_WhenRentalIsActive() = runBlocking {
        // Arrange
        val rental = sampleRental(status = RentalStatus.ACTIVE, startOdometerKm = 5000)
        val vehicle = sampleVehicle(mileageKm = 5000)
        val savedVehicle = slot<Vehicle>()
        coEvery { rentalRepository.findById(RentalId(rentalId)) } returns rental
        coEvery { vehicleRepository.findById(VehicleId(vehicleId)) } returns vehicle
        coEvery { vehicleRepository.save(capture(savedVehicle)) } returnsArgument 0
        coEvery { rentalRepository.save(any()) } returnsArgument 0
        coEvery { invoiceRepository.findByRentalId(any()) } returns null
        coEvery { issueInvoiceUseCase.execute(any()) } returnsArgument 0

        // Act
        val result = useCase.execute(rentalId, finalMileage = 5150)

        // Assert
        assertThat(result.status).isEqualTo(RentalStatus.COMPLETED)
        assertThat(result.endOdometerKm).isEqualTo(5150)
        assertThat(savedVehicle.captured.state).isEqualTo(VehicleState.AVAILABLE)
        coVerify(exactly = 1) { issueInvoiceUseCase.execute(any()) }
    }

    @Test
    fun shouldAutoGenerateInvoice_WhenRentalCompleted() = runBlocking {
        // Arrange
        val rental = sampleRental(status = RentalStatus.ACTIVE)
        val vehicle = sampleVehicle()
        coEvery { rentalRepository.findById(RentalId(rentalId)) } returns rental
        coEvery { vehicleRepository.findById(VehicleId(vehicleId)) } returns vehicle
        coEvery { vehicleRepository.save(any()) } returnsArgument 0
        coEvery { rentalRepository.save(any()) } returnsArgument 0
        coEvery { invoiceRepository.findByRentalId(any()) } returns null
        coEvery { issueInvoiceUseCase.execute(any()) } returnsArgument 0

        // Act
        useCase.execute(rentalId, finalMileage = 5100)

        // Assert
        val invoiceSlot = slot<com.solodev.fleet.modules.accounts.application.dto.InvoiceRequest>()
        coVerify { issueInvoiceUseCase.execute(capture(invoiceSlot)) }
        assertThat(invoiceSlot.captured.customerId).isEqualTo(customerId)
        assertThat(invoiceSlot.captured.rentalId).isEqualTo(rentalId)
        assertThat(invoiceSlot.captured.subtotal).isEqualTo(7000)
    }

    @Test
    fun shouldNotDuplicateInvoice_WhenAlreadyExists() = runBlocking {
        // Arrange: an invoice already exists for this rental
        val rental = sampleRental(status = RentalStatus.ACTIVE)
        val vehicle = sampleVehicle()
        val existingInvoice = sampleInvoice()
        coEvery { rentalRepository.findById(RentalId(rentalId)) } returns rental
        coEvery { vehicleRepository.findById(VehicleId(vehicleId)) } returns vehicle
        coEvery { vehicleRepository.save(any()) } returnsArgument 0
        coEvery { rentalRepository.save(any()) } returnsArgument 0
        coEvery { invoiceRepository.findByRentalId(any()) } returns existingInvoice

        // Act
        useCase.execute(rentalId, finalMileage = 5100)

        // Assert: issueInvoiceUseCase must NOT be called again
        coVerify(exactly = 0) { issueInvoiceUseCase.execute(any()) }
    }

    @Test
    fun shouldThrowIllegalArgument_WhenRentalIsNotActive() {
        // Arrange
        val rental = sampleRental(status = RentalStatus.RESERVED)
        coEvery { rentalRepository.findById(RentalId(rentalId)) } returns rental

        // Act / Assert
        assertThatThrownBy { runBlocking { useCase.execute(rentalId, finalMileage = 5150) } }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun shouldThrowIllegalArgument_WhenFinalMileageLessThanStart() {
        // Arrange
        val rental = sampleRental(status = RentalStatus.ACTIVE, startOdometerKm = 5000)
        val vehicle = sampleVehicle(mileageKm = 5000)
        coEvery { rentalRepository.findById(RentalId(rentalId)) } returns rental
        coEvery { vehicleRepository.findById(VehicleId(vehicleId)) } returns vehicle

        // Act / Assert
        assertThatThrownBy { runBlocking { useCase.execute(rentalId, finalMileage = 4999) } }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    // ---- helpers ----

    private fun sampleRental(status: RentalStatus, startOdometerKm: Int? = null) = Rental(
        id = RentalId(rentalId),
        rentalNumber = "RNT-001",
        customerId = CustomerId(customerId),
        vehicleId = VehicleId(vehicleId),
        status = status,
        startDate = Instant.now(),
        endDate = Instant.now().plus(7, ChronoUnit.DAYS),
        dailyRateAmount = 1000,
        totalAmount = 7000,
        startOdometerKm = startOdometerKm
    )

    private fun sampleVehicle(mileageKm: Int = 0) = Vehicle(
        id = VehicleId(vehicleId),
        vin = "1HGBH41JXMN109186",
        licensePlate = "ABC-1234",
        make = "Toyota",
        model = "Corolla",
        year = 2023,
        state = VehicleState.RENTED,
        mileageKm = mileageKm
    )

    private fun sampleInvoice() = Invoice(
        id = UUID.randomUUID(),
        invoiceNumber = "INV-001",
        customerId = CustomerId(customerId),
        rentalId = com.solodev.fleet.modules.rentals.domain.model.RentalId(rentalId),
        status = InvoiceStatus.ISSUED,
        subtotal = 7000,
        tax = 0,
        paidAmount = 0,
        issueDate = Instant.now(),
        dueDate = Instant.now().plus(30, ChronoUnit.DAYS)
    )
}
