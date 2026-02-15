package com.solodev.fleet.modules.rentals.application.usecases

import com.solodev.fleet.modules.rentals.application.dto.RentalRequest
import com.solodev.fleet.modules.rentals.domain.model.CustomerId
import com.solodev.fleet.modules.rentals.domain.model.Rental
import com.solodev.fleet.modules.rentals.domain.model.RentalId
import com.solodev.fleet.modules.rentals.domain.model.RentalStatus
import com.solodev.fleet.modules.rentals.domain.repository.RentalRepository
import com.solodev.fleet.modules.vehicles.domain.model.Vehicle
import com.solodev.fleet.modules.vehicles.domain.model.VehicleId
import com.solodev.fleet.modules.vehicles.domain.model.VehicleState
import com.solodev.fleet.modules.vehicles.domain.repository.VehicleRepository
import com.solodev.fleet.shared.helpers.dbQuery
import java.time.Instant
import java.util.*
import org.jetbrains.exposed.sql.transactions.TransactionManager

class CreateRentalUseCase(
        private val rentalRepository: RentalRepository,
        private val vehicleRepository: VehicleRepository
) {

    suspend fun execute(request: RentalRequest): Rental = dbQuery {
        val vehicleId = VehicleId(request.vehicleId)
        val customerId = CustomerId(request.customerId)
        val startDate = Instant.parse(request.startDate)
        val endDate = Instant.parse(request.endDate)

        // 1. ACQUIRE PESSIMISTIC LOCK - Prevents concurrent reservations of the same
        // vehicle
        val lockId = UUID.fromString(vehicleId.value).hashCode().toLong()
        TransactionManager.current().exec("SELECT pg_advisory_xact_lock($lockId)")

        // 2. VALIDATE - Now safe from race conditions
        val vehicle =
                vehicleRepository.findById(vehicleId)
                        ?: throw IllegalArgumentException("Vehicle not found")

        require(vehicle.state == VehicleState.AVAILABLE) { "Vehicle is not available for rental" }

        // 3. CHECK FOR CONFLICTS
        val conflicts = rentalRepository.findConflictingRentals(vehicleId, startDate, endDate)
        require(conflicts.isEmpty()) { "Vehicle is already rented during this period" }

        // 4. CREATE AND PERSIST RENTAL
        val rental =
                Rental(
                        id = RentalId(UUID.randomUUID().toString()),
                        rentalNumber = "RNT-${System.currentTimeMillis()}",
                        vehicleId = vehicleId,
                        customerId = customerId,
                        status = RentalStatus.RESERVED,
                        startDate = startDate,
                        endDate = endDate,
                        totalAmount = calculateCost(vehicle, startDate, endDate),
                        dailyRateAmount = dailyRate(vehicle)
                )

        rentalRepository.save(rental)
    }

    private fun calculateCost(vehicle: Vehicle, start: Instant, end: Instant): Int {
        val days = java.time.Duration.between(start, end).toDays().toInt()
        return days * dailyRate(vehicle)
    }

    private fun dailyRate(vehicle: Vehicle): Int = vehicle.dailyRateAmount ?: 5000
}
