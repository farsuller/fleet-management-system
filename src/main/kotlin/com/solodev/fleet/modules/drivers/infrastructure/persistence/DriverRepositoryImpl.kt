package com.solodev.fleet.modules.drivers.infrastructure.persistence

import com.solodev.fleet.modules.drivers.domain.model.Driver
import com.solodev.fleet.modules.drivers.domain.model.DriverId
import com.solodev.fleet.modules.drivers.domain.model.VehicleDriverAssignment
import com.solodev.fleet.modules.drivers.domain.repository.DriverRepository
import com.solodev.fleet.shared.helpers.dbQuery
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNull
import com.solodev.fleet.modules.drivers.domain.model.DriverShift
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

class DriverRepositoryImpl : DriverRepository {

    private fun ResultRow.toDriver() = Driver(
        id           = DriverId(this[DriversTable.id].value.toString()),
        userId       = this[DriversTable.userId],
        firstName    = this[DriversTable.firstName],
        lastName     = this[DriversTable.lastName],
        email        = this[DriversTable.email],
        phone        = this[DriversTable.phone],
        licenseNumber  = this[DriversTable.licenseNumber],
        licenseExpiry  = this[DriversTable.licenseExpiry].atStartOfDay().toInstant(ZoneOffset.UTC),
        licenseClass   = this[DriversTable.licenseClass],
        address      = this[DriversTable.address],
        city         = this[DriversTable.city],
        state        = this[DriversTable.state],
        postalCode   = this[DriversTable.postalCode],
        country      = this[DriversTable.country],
        isActive     = this[DriversTable.isActive],
        createdAt    = this[DriversTable.createdAt],
    )

    private fun ResultRow.toAssignment() = VehicleDriverAssignment(
        id          = this[VehicleDriverAssignmentsTable.id].value.toString(),
        vehicleId   = this[VehicleDriverAssignmentsTable.vehicleId].value.toString(),
        driverId    = this[VehicleDriverAssignmentsTable.driverId].value.toString(),
        assignedAt  = this[VehicleDriverAssignmentsTable.assignedAt],
        releasedAt  = this[VehicleDriverAssignmentsTable.releasedAt],
        notes       = this[VehicleDriverAssignmentsTable.notes],
    )

    private fun ResultRow.toDriverShift() = DriverShift(
        id        = this[DriverShiftsTable.id],
        driverId  = this[DriverShiftsTable.driverId],
        vehicleId = this[DriverShiftsTable.vehicleId],
        startedAt = this[DriverShiftsTable.startedAt],
        endedAt   = this[DriverShiftsTable.endedAt],
        notes     = this[DriverShiftsTable.notes],
    )

    override suspend fun findById(id: DriverId): Driver? = dbQuery {
        DriversTable.selectAll()
            .where { DriversTable.id eq UUID.fromString(id.value) }
            .map { it.toDriver() }
            .singleOrNull()
    }

    override suspend fun findByEmail(email: String): Driver? = dbQuery {
        DriversTable.selectAll()
            .where { DriversTable.email eq email }
            .map { it.toDriver() }
            .singleOrNull()
    }

    override suspend fun findByLicenseNumber(licenseNumber: String): Driver? = dbQuery {
        DriversTable.selectAll()
            .where { DriversTable.licenseNumber eq licenseNumber }
            .map { it.toDriver() }
            .singleOrNull()
    }

    override suspend fun findAll(): List<Driver> = dbQuery {
        DriversTable.selectAll()
            .orderBy(DriversTable.createdAt, SortOrder.DESC)
            .map { it.toDriver() }
    }

    override suspend fun save(driver: Driver): Driver = dbQuery {
        val uuid = UUID.fromString(driver.id.value)
        val now  = Instant.now()
        val exists = DriversTable.selectAll().where { DriversTable.id eq uuid }.count() > 0

        if (exists) {
            DriversTable.update({ DriversTable.id eq uuid }) {
                it[userId]         = driver.userId
                it[firstName]      = driver.firstName
                it[lastName]       = driver.lastName
                it[email]          = driver.email
                it[phone]          = driver.phone
                it[licenseNumber]  = driver.licenseNumber
                it[licenseExpiry]  = LocalDate.ofInstant(driver.licenseExpiry, ZoneOffset.UTC)
                it[licenseClass]   = driver.licenseClass
                it[address]        = driver.address
                it[city]           = driver.city
                it[state]          = driver.state
                it[postalCode]     = driver.postalCode
                it[country]        = driver.country
                it[isActive]       = driver.isActive
                it[updatedAt]      = now
            }
        } else {
            DriversTable.insert {
                it[id]             = uuid
                it[userId]         = driver.userId
                it[firstName]      = driver.firstName
                it[lastName]       = driver.lastName
                it[email]          = driver.email
                it[phone]          = driver.phone
                it[licenseNumber]  = driver.licenseNumber
                it[licenseExpiry]  = LocalDate.ofInstant(driver.licenseExpiry, ZoneOffset.UTC)
                it[licenseClass]   = driver.licenseClass
                it[address]        = driver.address
                it[city]           = driver.city
                it[state]          = driver.state
                it[postalCode]     = driver.postalCode
                it[country]        = driver.country
                it[isActive]       = driver.isActive
                it[createdAt]      = now
                it[updatedAt]      = now
            }
        }
        driver
    }

    override suspend fun deleteById(id: DriverId): Boolean = dbQuery {
        DriversTable.deleteWhere { DriversTable.id eq UUID.fromString(id.value) } > 0
    }

    // ── Assignment operations ───────────────────────────────────────────────

    override suspend fun assignToVehicle(
        driverId: DriverId,
        vehicleId: String,
        notes: String?,
    ): VehicleDriverAssignment = dbQuery {
        val now = Instant.now()
        val newId = UUID.randomUUID()
        VehicleDriverAssignmentsTable.insert {
            it[VehicleDriverAssignmentsTable.id]        = newId
            it[VehicleDriverAssignmentsTable.vehicleId] = UUID.fromString(vehicleId)
            it[VehicleDriverAssignmentsTable.driverId]  = UUID.fromString(driverId.value)
            it[VehicleDriverAssignmentsTable.assignedAt] = now
            it[VehicleDriverAssignmentsTable.releasedAt] = null
            it[VehicleDriverAssignmentsTable.notes]      = notes
        }
        VehicleDriverAssignment(
            id         = newId.toString(),
            vehicleId  = vehicleId,
            driverId   = driverId.value,
            assignedAt = now,
            releasedAt = null,
            notes      = notes,
        )
    }

    override suspend fun releaseFromVehicle(driverId: DriverId): VehicleDriverAssignment? = dbQuery {
        val driverUuid = UUID.fromString(driverId.value)
        val now = Instant.now()
        val row = VehicleDriverAssignmentsTable.selectAll()
            .where {
                (VehicleDriverAssignmentsTable.driverId eq driverUuid) and
                (VehicleDriverAssignmentsTable.releasedAt.isNull())
            }
            .singleOrNull() ?: return@dbQuery null

        VehicleDriverAssignmentsTable.update({
            (VehicleDriverAssignmentsTable.driverId eq driverUuid) and
            (VehicleDriverAssignmentsTable.releasedAt.isNull())
        }) {
            it[VehicleDriverAssignmentsTable.releasedAt] = now
        }
        row.toAssignment().copy(releasedAt = now)
    }

    override suspend fun findActiveAssignmentByVehicle(vehicleId: String): VehicleDriverAssignment? = dbQuery {
        VehicleDriverAssignmentsTable.selectAll()
            .where {
                (VehicleDriverAssignmentsTable.vehicleId eq UUID.fromString(vehicleId)) and
                (VehicleDriverAssignmentsTable.releasedAt.isNull())
            }
            .map { it.toAssignment() }
            .singleOrNull()
    }

    override suspend fun findActiveAssignmentByDriver(driverId: DriverId): VehicleDriverAssignment? = dbQuery {
        VehicleDriverAssignmentsTable.selectAll()
            .where {
                (VehicleDriverAssignmentsTable.driverId eq UUID.fromString(driverId.value)) and
                (VehicleDriverAssignmentsTable.releasedAt.isNull())
            }
            .map { it.toAssignment() }
            .singleOrNull()
    }

    override suspend fun findAssignmentHistoryByVehicle(vehicleId: String): List<VehicleDriverAssignment> = dbQuery {
        VehicleDriverAssignmentsTable.selectAll()
            .where { VehicleDriverAssignmentsTable.vehicleId eq UUID.fromString(vehicleId) }
            .orderBy(VehicleDriverAssignmentsTable.assignedAt, SortOrder.DESC)
            .map { it.toAssignment() }
    }

    override suspend fun findAssignmentHistoryByDriver(driverId: DriverId): List<VehicleDriverAssignment> = dbQuery {
        VehicleDriverAssignmentsTable.selectAll()
            .where { VehicleDriverAssignmentsTable.driverId eq UUID.fromString(driverId.value) }
            .orderBy(VehicleDriverAssignmentsTable.assignedAt, SortOrder.DESC)
            .map { it.toAssignment() }
    }

    // ── Shift operations ────────────────────────────────────────────────────

    override suspend fun findActiveShift(driverId: String): DriverShift? = dbQuery {
        DriverShiftsTable.selectAll()
            .where { (DriverShiftsTable.driverId eq UUID.fromString(driverId)) and (DriverShiftsTable.endedAt.isNull()) }
            .map { it.toDriverShift() }
            .singleOrNull()
    }

    override suspend fun startShift(driverId: String, vehicleId: String, notes: String?): DriverShift = dbQuery {
        val active = findActiveShift(driverId)
        if (active != null) throw IllegalStateException("Driver already has an active shift")

        val id = UUID.randomUUID()
        val now = Instant.now()
        val driverUuid = UUID.fromString(driverId)
        val vehicleUuid = UUID.fromString(vehicleId)
        DriverShiftsTable.insert {
            it[DriverShiftsTable.id] = id
            it[DriverShiftsTable.driverId] = driverUuid
            it[DriverShiftsTable.vehicleId] = vehicleUuid
            it[DriverShiftsTable.startedAt] = now
            it[DriverShiftsTable.notes] = notes
        }
        DriverShift(id, driverUuid, vehicleUuid, now, null, notes)
    }

    override suspend fun endShift(driverId: String, notes: String?): DriverShift? = dbQuery {
        val active = findActiveShift(driverId) ?: return@dbQuery null
        val now = Instant.now()
        DriverShiftsTable.update({ DriverShiftsTable.id eq active.id }) {
            it[endedAt] = now
            if (notes != null) it[DriverShiftsTable.notes] = notes
        }
        active.copy(endedAt = now, notes = notes ?: active.notes)
    }

    override suspend fun findShiftHistory(driverId: String): List<DriverShift> = dbQuery {
        DriverShiftsTable.selectAll()
            .where { DriverShiftsTable.driverId eq UUID.fromString(driverId) }
            .orderBy(DriverShiftsTable.startedAt, SortOrder.DESC)
            .map { it.toDriverShift() }
    }
}
