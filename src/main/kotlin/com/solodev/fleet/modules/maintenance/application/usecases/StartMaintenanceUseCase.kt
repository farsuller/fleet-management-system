package com.solodev.fleet.modules.maintenance.application.usecases

import com.solodev.fleet.modules.domain.models.MaintenanceJob
import com.solodev.fleet.modules.domain.models.MaintenanceJobId
import com.solodev.fleet.modules.domain.ports.MaintenanceRepository

class StartMaintenanceUseCase(private val repository: MaintenanceRepository) {
    suspend fun execute(jobId: String): MaintenanceJob {
        val job = repository.findById(MaintenanceJobId(jobId))
            ?: throw IllegalArgumentException("Job not found")

        val startedJob = job.start()
        return repository.saveJob(startedJob)
    }
}