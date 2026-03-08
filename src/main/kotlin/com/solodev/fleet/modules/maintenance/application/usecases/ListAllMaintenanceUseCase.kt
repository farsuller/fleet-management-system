package com.solodev.fleet.modules.maintenance.application.usecases

import com.solodev.fleet.modules.maintenance.domain.model.MaintenanceJob
import com.solodev.fleet.modules.maintenance.domain.model.MaintenanceStatus
import com.solodev.fleet.modules.maintenance.domain.repository.MaintenanceRepository

/** Lists all maintenance jobs, optionally filtered by status. */
class ListAllMaintenanceUseCase(private val repository: MaintenanceRepository) {
    suspend fun execute(status: MaintenanceStatus? = null): List<MaintenanceJob> =
        if (status != null) repository.findByStatus(status)
        else repository.findAll()
}
