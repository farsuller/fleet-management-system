package com.solodev.fleet.modules.vehicles.application.usecases

import com.solodev.fleet.modules.vehicles.domain.model.Vehicle
import com.solodev.fleet.modules.vehicles.domain.repository.VehicleRepository

class ListVehiclesUseCase(
    private val repository: VehicleRepository
) {
    suspend fun execute(): List<Vehicle> {
        return repository.findAll()
    }
}