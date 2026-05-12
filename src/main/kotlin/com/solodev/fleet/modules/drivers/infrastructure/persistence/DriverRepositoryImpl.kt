package com.solodev.fleet.modules.drivers.infrastructure.persistence

import com.solodev.fleet.modules.drivers.domain.model.Driver
import com.solodev.fleet.modules.drivers.domain.model.DriverId
import com.solodev.fleet.modules.drivers.domain.model.DriverShift
import com.solodev.fleet.modules.drivers.domain.model.VehicleDriverAssignment
import com.solodev.fleet.modules.drivers.domain.repository.DriverRepository
import com.solodev.fleet.shared.helpers.dbQuery
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNull
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

class DriverRepositoryImpl : DriverRepository {
    private fun ResultRow.toDriver() =
        Driver(
            id = DriverId(this[DriversTable.id].value.toString()),
            userId = this[DriversTable.userId],
            firstName = this[DriversTable.firstName],
            lastName = this[DriversTable.lastName],
            email = this[DriversTable.email],
            phone = this[DriversTable.phone],
            licenseNumber = this[DriversTable.licenseNumber],
            licenseExpiry = this[DriversTable.licenseExpiry]?.atStartOfDay()?.toInstant(ZoneOffset.UTC),
            licenseClass = this[DriversTable.licenseClass],
            address = this[DriversTable.address],
            city = this[DriversTable.city],
            province = this[DriversTable.province],
            postalCode = this[DriversTable.postalCode],
            country = this[DriversTable.country],
            status = this[DriversTable.status],
            availabilityStatus = this[DriversTable.availabilityStatus],
            createdAt = this[DriversTable.createdAt],
        )

    override suspend fun findById(id: DriverId): Driver? =
        dbQuery {
            DriversTable
                .selectAll()
                .where { DriversTable.id eq UUID.fromString(id.value) }
                .map { it.toDriver() }
                .singleOrNull()
        }

    override suspend fun findByEmail(email: String): Driver? =
        dbQuery {
            DriversTable
                .selectAll()
                .where { DriversTable.email eq email }
                .map { it.toDriver() }
                .singleOrNull()
        }

    override suspend fun findByLicenseNumber(licenseNumber: String): Driver? =
        dbQuery {
            DriversTable
                .selectAll()
                .where { DriversTable.licenseNumber eq licenseNumber }
                .map { it.toDriver() }
                .singleOrNull()
        }

    override suspend fun findAll(): List<Driver> =
        dbQuery {
            DriversTable.selectAll().map { it.toDriver() }
        }

    override suspend fun findPendingDrivers(): List<Driver> =
        dbQuery {
            DriversTable
                .selectAll()
                .where { DriversTable.status eq com.solodev.fleet.modules.drivers.domain.model.DriverStatus.PENDING }
                .map { it.toDriver() }
        }

    override suspend fun save(driver: Driver): Driver =
        dbQuery {
            val uuid = UUID.fromString(driver.id.value)
            val now = Instant.now()
            val exists =
                DriversTable.selectAll().where { DriversTable.id eq uuid }.count() > 0

            if (exists) {
                DriversTable.update({ DriversTable.id eq uuid }) {
                    it[userId] = driver.userId
                    it[firstName] = driver.firstName
                    it[lastName] = driver.lastName
                    it[email] = driver.email
                    it[phone] = driver.phone
                    it[licenseNumber] = driver.licenseNumber
                    it[licenseExpiry] = driver.licenseExpiry?.let { LocalDate.ofInstant(it, ZoneOffset.UTC) }
                    it[licenseClass] = driver.licenseClass
                    it[address] = driver.address
                    it[city] = driver.city
                    it[province] = driver.province
                    it[postalCode] = driver.postalCode
                    it[country] = driver.country
                    it[status] = driver.status
                    it[availabilityStatus] = driver.availabilityStatus
                    it[updatedAt] = now
                }
            } else {
                DriversTable.insert {
                    it[id] = uuid
                    it[userId] = driver.userId
                    it[firstName] = driver.firstName
                    it[lastName] = driver.lastName
                    it[email] = driver.email
                    it[phone] = driver.phone
                    it[licenseNumber] = driver.licenseNumber
                    it[licenseExpiry] = driver.licenseExpiry?.let { LocalDate.ofInstant(it, ZoneOffset.UTC) }
                    it[licenseClass] = driver.licenseClass
                    it[address] = driver.address
                    it[city] = driver.city
                    it[province] = driver.province
                    it[postalCode] = driver.postalCode
                    it[country] = driver.country
                    it[status] = driver.status
                    it[availabilityStatus] = driver.availabilityStatus
                    it[createdAt] = now
                    it[updatedAt] = now
                }
            }
            driver
        }

    override suspend fun deleteById(id: DriverId): Boolean =
        dbQuery {
            DriversTable.deleteWhere { DriversTable.id eq UUID.fromString(id.value) } > 0
        }

    override suspend fun assignToVehicle(
        driverId: DriverId,
        vehicleId: String,
        notes: String?,
    ): VehicleDriverAssignment =
        dbQuery {
            val now = Instant.now()
            val assignmentUuid = UUID.randomUUID()

            // Release any active assignment for this vehicle
            VehicleDriverAssignmentsTable.update({
                (VehicleDriverAssignmentsTable.vehicleId eq UUID.fromString(vehicleId)) and
                    (VehicleDriverAssignmentsTable.releasedAt.isNull())
            }) {
                it[releasedAt] = now
            }

            // Release any active assignment for this driver
            VehicleDriverAssignmentsTable.update({
                (VehicleDriverAssignmentsTable.driverId eq UUID.fromString(driverId.value)) and
                    (VehicleDriverAssignmentsTable.releasedAt.isNull())
            }) {
                it[releasedAt] = now
            }

            VehicleDriverAssignmentsTable.insert {
                it[id] = assignmentUuid
                it[VehicleDriverAssignmentsTable.vehicleId] = UUID.fromString(vehicleId)
                it[VehicleDriverAssignmentsTable.driverId] = UUID.fromString(driverId.value)
                it[assignedAt] = now
                it[VehicleDriverAssignmentsTable.notes] = notes
            }

            VehicleDriverAssignment(
                id = assignmentUuid.toString(),
                vehicleId = vehicleId,
                driverId = driverId.value,
                assignedAt = now,
                releasedAt = null,
                notes = notes,
            )
        }

    override suspend fun releaseFromVehicle(driverId: DriverId): VehicleDriverAssignment? =
        dbQuery {
            val now = Instant.now()
            val active =
                VehicleDriverAssignmentsTable
                    .selectAll()
                    .where {
                        (VehicleDriverAssignmentsTable.driverId eq UUID.fromString(driverId.value)) and
                            (VehicleDriverAssignmentsTable.releasedAt.isNull())
                    }.singleOrNull() ?: return@dbQuery null

            VehicleDriverAssignmentsTable.update({
                (VehicleDriverAssignmentsTable.id eq active[VehicleDriverAssignmentsTable.id])
            }) {
                it[releasedAt] = now
            }

            VehicleDriverAssignment(
                id = active[VehicleDriverAssignmentsTable.id].value.toString(),
                vehicleId = active[VehicleDriverAssignmentsTable.vehicleId].toString(),
                driverId = active[VehicleDriverAssignmentsTable.driverId].toString(),
                assignedAt = active[VehicleDriverAssignmentsTable.assignedAt],
                releasedAt = now,
                notes = active[VehicleDriverAssignmentsTable.notes],
            )
        }

    override suspend fun findActiveAssignmentByVehicle(vehicleId: String): VehicleDriverAssignment? =
        dbQuery {
            VehicleDriverAssignmentsTable
                .selectAll()
                .where {
                    (VehicleDriverAssignmentsTable.vehicleId eq UUID.fromString(vehicleId)) and
                        (VehicleDriverAssignmentsTable.releasedAt.isNull())
                }.map {
                    VehicleDriverAssignment(
                        id = it[VehicleDriverAssignmentsTable.id].value.toString(),
                        vehicleId = it[VehicleDriverAssignmentsTable.vehicleId].toString(),
                        driverId = it[VehicleDriverAssignmentsTable.driverId].toString(),
                        assignedAt = it[VehicleDriverAssignmentsTable.assignedAt],
                        releasedAt = it[VehicleDriverAssignmentsTable.releasedAt],
                        notes = it[VehicleDriverAssignmentsTable.notes],
                    )
                }.singleOrNull()
        }

    override suspend fun findActiveAssignmentByDriver(driverId: DriverId): VehicleDriverAssignment? =
        dbQuery {
            VehicleDriverAssignmentsTable
                .selectAll()
                .where {
                    (VehicleDriverAssignmentsTable.driverId eq UUID.fromString(driverId.value)) and
                        (VehicleDriverAssignmentsTable.releasedAt.isNull())
                }.map {
                    VehicleDriverAssignment(
                        id = it[VehicleDriverAssignmentsTable.id].value.toString(),
                        vehicleId = it[VehicleDriverAssignmentsTable.vehicleId].toString(),
                        driverId = it[VehicleDriverAssignmentsTable.driverId].toString(),
                        assignedAt = it[VehicleDriverAssignmentsTable.assignedAt],
                        releasedAt = it[VehicleDriverAssignmentsTable.releasedAt],
                        notes = it[VehicleDriverAssignmentsTable.notes],
                    )
                }.singleOrNull()
        }

    override suspend fun findAssignmentHistoryByVehicle(vehicleId: String): List<VehicleDriverAssignment> =
        dbQuery {
            VehicleDriverAssignmentsTable
                .selectAll()
                .where { VehicleDriverAssignmentsTable.vehicleId eq UUID.fromString(vehicleId) }
                .orderBy(VehicleDriverAssignmentsTable.assignedAt to SortOrder.DESC)
                .map {
                    VehicleDriverAssignment(
                        id = it[VehicleDriverAssignmentsTable.id].value.toString(),
                        vehicleId = it[VehicleDriverAssignmentsTable.vehicleId].toString(),
                        driverId = it[VehicleDriverAssignmentsTable.driverId].toString(),
                        assignedAt = it[VehicleDriverAssignmentsTable.assignedAt],
                        releasedAt = it[VehicleDriverAssignmentsTable.releasedAt],
                        notes = it[VehicleDriverAssignmentsTable.notes],
                    )
                }
        }

    override suspend fun findAssignmentHistoryByDriver(driverId: DriverId): List<VehicleDriverAssignment> =
        dbQuery {
            VehicleDriverAssignmentsTable
                .selectAll()
                .where { VehicleDriverAssignmentsTable.driverId eq UUID.fromString(driverId.value) }
                .orderBy(VehicleDriverAssignmentsTable.assignedAt to SortOrder.DESC)
                .map {
                    VehicleDriverAssignment(
                        id = it[VehicleDriverAssignmentsTable.id].value.toString(),
                        vehicleId = it[VehicleDriverAssignmentsTable.vehicleId].toString(),
                        driverId = it[VehicleDriverAssignmentsTable.driverId].toString(),
                        assignedAt = it[VehicleDriverAssignmentsTable.assignedAt],
                        releasedAt = it[VehicleDriverAssignmentsTable.releasedAt],
                        notes = it[VehicleDriverAssignmentsTable.notes],
                    )
                }
        }

    private fun ResultRow.toDriverShift() =
        DriverShift(
            id = this[DriverShiftsTable.id],
            driverId = this[DriverShiftsTable.driverId],
            vehicleId = this[DriverShiftsTable.vehicleId],
            startedAt = this[DriverShiftsTable.startedAt],
            endedAt = this[DriverShiftsTable.endedAt],
            notes = this[DriverShiftsTable.notes],
        )

    override suspend fun findActiveShift(driverId: String): DriverShift? =
        dbQuery {
            DriverShiftsTable
                .selectAll()
                .where {
                    (DriverShiftsTable.driverId eq UUID.fromString(driverId)) and
                        (DriverShiftsTable.endedAt.isNull())
                }.map { it.toDriverShift() }
                .singleOrNull()
        }

    override suspend fun startShift(
        driverId: String,
        vehicleId: String,
        notes: String?,
    ): DriverShift =
        dbQuery {
            val id = UUID.randomUUID()
            val now = Instant.now()

            DriversTable.update({ DriversTable.id eq UUID.fromString(driverId) }) {
                it[availabilityStatus] = false
            }

            DriverShiftsTable.insert {
                it[DriverShiftsTable.id] = id
                it[DriverShiftsTable.driverId] = UUID.fromString(driverId)
                it[DriverShiftsTable.vehicleId] = UUID.fromString(vehicleId)
                it[startedAt] = now
                it[DriverShiftsTable.notes] = notes
            }

            DriverShift(
                id = id,
                driverId = UUID.fromString(driverId),
                vehicleId = UUID.fromString(vehicleId),
                startedAt = now,
                notes = notes,
            )
        }

    override suspend fun endShift(
        driverId: String,
        notes: String?,
    ): DriverShift? =
        dbQuery {
            val now = Instant.now()
            val active =
                DriverShiftsTable
                    .selectAll()
                    .where {
                        (DriverShiftsTable.driverId eq UUID.fromString(driverId)) and
                            (DriverShiftsTable.endedAt.isNull())
                    }.singleOrNull() ?: return@dbQuery null

            DriversTable.update({ DriversTable.id eq UUID.fromString(driverId) }) {
                it[availabilityStatus] = true
            }

            DriverShiftsTable.update({ DriverShiftsTable.id eq active[DriverShiftsTable.id] }) {
                it[endedAt] = now
                if (notes != null) it[DriverShiftsTable.notes] = notes
            }

            active.toDriverShift().copy(endedAt = now, notes = notes ?: active[DriverShiftsTable.notes])
        }

    override suspend fun findShiftHistory(driverId: String): List<DriverShift> =
        dbQuery {
            DriverShiftsTable
                .selectAll()
                .where { DriverShiftsTable.driverId eq UUID.fromString(driverId) }
                .orderBy(DriverShiftsTable.startedAt to SortOrder.DESC)
                .map { it.toDriverShift() }
        }
}
