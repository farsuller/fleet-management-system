package com.solodev.fleet.modules.maintenance.application.usecases

import com.solodev.fleet.modules.maintenance.domain.model.MaintenanceJob
import com.solodev.fleet.modules.maintenance.domain.model.MaintenanceJobId
import com.solodev.fleet.modules.maintenance.domain.repository.MaintenanceRepository

class CompleteMaintenanceUseCase(private val repository: MaintenanceRepository) {
    suspend fun execute(jobId: String, laborCost: Int, partsCost: Int): MaintenanceJob {
        val job = repository.findById(MaintenanceJobId(jobId))
            ?: throw IllegalArgumentException("Job not found")

        val completedJob = job.complete(laborCost, partsCost)
        // Note: In a real implementation, this would also event-source a "MaintenanceCompleted" event
        // to update the Vehicle status back to AVAILABLE via a domain event listener.
        return repository.saveJob(completedJob)
    }
}