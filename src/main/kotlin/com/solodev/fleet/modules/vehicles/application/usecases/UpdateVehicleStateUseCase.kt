package com.solodev.fleet.modules.vehicles.application.usecases

import com.solodev.fleet.modules.vehicles.domain.model.Vehicle
import com.solodev.fleet.modules.vehicles.domain.model.VehicleId
import com.solodev.fleet.modules.vehicles.domain.model.VehicleState
import com.solodev.fleet.modules.vehicles.domain.repository.VehicleRepository

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