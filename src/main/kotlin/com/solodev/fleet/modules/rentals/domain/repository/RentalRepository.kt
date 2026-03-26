package com.solodev.fleet.modules.rentals.domain.repository

import com.solodev.fleet.modules.rentals.domain.model.CustomerId
import com.solodev.fleet.modules.rentals.domain.model.Rental
import com.solodev.fleet.modules.rentals.domain.model.RentalId
import com.solodev.fleet.modules.rentals.domain.model.RentalStatus
import com.solodev.fleet.modules.vehicles.domain.model.VehicleId
import java.time.Instant

/** Domain model holding a Rental along with basic related entity info for listing. */
data class RentalWithDetails(
    val rental: Rental,
    val vehiclePlateNumber: String?,
    val vehicleMake: String?,
    val vehicleModel: String?,
    val customerName: String?
)

/** Repository interface for Rental persistence. */
interface RentalRepository {
    /** Find a rental by its unique identifier. */
    suspend fun findById(id: RentalId): Rental?

    /** Find a rental by ID including enriched details. */
    suspend fun findByIdWithDetails(id: RentalId): RentalWithDetails?

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

    /** Find rentals with pagination and filtering, including enriched details. */
    suspend fun findAllPaged(
        page: Int = 1,
        limit: Int = 10,
        status: RentalStatus? = null,
        vehicleId: VehicleId? = null,
        customerId: CustomerId? = null
    ): List<RentalWithDetails>

    /** Count total rentals matching the filters. */
    suspend fun count(
        status: RentalStatus? = null,
        vehicleId: VehicleId? = null,
        customerId: CustomerId? = null
    ): Long
}
