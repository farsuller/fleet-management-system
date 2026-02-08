package com.solodev.fleet.modules.maintenance.domain.repository

import com.solodev.fleet.modules.maintenance.domain.model.MaintenanceJob
import com.solodev.fleet.modules.maintenance.domain.model.MaintenanceJobId
import com.solodev.fleet.modules.maintenance.domain.model.MaintenanceJobType
import com.solodev.fleet.modules.maintenance.domain.model.MaintenancePart
import com.solodev.fleet.modules.maintenance.domain.model.MaintenancePriority
import com.solodev.fleet.modules.maintenance.domain.model.MaintenanceStatus
import com.solodev.fleet.modules.vehicles.domain.model.VehicleId
import com.solodev.fleet.modules.maintenance.infrastructure.persistence.MaintenanceJobsTable
import com.solodev.fleet.modules.maintenance.infrastructure.persistence.MaintenancePartsTable
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.update
import java.time.Instant
import java.util.UUID

/**
 * PostgreSQL implementation of MaintenanceRepository using Exposed ORM.
 */
class MaintenanceRepositoryImpl : MaintenanceRepository {

    private suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }

    private fun ResultRow.toMaintenanceJob() = MaintenanceJob(
        id = MaintenanceJobId(this[MaintenanceJobsTable.id].value.toString()),
        jobNumber = this[MaintenanceJobsTable.jobNumber],
        vehicleId = VehicleId(this[MaintenanceJobsTable.vehicleId].value.toString()),
        status = MaintenanceStatus.valueOf(this[MaintenanceJobsTable.status]),
        jobType = MaintenanceJobType.valueOf(this[MaintenanceJobsTable.jobType]),
        description = this[MaintenanceJobsTable.description],
        priority = MaintenancePriority.valueOf(this[MaintenanceJobsTable.priority]),
        scheduledDate = this[MaintenanceJobsTable.scheduledDate],
        startedAt = this[MaintenanceJobsTable.startedAt],
        completedAt = this[MaintenanceJobsTable.completedAt],
        odometerKm = this[MaintenanceJobsTable.odometerKm],
        laborCostCents = this[MaintenanceJobsTable.laborCostCents],
        partsCostCents = this[MaintenanceJobsTable.partsCostCents],
        currencyCode = this[MaintenanceJobsTable.currencyCode],
        assignedToUserId = this[MaintenanceJobsTable.assignedToUserId],
        completedByUserId = this[MaintenanceJobsTable.completedByUserId],
        notes = this[MaintenanceJobsTable.notes]
    )

    private fun ResultRow.toMaintenancePart() = MaintenancePart(
        id = this[MaintenancePartsTable.id].value,
        jobId = MaintenanceJobId(this[MaintenancePartsTable.jobId].value.toString()),
        partNumber = this[MaintenancePartsTable.partNumber],
        partName = this[MaintenancePartsTable.partName],
        quantity = this[MaintenancePartsTable.quantity],
        unitCostCents = this[MaintenancePartsTable.unitCostCents],
        currencyCode = this[MaintenancePartsTable.currencyCode],
        supplier = this[MaintenancePartsTable.supplier],
        notes = this[MaintenancePartsTable.notes]
    )

    override suspend fun findByJobNumber(jobNumber: String): MaintenanceJob? = dbQuery {
        MaintenanceJobsTable.selectAll().where { MaintenanceJobsTable.jobNumber eq jobNumber }
            .map { it.toMaintenanceJob() }
            .singleOrNull()
    }

    override suspend fun saveJob(job: MaintenanceJob): MaintenanceJob = dbQuery {
        val exists =
            MaintenanceJobsTable.selectAll().where { MaintenanceJobsTable.id eq UUID.fromString(job.id.value) }.count() > 0

        if (exists) {
            MaintenanceJobsTable.update({ MaintenanceJobsTable.id eq UUID.fromString(job.id.value) }) {
                it[status] = job.status.name
                it[startedAt] = job.startedAt
                it[completedAt] = job.completedAt
                it[laborCostCents] = job.laborCostCents
                it[partsCostCents] = job.partsCostCents
                it[updatedAt] = Instant.now()
            }
        } else {
            MaintenanceJobsTable.insert {
                it[id] = UUID.fromString(job.id.value)
                it[jobNumber] = job.jobNumber
                it[vehicleId] = UUID.fromString(job.vehicleId.value)
                it[status] = job.status.name
                it[jobType] = job.jobType.name
                it[description] = job.description
                it[scheduledDate] = job.scheduledDate
                it[createdAt] = Instant.now()
                it[updatedAt] = Instant.now()
            }
        }
        job
    }

    override suspend fun findById(id: MaintenanceJobId): MaintenanceJob? = dbQuery {
        MaintenanceJobsTable.selectAll().where { MaintenanceJobsTable.id eq UUID.fromString(id.value) }
            .map { it.toMaintenanceJob() }
            .singleOrNull()
    }

    override suspend fun findByVehicleId(vehicleId: VehicleId): List<MaintenanceJob> = dbQuery {
        MaintenanceJobsTable.selectAll().where { MaintenanceJobsTable.vehicleId eq UUID.fromString(vehicleId.value) }
            .map { it.toMaintenanceJob() }
    }

    override suspend fun findByStatus(status: MaintenanceStatus): List<MaintenanceJob> = dbQuery {
        MaintenanceJobsTable.selectAll().where { MaintenanceJobsTable.status eq status.name }
            .orderBy(MaintenanceJobsTable.scheduledDate to SortOrder.ASC)
            .map { it.toMaintenanceJob() }
    }

    override suspend fun findScheduledBetween(startDate: Instant, endDate: Instant): List<MaintenanceJob> = dbQuery {
        MaintenanceJobsTable.selectAll().where {
            (MaintenanceJobsTable.scheduledDate greaterEq startDate) and
                    (MaintenanceJobsTable.scheduledDate lessEq endDate)
        }
            .orderBy(MaintenanceJobsTable.scheduledDate to SortOrder.ASC)
            .map { it.toMaintenanceJob() }
    }

    override suspend fun findByAssignedUser(userId: UUID): List<MaintenanceJob> = dbQuery {
        MaintenanceJobsTable.selectAll().where { MaintenanceJobsTable.assignedToUserId eq userId }
            .orderBy(MaintenanceJobsTable.scheduledDate to SortOrder.ASC)
            .map { it.toMaintenanceJob() }
    }
}