package com.solodev.fleet.modules.vehicles.application.usecases

import com.solodev.fleet.modules.vehicles.domain.repository.VehicleRepository
import com.solodev.fleet.modules.vehicles.application.dto.VehicleUpdateRequest
import com.solodev.fleet.modules.vehicles.domain.model.Vehicle
import com.solodev.fleet.modules.vehicles.domain.model.VehicleId

class UpdateVehicleUseCase(
    private val repository: VehicleRepository
) {
    suspend fun execute(id: String, request: VehicleUpdateRequest): Vehicle? {
        val existing = repository.findById(VehicleId(id)) ?: return null

        val updated = existing.copy(
            licensePlate = request.licensePlate ?: existing.licensePlate,
            color = request.color ?: existing.color,
            dailyRateCents = request.dailyRateCents ?: existing.dailyRateCents
        )

        return repository.save(updated)
    }
}