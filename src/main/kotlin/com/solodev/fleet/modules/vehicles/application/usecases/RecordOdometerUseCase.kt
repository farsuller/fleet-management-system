package com.solodev.fleet.modules.vehicles.application.usecases

import com.solodev.fleet.modules.domain.models.*
import com.solodev.fleet.modules.domain.ports.VehicleRepository

class RecordOdometerUseCase(
    private val repository: VehicleRepository
) {
    suspend fun execute(id: String, newMileage: Int): Vehicle? {
        val vehicle = repository.findById(VehicleId(id)) ?: return null

        require(newMileage >= vehicle.mileageKm) {
            "New mileage ($newMileage) cannot be less than current mileage (${vehicle.mileageKm})"
        }

        val updated = vehicle.copy(mileageKm = newMileage)
        return repository.save(updated)
    }
}