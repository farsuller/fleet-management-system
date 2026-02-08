package com.solodev.fleet.modules.maintenance.application.usecases

import com.solodev.fleet.modules.maintenance.domain.model.MaintenanceJob
import com.solodev.fleet.modules.vehicles.domain.model.VehicleId
import com.solodev.fleet.modules.maintenance.domain.repository.MaintenanceRepository

class ListVehicleMaintenanceUseCase(private val repository: MaintenanceRepository) {
    suspend fun execute(vehicleId: String): List<MaintenanceJob> {
        return repository.findByVehicleId(VehicleId(vehicleId))
    }
}