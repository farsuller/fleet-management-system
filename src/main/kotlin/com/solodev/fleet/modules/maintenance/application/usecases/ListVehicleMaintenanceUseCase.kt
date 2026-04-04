package com.solodev.fleet.modules.maintenance.application.usecases

import com.solodev.fleet.modules.maintenance.domain.model.MaintenanceJob
import com.solodev.fleet.modules.maintenance.domain.repository.MaintenanceRepository
import com.solodev.fleet.modules.vehicles.domain.model.VehicleId

class ListVehicleMaintenanceUseCase(
    private val repository: MaintenanceRepository,
) {
    suspend fun execute(vehicleId: String): List<MaintenanceJob> = repository.findByVehicleId(VehicleId(vehicleId))
}
