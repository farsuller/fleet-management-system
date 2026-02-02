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

/** Repository interface for Customer persistence. */
interface CustomerRepository {
    /** Find a customer by their unique identifier. */
    suspend fun findById(id: CustomerId): Customer?

    /** Find a customer by email. */
    suspend fun findByEmail(email: String): Customer?

    /** Find a customer by driver license number. */
    suspend fun findByDriverLicense(licenseNumber: String): Customer?

    /** Save a new customer or update an existing one. */
    suspend fun save(customer: Customer): Customer

    /** Find all customers. */
    suspend fun findAll(): List<Customer>

    /** Delete a customer by ID. */
    suspend fun deleteById(id: CustomerId): Boolean
}
