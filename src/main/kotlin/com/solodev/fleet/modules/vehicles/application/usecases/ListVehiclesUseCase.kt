package com.solodev.fleet.modules.vehicles.application.usecases

import com.solodev.fleet.modules.domain.models.Vehicle
import com.solodev.fleet.modules.domain.ports.VehicleRepository

class ListVehiclesUseCase(
    private val repository: VehicleRepository
) {
    suspend fun execute(): List<Vehicle> {
        return repository.findAll()
    }
}