package com.solodev.fleet.modules.maintenance.application.usecases

import com.solodev.fleet.modules.maintenance.domain.model.MaintenanceJob
import com.solodev.fleet.modules.maintenance.domain.model.MaintenanceJobId
import com.solodev.fleet.modules.maintenance.domain.repository.MaintenanceRepository

class GetMaintenanceJobUseCase(private val repository: MaintenanceRepository) {
    suspend fun execute(jobId: String): MaintenanceJob {
        return repository.findById(MaintenanceJobId(jobId))
            ?: throw IllegalArgumentException("Maintenance job not found: $jobId")
    }
}
