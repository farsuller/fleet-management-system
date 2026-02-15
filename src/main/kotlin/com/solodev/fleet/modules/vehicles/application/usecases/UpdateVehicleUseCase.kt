package com.solodev.fleet.modules.vehicles.application.usecases

import com.solodev.fleet.modules.vehicles.application.dto.VehicleUpdateRequest
import com.solodev.fleet.modules.vehicles.domain.model.Vehicle
import com.solodev.fleet.modules.vehicles.domain.model.VehicleId
import com.solodev.fleet.modules.vehicles.domain.repository.VehicleRepository

class UpdateVehicleUseCase(private val repository: VehicleRepository) {
    suspend fun execute(id: String, request: VehicleUpdateRequest): Vehicle? {
        val existing = repository.findById(VehicleId(id)) ?: return null

        val updated =
                existing.copy(
                        licensePlate = request.licensePlate ?: existing.licensePlate,
                        color = request.color ?: existing.color,
                        dailyRateAmount = request.dailyRate?.let { (it * 100).toInt() }
                                        ?: existing.dailyRateAmount
                )

        return repository.save(updated)
    }
}
