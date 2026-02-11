package com.solodev.fleet.modules.vehicles.domain.repository

import com.solodev.fleet.modules.vehicles.domain.model.Vehicle
import com.solodev.fleet.modules.vehicles.domain.model.VehicleId
import com.solodev.fleet.shared.models.PaginationParams

/**
 * Repository interface for Vehicle persistence.
 *
 * This is a port in Clean Architecture - it defines what the domain needs without specifying how
 * it's implemented. The actual implementation will be in the infrastructure layer using
 * Exposed/PostgreSQL.
 */
interface VehicleRepository {
    /**
     * Find a vehicle by its unique identifier.
     *
     * @return Vehicle if found, null otherwise
     */
    suspend fun findById(id: VehicleId): Vehicle?

    /**
     * Find a vehicle by its plate number.
     *
     * @return Vehicle if found, null otherwise
     */
    suspend fun findByPlateNumber(plateNumber: String): Vehicle?

    /**
     * Save a new vehicle or update an existing one.
     *
     * @return The saved vehicle
     */
    suspend fun save(vehicle: Vehicle): Vehicle

    /**
     * Find all vehicles (with optional pagination in future).
     *
     * @return List of all vehicles
     */
    suspend fun findAll(params: PaginationParams): Pair<List<Vehicle>, Long>

    /**
     * Delete a vehicle by ID.
     *
     * @return true if deleted, false if not found
     */
    suspend fun deleteById(id: VehicleId): Boolean
}