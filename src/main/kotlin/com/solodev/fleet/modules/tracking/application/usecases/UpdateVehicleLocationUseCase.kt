package com.solodev.fleet.modules.tracking.application.usecases

import com.solodev.fleet.modules.tracking.infrastructure.persistence.PostGISAdapter
import com.solodev.fleet.modules.vehicles.domain.model.VehicleId
import com.solodev.fleet.modules.vehicles.domain.repository.VehicleRepository
import com.solodev.fleet.shared.domain.model.Location
import com.solodev.fleet.shared.exceptions.NotFoundException
import java.util.*
import org.slf4j.LoggerFactory

/**
 * Use case to process real-time vehicle location updates. Handles route snapping and persistence of
 * spatial data.
 */
class UpdateVehicleLocationUseCase(
        private val vehicleRepository: VehicleRepository,
        private val spatialAdapter: PostGISAdapter
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    suspend fun execute(vehicleId: String, rawLocation: Location, routeId: UUID? = null): Location {
        logger.info("Updating location for vehicle $vehicleId: $rawLocation")

        val vehicle =
                vehicleRepository.findById(VehicleId(vehicleId))
                        ?: throw NotFoundException(
                                "VEHICLE_NOT_FOUND",
                                "Vehicle not found: $vehicleId"
                        )

        var finalLocation = rawLocation
        var progress = vehicle.routeProgress

        // If a route is provided, snap the location to the digital rail
        if (routeId != null) {
            val snapped = spatialAdapter.snapToRoute(rawLocation, routeId)
            if (snapped != null) {
                finalLocation = snapped.first
                progress = snapped.second
                logger.debug(
                        "Snapped location for $vehicleId to $finalLocation (Progress: $progress)"
                )
            }
        }

        // Update the vehicle entity with new spatial data
        val updatedVehicle =
                vehicle.copy(
                        lastLocation = finalLocation,
                        routeProgress = progress
                        // TODO: Calculate bearing based on previous location
                        )

        vehicleRepository.save(updatedVehicle)

        // TODO: Broadcast update to WebSockets for the live dashboard

        return finalLocation
    }
}
