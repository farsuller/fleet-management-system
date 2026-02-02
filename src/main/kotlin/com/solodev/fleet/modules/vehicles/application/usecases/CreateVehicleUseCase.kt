package com.solodev.fleet.modules.vehicles.application.usecases

import com.solodev.fleet.modules.domain.models.Vehicle
import com.solodev.fleet.modules.domain.models.VehicleId
import com.solodev.fleet.modules.domain.ports.VehicleRepository
import com.solodev.fleet.modules.vehicles.application.dto.VehicleRequest
import java.util.*

class CreateVehicleUseCase(private val repository: VehicleRepository) {
    suspend fun execute(request: VehicleRequest): Vehicle {
        val vehicle =
                Vehicle(
                        id = VehicleId(UUID.randomUUID().toString()),
                        plateNumber = request.plateNumber,
                        make = request.make,
                        model = request.model,
                        year = request.year,
                        passengerCapacity = request.capacity
                )
        return repository.save(vehicle)
    }
}
