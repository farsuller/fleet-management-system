package com.solodev.fleet.modules.vehicles.application.usecases

import com.solodev.fleet.modules.vehicles.domain.model.VehicleId
import com.solodev.fleet.modules.vehicles.domain.model.VehicleState
import com.solodev.fleet.modules.vehicles.domain.repository.VehicleRepository

class DeleteVehicleUseCase(
    private val repository: VehicleRepository,
) {
    suspend fun execute(id: String): Boolean {
        val vehicle = repository.findById(VehicleId(id)) ?: return false
        if (vehicle.state == VehicleState.RENTED || vehicle.state == VehicleState.MAINTENANCE) {
            throw IllegalStateException("Cannot delete vehicle while it is in ${vehicle.state} status")
        }
        return repository.deleteById(VehicleId(id))
    }
}
