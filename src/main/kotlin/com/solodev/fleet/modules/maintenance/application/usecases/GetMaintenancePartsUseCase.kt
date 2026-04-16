package com.solodev.fleet.modules.maintenance.application.usecases

import com.solodev.fleet.modules.maintenance.domain.model.MaintenanceJobId
import com.solodev.fleet.modules.maintenance.domain.model.MaintenancePart
import com.solodev.fleet.modules.maintenance.domain.repository.MaintenanceRepository

class GetMaintenancePartsUseCase(
    private val repository: MaintenanceRepository,
) {
    suspend fun execute(jobId: String): List<MaintenancePart> = repository.findPartsByJobId(MaintenanceJobId(jobId))
}
