package com.solodev.fleet.modules.maintenance.application.usecases

import com.solodev.fleet.modules.maintenance.domain.model.MaintenanceJob
import com.solodev.fleet.modules.maintenance.domain.model.MaintenanceJobId
import com.solodev.fleet.modules.maintenance.domain.model.MaintenanceJobType
import com.solodev.fleet.modules.maintenance.domain.model.MaintenanceStatus
import com.solodev.fleet.modules.vehicles.domain.model.VehicleId
import com.solodev.fleet.modules.maintenance.domain.repository.MaintenanceRepository
import com.solodev.fleet.modules.maintenance.application.dto.MaintenanceRequest
import java.time.Instant
import java.util.UUID

class ScheduleMaintenanceUseCase(private val repository: MaintenanceRepository) {
    suspend fun execute(request: MaintenanceRequest): MaintenanceJob {
        val job = MaintenanceJob(
            id = MaintenanceJobId(UUID.randomUUID().toString()),
            jobNumber = "MAINT-${System.currentTimeMillis()}",
            vehicleId = VehicleId(request.vehicleId),
            status = MaintenanceStatus.SCHEDULED,
            jobType = MaintenanceJobType.valueOf(request.jobType),
            description = request.description,
            scheduledDate = Instant.parse(request.scheduledDate)
        )
        return repository.saveJob(job)
    }
}