package com.solodev.fleet.modules.rentals.application.usecases

import com.solodev.fleet.modules.accounts.application.dto.InvoiceRequest
import com.solodev.fleet.modules.accounts.application.usecases.IssueInvoiceUseCase
import com.solodev.fleet.modules.accounts.domain.repository.InvoiceRepository
import com.solodev.fleet.modules.rentals.domain.model.Rental
import com.solodev.fleet.modules.rentals.domain.model.RentalId
import com.solodev.fleet.modules.rentals.domain.model.RentalStatus
import com.solodev.fleet.modules.rentals.domain.repository.RentalRepository
import com.solodev.fleet.modules.vehicles.domain.model.VehicleState
import com.solodev.fleet.modules.vehicles.domain.repository.VehicleRepository
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

class CompleteRentalUseCase(
        private val rentalRepository: RentalRepository,
        private val vehicleRepository: VehicleRepository,
        private val issueInvoiceUseCase: IssueInvoiceUseCase,
        private val invoiceRepository: InvoiceRepository
) {
    suspend fun execute(id: String, finalMileage: Int? = null): Rental {
        val rental =
                rentalRepository.findById(RentalId(id))
                        ?: throw IllegalArgumentException("Rental not found")

        require(rental.status == RentalStatus.ACTIVE) { "Can only complete active rentals" }

        val vehicle =
                vehicleRepository.findById(rental.vehicleId)
                        ?: throw IllegalStateException("Vehicle not found")

        val actualEndOdo = finalMileage ?: vehicle.mileageKm

        rental.startOdometerKm?.let { start ->
            require(actualEndOdo >= start) {
                "End mileage ($actualEndOdo) cannot be less than start mileage ($start)"
            }
        }

        val completed = rental.complete(actualEnd = Instant.now(), endOdo = actualEndOdo)

        val updatedVehicle = vehicle.copy(state = VehicleState.AVAILABLE, mileageKm = actualEndOdo)
        vehicleRepository.save(updatedVehicle)

        val savedRental = rentalRepository.save(completed)

        // Auto-generate invoice if one does not already exist for this rental (idempotency guard)
        val rentalUUID = UUID.fromString(id)
        if (invoiceRepository.findByRentalId(rentalUUID) == null) {
            val invoiceRequest = InvoiceRequest(
                customerId = savedRental.customerId.value,
                rentalId   = id,
                subtotal   = savedRental.totalAmount,
                tax        = 0,
                dueDate    = Instant.now().plus(30, ChronoUnit.DAYS).toString()
            )
            issueInvoiceUseCase.execute(invoiceRequest)
        }

        return savedRental
    }
}
