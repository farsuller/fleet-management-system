package com.solodev.fleet.modules.vehicles.application.usecases

import com.solodev.fleet.modules.domain.models.*
import com.solodev.fleet.modules.domain.ports.VehicleRepository

class UpdateVehicleStateUseCase(
    private val repository: VehicleRepository
) {
    suspend fun execute(id: String, newState: String): Vehicle? {
        val vehicle = repository.findById(VehicleId(id)) ?: return null
        val state = VehicleState.valueOf(newState)

        val updated = vehicle.copy(state = state)
        return repository.save(updated)
    }
}