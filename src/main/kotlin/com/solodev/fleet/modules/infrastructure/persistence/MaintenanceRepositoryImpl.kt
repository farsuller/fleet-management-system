package com.solodev.fleet.modules.infrastructure.persistence

import com.solodev.fleet.modules.domain.models.*
import com.solodev.fleet.modules.domain.ports.MaintenanceRepository
import java.time.Instant
import java.util.*
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

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

    override suspend fun findById(id: MaintenanceJobId): MaintenanceJob? = dbQuery {
        MaintenanceJobsTable.select { MaintenanceJobsTable.id eq UUID.fromString(id.value) }
            .map { it.toMaintenanceJob() }
            .singleOrNull()
    }

    override suspend fun findByJobNumber(jobNumber: String): MaintenanceJob? = dbQuery {
        MaintenanceJobsTable.select { MaintenanceJobsTable.jobNumber eq jobNumber }
            .map { it.toMaintenanceJob() }
            .singleOrNull()
    }

    override suspend fun save(job: MaintenanceJob): MaintenanceJob = dbQuery {
        val jobUuid = UUID.fromString(job.id.value)
        val now = Instant.now()

        val exists = MaintenanceJobsTable.select { MaintenanceJobsTable.id eq jobUuid }.count() > 0

        if (exists) {
            MaintenanceJobsTable.update({ MaintenanceJobsTable.id eq jobUuid }) {
                it[jobNumber] = job.jobNumber
                it[vehicleId] = UUID.fromString(job.vehicleId.value)
                it[status] = job.status.name
                it[jobType] = job.jobType.name
                it[description] = job.description
                it[priority] = job.priority.name
                it[scheduledDate] = job.scheduledDate
                it[startedAt] = job.startedAt
                it[completedAt] = job.completedAt
                it[odometerKm] = job.odometerKm
                it[laborCostCents] = job.laborCostCents
                it[partsCostCents] = job.partsCostCents
                it[currencyCode] = job.currencyCode
                it[assignedToUserId] = job.assignedToUserId
                it[completedByUserId] = job.completedByUserId
                it[notes] = job.notes
                it[updatedAt] = now
            }
        } else {
            MaintenanceJobsTable.insert {
                it[id] = jobUuid
                it[jobNumber] = job.jobNumber
                it[vehicleId] = UUID.fromString(job.vehicleId.value)
                it[status] = job.status.name
                it[jobType] = job.jobType.name
                it[description] = job.description
                it[priority] = job.priority.name
                it[scheduledDate] = job.scheduledDate
                it[startedAt] = job.startedAt
                it[completedAt] = job.completedAt
                it[odometerKm] = job.odometerKm
                it[laborCostCents] = job.laborCostCents
                it[partsCostCents] = job.partsCostCents
                it[currencyCode] = job.currencyCode
                it[assignedToUserId] = job.assignedToUserId
                it[completedByUserId] = job.completedByUserId
                it[notes] = job.notes
                it[createdAt] = now
                it[updatedAt] = now
            }
        }

        job
    }

    override suspend fun findByVehicleId(vehicleId: VehicleId): List<MaintenanceJob> = dbQuery {
        MaintenanceJobsTable.select { MaintenanceJobsTable.vehicleId eq UUID.fromString(vehicleId.value) }
            .orderBy(MaintenanceJobsTable.scheduledDate to SortOrder.DESC)
            .map { it.toMaintenanceJob() }
    }

    override suspend fun findByStatus(status: MaintenanceStatus): List<MaintenanceJob> = dbQuery {
        MaintenanceJobsTable.select { MaintenanceJobsTable.status eq status.name }
            .orderBy(MaintenanceJobsTable.scheduledDate to SortOrder.ASC)
            .map { it.toMaintenanceJob() }
    }

    override suspend fun findScheduledBetween(startDate: Instant, endDate: Instant): List<MaintenanceJob> = dbQuery {
        MaintenanceJobsTable.select {
            (MaintenanceJobsTable.scheduledDate greaterEq startDate) and
            (MaintenanceJobsTable.scheduledDate lessEq endDate)
        }
            .orderBy(MaintenanceJobsTable.scheduledDate to SortOrder.ASC)
            .map { it.toMaintenanceJob() }
    }

    override suspend fun findByAssignedUser(userId: UUID): List<MaintenanceJob> = dbQuery {
        MaintenanceJobsTable.select { MaintenanceJobsTable.assignedToUserId eq userId }
            .orderBy(MaintenanceJobsTable.scheduledDate to SortOrder.ASC)
            .map { it.toMaintenanceJob() }
    }

    override suspend fun deleteById(id: MaintenanceJobId): Boolean = dbQuery {
        val deletedCount = MaintenanceJobsTable.deleteWhere { MaintenanceJobsTable.id eq UUID.fromString(id.value) }
        deletedCount > 0
    }

    override suspend fun addPart(part: MaintenancePart): MaintenancePart = dbQuery {
        val now = Instant.now()

        MaintenancePartsTable.insert {
            it[id] = part.id
            it[jobId] = UUID.fromString(part.jobId.value)
            it[partNumber] = part.partNumber
            it[partName] = part.partName
            it[quantity] = part.quantity
            it[unitCostCents] = part.unitCostCents
            it[currencyCode] = part.currencyCode
            it[supplier] = part.supplier
            it[notes] = part.notes
            it[addedAt] = now
        }

        part
    }

    override suspend fun findPartsByJobId(jobId: MaintenanceJobId): List<MaintenancePart> = dbQuery {
        MaintenancePartsTable.select { MaintenancePartsTable.jobId eq UUID.fromString(jobId.value) }
            .map { it.toMaintenancePart() }
    }
}
