package com.solodev.fleet.modules.vehicles.application.usecases

import com.solodev.fleet.modules.domain.models.VehicleId
import com.solodev.fleet.modules.domain.ports.VehicleRepository

class DeleteVehicleUseCase(
    private val repository: VehicleRepository
) {
    suspend fun execute(id: String): Boolean {
        return repository.deleteById(VehicleId(id))
    }
}