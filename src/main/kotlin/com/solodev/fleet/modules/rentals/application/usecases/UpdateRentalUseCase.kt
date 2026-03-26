package com.solodev.fleet.modules.rentals.application.usecases

import com.solodev.fleet.modules.rentals.application.dto.UpdateRentalRequest
import com.solodev.fleet.modules.rentals.domain.model.CustomerId
import com.solodev.fleet.modules.rentals.domain.model.Rental
import com.solodev.fleet.modules.rentals.domain.model.RentalId
import com.solodev.fleet.modules.rentals.domain.repository.RentalRepository
import com.solodev.fleet.modules.rentals.domain.repository.RentalWithDetails
import com.solodev.fleet.modules.vehicles.domain.model.Vehicle
import com.solodev.fleet.modules.vehicles.domain.model.VehicleId
import com.solodev.fleet.modules.vehicles.domain.repository.VehicleRepository
import com.solodev.fleet.shared.helpers.dbQuery
import java.time.Instant
import java.util.*
import org.jetbrains.exposed.sql.transactions.TransactionManager

class UpdateRentalUseCase(
    private val rentalRepository: RentalRepository,
    private val vehicleRepository: VehicleRepository
) {
    suspend fun execute(id: String, request: UpdateRentalRequest): RentalWithDetails? = dbQuery {
        val rentalId = RentalId(id)
        val existing = rentalRepository.findById(rentalId) ?: return@dbQuery null

        // 1. Determine new values
        val newStartDate = request.startDate?.let { Instant.parse(it) } ?: existing.startDate
        val newEndDate = request.endDate?.let { Instant.parse(it) } ?: existing.endDate
        val newVehicleId = request.vehicleId?.let { VehicleId(it) } ?: existing.vehicleId
        val newCustomerId = request.customerId?.let { CustomerId(it) } ?: existing.customerId
        val newDailyRate = request.dailyRateAmount?.toInt() ?: existing.dailyRateAmount

        // 2. ACQUIRE PESSIMISTIC LOCK on the vehicle (if vehicle is being changed or dates are changed)
        val lockId = UUID.fromString(newVehicleId.value).hashCode().toLong()
        TransactionManager.current().exec("SELECT pg_advisory_xact_lock($lockId)")

        // 3. VALIDATE VEHICLE if changed
        val vehicle = vehicleRepository.findById(newVehicleId)
            ?: throw IllegalArgumentException("Vehicle not found")

        // 4. CHECK FOR CONFLICTS (excluding current rental)
        val conflicts = rentalRepository.findConflictingRentals(newVehicleId, newStartDate, newEndDate)
            .filter { it.id != rentalId }
        
        require(conflicts.isEmpty()) { "Vehicle is already rented during this period" }

        // 5. RECALCULATE COST
        val days = java.time.Duration.between(newStartDate, newEndDate).toDays().toInt().coerceAtLeast(1)
        val newTotalAmount = days * newDailyRate

        // 6. UPDATE AND PERSIST
        val updated = existing.copy(
            startDate = newStartDate,
            endDate = newEndDate,
            vehicleId = newVehicleId,
            customerId = newCustomerId,
            dailyRateAmount = newDailyRate,
            totalAmount = newTotalAmount
        )

        rentalRepository.save(updated)
        rentalRepository.findByIdWithDetails(rentalId)
    }
}
