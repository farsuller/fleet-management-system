package com.solodev.fleet.modules.domain.ports

import com.solodev.fleet.modules.domain.models.*
import java.time.Instant

/** Repository interface for Rental persistence. */
interface RentalRepository {
    /** Find a rental by its unique identifier. */
    suspend fun findById(id: RentalId): Rental?

    /** Find a rental by its rental number. */
    suspend fun findByRentalNumber(rentalNumber: String): Rental?

    /** Save a new rental or update an existing one. */
    suspend fun save(rental: Rental): Rental

    /** Find all rentals for a specific customer. */
    suspend fun findByCustomerId(customerId: CustomerId): List<Rental>

    /** Find all rentals for a specific vehicle. */
    suspend fun findByVehicleId(vehicleId: VehicleId): List<Rental>

    /**
     * Find active or reserved rentals for a vehicle in a date range. Used to check for
     * conflicts/double-booking.
     */
    suspend fun findConflictingRentals(
            vehicleId: VehicleId,
            startDate: Instant,
            endDate: Instant
    ): List<Rental>

    /** Find all rentals with a specific status. */
    suspend fun findByStatus(status: RentalStatus): List<Rental>

    /** Delete a rental by ID. */
    suspend fun deleteById(id: RentalId): Boolean

    /** Find all rentals. */
    suspend fun findAll(): List<Rental>
}
