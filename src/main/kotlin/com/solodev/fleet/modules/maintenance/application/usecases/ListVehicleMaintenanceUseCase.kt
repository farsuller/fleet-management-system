package com.solodev.fleet.modules.maintenance.application.usecases

import com.solodev.fleet.modules.domain.models.MaintenanceJob
import com.solodev.fleet.modules.domain.models.VehicleId
import com.solodev.fleet.modules.domain.ports.MaintenanceRepository

class ListVehicleMaintenanceUseCase(private val repository: MaintenanceRepository) {
    suspend fun execute(vehicleId: String): List<MaintenanceJob> {
        return repository.findByVehicleId(VehicleId(vehicleId))
    }
}