package com.solodev.fleet.modules.vehicles.application.usecases

import com.solodev.fleet.modules.domain.models.*
import com.solodev.fleet.modules.domain.ports.VehicleRepository

class GetVehicleUseCase(
    private val repository: VehicleRepository
) {
    suspend fun execute(id: String): Vehicle? {
        return repository.findById(VehicleId(id))
    }
}