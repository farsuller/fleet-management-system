package com.solodev.fleet.modules.vehicles.application.usecases

import com.solodev.fleet.modules.domain.models.*
import com.solodev.fleet.modules.domain.ports.VehicleRepository
import com.solodev.fleet.modules.vehicles.application.dto.VehicleRequest
import java.util.UUID

class CreateVehicleUseCase(
    private val repository: VehicleRepository
) {
    suspend fun execute(request: VehicleRequest): Vehicle {
        val vehicle = Vehicle(
            id = VehicleId(UUID.randomUUID().toString()),
            vin = request.vin,
            licensePlate = request.licensePlate,
            make = request.make,
            model = request.model,
            year = request.year,
            color = request.color,
            state = VehicleState.AVAILABLE,
            mileageKm = request.mileageKm
        )

        return repository.save(vehicle)
    }
}
