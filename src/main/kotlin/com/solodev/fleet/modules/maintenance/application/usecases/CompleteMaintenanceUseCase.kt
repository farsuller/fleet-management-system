package com.solodev.fleet.modules.maintenance.application.usecases

import com.solodev.fleet.modules.maintenance.domain.model.MaintenanceJob
import com.solodev.fleet.modules.maintenance.domain.model.MaintenanceJobId
import com.solodev.fleet.modules.maintenance.domain.repository.MaintenanceRepository

class CompleteMaintenanceUseCase(private val repository: MaintenanceRepository) {
    suspend fun execute(jobId: String, laborCost: Long, partsCost: Long): MaintenanceJob {
        val job =
            repository.findById(MaintenanceJobId(jobId))
                ?: throw IllegalArgumentException("Job not found")

        val completedJob = job.complete(laborCost.toInt(), partsCost.toInt())
        return repository.saveJob(completedJob)
    }
}
