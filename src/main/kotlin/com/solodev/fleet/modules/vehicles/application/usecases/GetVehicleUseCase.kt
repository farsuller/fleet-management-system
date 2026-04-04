package com.solodev.fleet.modules.vehicles.application.usecases

import com.solodev.fleet.modules.vehicles.domain.model.Vehicle
import com.solodev.fleet.modules.vehicles.domain.model.VehicleId
import com.solodev.fleet.modules.vehicles.domain.repository.VehicleRepository

class GetVehicleUseCase(
    private val repository: VehicleRepository,
) {
    suspend fun execute(id: String): Vehicle? = repository.findById(VehicleId(id))
}
