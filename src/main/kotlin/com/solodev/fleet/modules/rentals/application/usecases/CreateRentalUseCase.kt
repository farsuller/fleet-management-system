package com.solodev.fleet.modules.rentals.application.usecases

import com.solodev.fleet.modules.domain.models.CustomerId
import com.solodev.fleet.modules.domain.models.Rental
import com.solodev.fleet.modules.domain.models.RentalId
import com.solodev.fleet.modules.domain.models.RentalStatus
import com.solodev.fleet.modules.domain.models.Vehicle
import com.solodev.fleet.modules.domain.models.VehicleId
import com.solodev.fleet.modules.domain.models.VehicleState
import com.solodev.fleet.modules.domain.ports.RentalRepository
import com.solodev.fleet.modules.domain.ports.VehicleRepository
import com.solodev.fleet.modules.rentals.application.dto.RentalRequest
import java.time.Instant
import java.util.*

class CreateRentalUseCase(
        private val rentalRepository: RentalRepository,
        private val vehicleRepository: VehicleRepository
) {
    suspend fun execute(request: RentalRequest): Rental {
        val vehicleId = VehicleId(request.vehicleId)
        val customerId = CustomerId(request.customerId)
        val startDate = Instant.parse(request.startDate)
        val endDate = Instant.parse(request.endDate)

        // Validate vehicle exists and is available
        val vehicle =
                vehicleRepository.findById(vehicleId)
                        ?: throw IllegalArgumentException("Vehicle not found")

        require(vehicle.state == VehicleState.AVAILABLE) { "Vehicle is not available for rental" }

        // Check for conflicts
        val conflicts = rentalRepository.findConflictingRentals(vehicleId, startDate, endDate)
        require(conflicts.isEmpty()) { "Vehicle is already rented during this period" }

        val rental =
                Rental(
                        id = RentalId(UUID.randomUUID().toString()),
                        rentalNumber = "RNT-${System.currentTimeMillis()}",
                        vehicleId = vehicleId,
                        customerId = customerId,
                        status = RentalStatus.RESERVED,
                        startDate = startDate,
                        endDate = endDate,
                        totalAmountCents = calculateCost(vehicle, startDate, endDate),
                        dailyRateCents = dailyRate(vehicle)
                )

        return rentalRepository.save(rental)
    }

    private fun calculateCost(vehicle: Vehicle, start: Instant, end: Instant): Int {
        val days = java.time.Duration.between(start, end).toDays().toInt()
        return days * dailyRate(vehicle)
    }

    private fun dailyRate(vehicle: Vehicle): Int = vehicle.dailyRateCents ?: 5000
}
