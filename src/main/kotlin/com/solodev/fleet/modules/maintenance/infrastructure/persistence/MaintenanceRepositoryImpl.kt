package com.solodev.fleet.modules.maintenance.infrastructure.persistence

import com.solodev.fleet.modules.maintenance.domain.model.MaintenanceJob
import com.solodev.fleet.modules.maintenance.domain.model.MaintenanceJobId
import com.solodev.fleet.modules.maintenance.domain.model.MaintenanceJobType
import com.solodev.fleet.modules.maintenance.domain.model.MaintenancePart
import com.solodev.fleet.modules.maintenance.domain.model.MaintenancePriority
import com.solodev.fleet.modules.maintenance.domain.model.MaintenanceStatus
import com.solodev.fleet.modules.maintenance.domain.model.VehicleIncident
import com.solodev.fleet.modules.maintenance.domain.model.IncidentId
import com.solodev.fleet.modules.maintenance.domain.model.IncidentSeverity
import com.solodev.fleet.modules.maintenance.domain.model.IncidentStatus
import com.solodev.fleet.modules.maintenance.domain.repository.MaintenanceRepository
import com.solodev.fleet.modules.vehicles.domain.model.VehicleId
import com.solodev.fleet.modules.vehicles.infrastructure.persistence.VehiclesTable
import com.solodev.fleet.shared.helpers.dbQuery
import java.time.Instant
import java.util.UUID
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update

/** PostgreSQL implementation of MaintenanceRepository using Exposed ORM. */
class MaintenanceRepositoryImpl : MaintenanceRepository {

    private fun ResultRow.toMaintenanceJob() =
            MaintenanceJob(
                    id = MaintenanceJobId(this[MaintenanceJobsTable.id].value.toString()),
                    jobNumber = this[MaintenanceJobsTable.jobNumber],
                    vehicleId = VehicleId(this[MaintenanceJobsTable.vehicleId].value.toString()),
                    vehiclePlate = this.getOrNull(VehiclesTable.plateNumber),
                    vehicleMake = this.getOrNull(VehiclesTable.make),
                    vehicleModel = this.getOrNull(VehiclesTable.model),
                    status = MaintenanceStatus.valueOf(this[MaintenanceJobsTable.status]),
                    jobType = MaintenanceJobType.fromString(this[MaintenanceJobsTable.jobType]),
                    description = this[MaintenanceJobsTable.description],
                    priority = MaintenancePriority.fromString(this[MaintenanceJobsTable.priority]),
                    scheduledDate = this[MaintenanceJobsTable.scheduledDate],
                    startedAt = this[MaintenanceJobsTable.startedAt],
                    completedAt = this[MaintenanceJobsTable.completedAt],
                    odometerKm = this[MaintenanceJobsTable.odometerKm],
                    laborCost = this[MaintenanceJobsTable.laborCost],
                    partsCost = this[MaintenanceJobsTable.partsCost],
                    currencyCode = this[MaintenanceJobsTable.currencyCode],
                    assignedToUserId = this[MaintenanceJobsTable.assignedToUserId],
                    completedByUserId = this[MaintenanceJobsTable.completedByUserId],
                    notes = this[MaintenanceJobsTable.notes]
            )

    private fun ResultRow.toMaintenancePart() =
            MaintenancePart(
                    id = this[MaintenancePartsTable.id].value,
                    jobId = MaintenanceJobId(this[MaintenancePartsTable.jobId].value.toString()),
                    partNumber = this[MaintenancePartsTable.partNumber],
                    partName = this[MaintenancePartsTable.partName],
                    quantity = this[MaintenancePartsTable.quantity],
                    unitCost = this[MaintenancePartsTable.unitCost],
                    currencyCode = this[MaintenancePartsTable.currencyCode],
                    supplier = this[MaintenancePartsTable.supplier],
                    notes = this[MaintenancePartsTable.notes]
            )

    private fun ResultRow.toVehicleIncident(): VehicleIncident {
        return VehicleIncident(
                id = IncidentId(this[VehicleIncidentsTable.id].value),
                vehicleId = VehicleId(this[VehicleIncidentsTable.vehicleId].value.toString()),
                vehiclePlate = this.getOrNull(VehiclesTable.plateNumber),
                title = this[VehicleIncidentsTable.title],
                description = this[VehicleIncidentsTable.description],
                severity = IncidentSeverity.valueOf(this[VehicleIncidentsTable.severity]),
                status = IncidentStatus.valueOf(this[VehicleIncidentsTable.status]),
                reportedAt = this[VehicleIncidentsTable.reportedAt],
                reportedByUserId = this[VehicleIncidentsTable.reportedByUserId],
                maintenanceJobId = this[VehicleIncidentsTable.maintenanceJobId]?.let { MaintenanceJobId(it.value.toString()) },
                odometerKm = this[VehicleIncidentsTable.odometerKm],
                latitude = this[VehicleIncidentsTable.latitude],
                longitude = this[VehicleIncidentsTable.longitude]
        )
    }

    override suspend fun findById(id: MaintenanceJobId): MaintenanceJob? = dbQuery {
        MaintenanceJobsTable
                .join(VehiclesTable, JoinType.LEFT, MaintenanceJobsTable.vehicleId, VehiclesTable.id)
                .selectAll()
                .where { MaintenanceJobsTable.id eq UUID.fromString(id.value) }
                .map { it.toMaintenanceJob() }
                .singleOrNull()
    }

    override suspend fun findByJobNumber(jobNumber: String): MaintenanceJob? = dbQuery {
        MaintenanceJobsTable
                .join(VehiclesTable, JoinType.LEFT, MaintenanceJobsTable.vehicleId, VehiclesTable.id)
                .selectAll()
                .where { MaintenanceJobsTable.jobNumber eq jobNumber }
                .map { it.toMaintenanceJob() }
                .singleOrNull()
    }

    override suspend fun saveJob(job: MaintenanceJob): MaintenanceJob = dbQuery {
        val exists =
                MaintenanceJobsTable.selectAll()
                        .where { MaintenanceJobsTable.id eq UUID.fromString(job.id.value) }
                        .count() > 0

        if (exists) {
            MaintenanceJobsTable.update({
                MaintenanceJobsTable.id eq UUID.fromString(job.id.value)
            }) {
                it[status] = job.status.name
                it[startedAt] = job.startedAt
                it[completedAt] = job.completedAt
                it[laborCost] = job.laborCost
                it[partsCost] = job.partsCost
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

    override suspend fun findAll(): List<MaintenanceJob> = dbQuery {
        MaintenanceJobsTable
                .join(VehiclesTable, JoinType.LEFT, MaintenanceJobsTable.vehicleId, VehiclesTable.id)
                .selectAll()
                .orderBy(MaintenanceJobsTable.scheduledDate to SortOrder.DESC)
                .map { it.toMaintenanceJob() }
    }

    override suspend fun findByVehicleId(vehicleId: VehicleId): List<MaintenanceJob> = dbQuery {
        MaintenanceJobsTable
                .join(VehiclesTable, JoinType.LEFT, MaintenanceJobsTable.vehicleId, VehiclesTable.id)
                .selectAll()
                .where { MaintenanceJobsTable.vehicleId eq UUID.fromString(vehicleId.value) }
                .orderBy(MaintenanceJobsTable.scheduledDate to SortOrder.DESC)
                .map { it.toMaintenanceJob() }
    }

    override suspend fun findByStatus(status: MaintenanceStatus): List<MaintenanceJob> = dbQuery {
        MaintenanceJobsTable
                .join(VehiclesTable, JoinType.LEFT, MaintenanceJobsTable.vehicleId, VehiclesTable.id)
                .selectAll()
                .where { MaintenanceJobsTable.status eq status.name }
                .orderBy(MaintenanceJobsTable.scheduledDate to SortOrder.ASC)
                .map { it.toMaintenanceJob() }
    }

    override suspend fun findScheduledBetween(
            startDate: Instant,
            endDate: Instant
    ): List<MaintenanceJob> = dbQuery {
        MaintenanceJobsTable
                .join(VehiclesTable, JoinType.LEFT, MaintenanceJobsTable.vehicleId, VehiclesTable.id)
                .selectAll()
                .where {
                    (MaintenanceJobsTable.scheduledDate greaterEq startDate) and
                            (MaintenanceJobsTable.scheduledDate lessEq endDate)
                }
                .orderBy(MaintenanceJobsTable.scheduledDate to SortOrder.ASC)
                .map { it.toMaintenanceJob() }
    }

    override suspend fun findByAssignedUser(userId: UUID): List<MaintenanceJob> = dbQuery {
        MaintenanceJobsTable
                .join(VehiclesTable, JoinType.LEFT, MaintenanceJobsTable.vehicleId, VehiclesTable.id)
                .selectAll()
                .where { MaintenanceJobsTable.assignedToUserId eq userId }
                .orderBy(MaintenanceJobsTable.scheduledDate to SortOrder.ASC)
                .map { it.toMaintenanceJob() }
    }

    // ── Incident methods implementation ──────────────────────────────────────

    override suspend fun findIncidentById(id: IncidentId): VehicleIncident? = dbQuery {
        (VehicleIncidentsTable leftJoin VehiclesTable)
                .selectAll()
                .where { VehicleIncidentsTable.id eq id.value }
                .map { it.toVehicleIncident() }
                .singleOrNull()
    }

    override suspend fun findActiveIncidentsByVehicleId(vehicleId: VehicleId): List<VehicleIncident> = dbQuery {
        (VehicleIncidentsTable leftJoin VehiclesTable)
                .selectAll()
                .where { 
                    (VehicleIncidentsTable.vehicleId eq UUID.fromString(vehicleId.value)) and 
                    (VehicleIncidentsTable.status eq IncidentStatus.REPORTED.name) 
                }
                .orderBy(VehicleIncidentsTable.reportedAt to SortOrder.DESC)
                .map { it.toVehicleIncident() }
    }

    override suspend fun findIncidentsByVehicleId(vehicleId: VehicleId): List<VehicleIncident> = dbQuery {
        (VehicleIncidentsTable leftJoin VehiclesTable)
                .selectAll()
                .where { VehicleIncidentsTable.vehicleId eq UUID.fromString(vehicleId.value) }
                .orderBy(VehicleIncidentsTable.reportedAt to SortOrder.DESC)
                .map { it.toVehicleIncident() }
    }

    override suspend fun findIncidentsByJobId(jobId: MaintenanceJobId): List<VehicleIncident> = dbQuery {
        (VehicleIncidentsTable leftJoin VehiclesTable)
                .selectAll()
                .where { VehicleIncidentsTable.maintenanceJobId eq UUID.fromString(jobId.value) }
                .orderBy(VehicleIncidentsTable.reportedAt to SortOrder.DESC)
                .map { it.toVehicleIncident() }
    }

    override suspend fun saveIncident(incident: VehicleIncident): VehicleIncident = dbQuery {
        val exists = VehicleIncidentsTable.selectAll()
                .where { VehicleIncidentsTable.id eq incident.id.value }
                .count() > 0

        if (exists) {
            VehicleIncidentsTable.update({ VehicleIncidentsTable.id eq incident.id.value }) {
                it[status] = incident.status.name
                it[maintenanceJobId] = incident.maintenanceJobId?.let { jid -> UUID.fromString(jid.value) }
                it[updatedAt] = Instant.now()
            }
        } else {
            VehicleIncidentsTable.insert {
                it[id] = incident.id.value
                it[vehicleId] = UUID.fromString(incident.vehicleId.value)
                it[maintenanceJobId] = incident.maintenanceJobId?.let { jid -> UUID.fromString(jid.value) }
                it[title] = incident.title
                it[description] = incident.description
                it[severity] = incident.severity.name
                it[status] = incident.status.name
                it[reportedAt] = incident.reportedAt
                it[reportedByUserId] = incident.reportedByUserId
                it[odometerKm] = incident.odometerKm
                it[latitude] = incident.latitude
                it[longitude] = incident.longitude
                it[createdAt] = Instant.now()
                it[updatedAt] = Instant.now()
            }
        }
        incident
    }

    override suspend fun findAllIncidents(status: IncidentStatus?): List<VehicleIncident> = dbQuery {
        val query = (VehicleIncidentsTable leftJoin VehiclesTable).selectAll()
        if (status != null) {
            query.where { VehicleIncidentsTable.status eq status.name }
        }
        query.orderBy(VehicleIncidentsTable.reportedAt to SortOrder.DESC)
            .map { it.toVehicleIncident() }
    }
}
