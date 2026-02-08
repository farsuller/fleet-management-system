package com.solodev.fleet.modules.vehicles.infrastructure.persistence

import com.solodev.fleet.modules.vehicles.domain.model.Vehicle
import com.solodev.fleet.modules.vehicles.domain.model.VehicleId
import com.solodev.fleet.modules.vehicles.domain.model.VehicleState
import com.solodev.fleet.modules.vehicles.domain.repository.VehicleRepository
import java.time.Instant
import java.util.*
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

/**
 * PostgreSQL implementation of VehicleRepository using Exposed ORM.
 *
 * This is the infrastructure layer adapter that implements the domain port.
 */
class VehicleRepositoryImpl : VehicleRepository {

    /** Execute database operations in a suspended transaction. */
    private suspend fun <T> dbQuery(block: suspend () -> T): T =
            newSuspendedTransaction(Dispatchers.IO) { block() }

    /** Map a database result row to a Vehicle domain model. */
    private fun ResultRow.toVehicle() =
            Vehicle(
                    id = VehicleId(this[VehiclesTable.id].value.toString()),
                    vin = this[VehiclesTable.vin] ?: "",
                    licensePlate = this[VehiclesTable.plateNumber],
                    make = this[VehiclesTable.make],
                    model = this[VehiclesTable.model],
                    year = this[VehiclesTable.year],
                    color = this[VehiclesTable.color],
                    state = VehicleState.valueOf(this[VehiclesTable.status]),
                    mileageKm = this[VehiclesTable.currentOdometerKm],
                    dailyRateCents = this[VehiclesTable.dailyRateCents],
                    currencyCode = this[VehiclesTable.currencyCode],
                    passengerCapacity = this[VehiclesTable.passengerCapacity]
            )

    override suspend fun findById(id: VehicleId): Vehicle? = dbQuery {
        VehiclesTable.selectAll().where { VehiclesTable.id eq UUID.fromString(id.value) }
                .map { it.toVehicle() }
                .singleOrNull()
    }

    override suspend fun findByPlateNumber(plateNumber: String): Vehicle? = dbQuery {
        VehiclesTable.selectAll().where { VehiclesTable.plateNumber eq plateNumber }
                .map { it.toVehicle() }
                .singleOrNull()
    }

    override suspend fun save(vehicle: Vehicle): Vehicle = dbQuery {
        val vehicleUuid = UUID.fromString(vehicle.id.value)
        val now = Instant.now()

        // Check if vehicle exists
        val exists = VehiclesTable.selectAll().where { VehiclesTable.id eq vehicleUuid }.count() > 0

        if (exists) {
            // Update existing vehicle
            VehiclesTable.update({ VehiclesTable.id eq vehicleUuid }) {
                it[vin] = vehicle.vin
                it[plateNumber] = vehicle.licensePlate
                it[make] = vehicle.make
                it[model] = vehicle.model
                it[year] = vehicle.year
                it[color] = vehicle.color
                it[status] = vehicle.state.name
                it[currentOdometerKm] = vehicle.mileageKm
                it[dailyRateCents] = vehicle.dailyRateCents
                it[currencyCode] = vehicle.currencyCode
                it[passengerCapacity] = vehicle.passengerCapacity
                it[updatedAt] = now
            }
        } else {
            // Insert new vehicle
            VehiclesTable.insert {
                it[id] = vehicleUuid
                it[vin] = vehicle.vin
                it[plateNumber] = vehicle.licensePlate
                it[make] = vehicle.make
                it[model] = vehicle.model
                it[year] = vehicle.year
                it[color] = vehicle.color
                it[status] = vehicle.state.name
                it[currentOdometerKm] = vehicle.mileageKm
                it[dailyRateCents] = vehicle.dailyRateCents
                it[currencyCode] = vehicle.currencyCode
                it[passengerCapacity] = vehicle.passengerCapacity
                it[createdAt] = now
                it[updatedAt] = now
            }
        }

        vehicle
    }

    override suspend fun findAll(): List<Vehicle> = dbQuery {
        VehiclesTable.selectAll().map { it.toVehicle() }
    }

    override suspend fun deleteById(id: VehicleId): Boolean = dbQuery {
        val deletedCount =
                VehiclesTable.deleteWhere { VehiclesTable.id eq UUID.fromString(id.value) }
        deletedCount > 0
    }

    /**
     * Record an odometer reading for a vehicle. This creates a historical record in the
     * odometer_readings table.
     */
    suspend fun recordOdometerReading(
            vehicleId: VehicleId,
            readingKm: Int,
            recordedByUserId: UUID? = null,
            notes: String? = null
    ): UUID = dbQuery {
        val readingId = UUID.randomUUID()
        val now = Instant.now()

        OdometerReadingsTable.insert {
            it[id] = readingId
            it[OdometerReadingsTable.vehicleId] = UUID.fromString(vehicleId.value)
            it[OdometerReadingsTable.readingKm] = readingKm
            it[OdometerReadingsTable.recordedByUserId] = recordedByUserId
            it[recordedAt] = now
            it[OdometerReadingsTable.notes] = notes
        }

        readingId
    }

    /** Get odometer reading history for a vehicle. */
    suspend fun getOdometerHistory(vehicleId: VehicleId): List<OdometerReading> = dbQuery {
        OdometerReadingsTable.selectAll().where { OdometerReadingsTable.vehicleId eq UUID.fromString(vehicleId.value) }
                .orderBy(OdometerReadingsTable.recordedAt to SortOrder.DESC)
                .map {
                    OdometerReading(
                            id = it[OdometerReadingsTable.id].value,
                            vehicleId = vehicleId,
                            readingKm = it[OdometerReadingsTable.readingKm],
                            recordedByUserId = it[OdometerReadingsTable.recordedByUserId],
                            recordedAt = it[OdometerReadingsTable.recordedAt],
                            notes = it[OdometerReadingsTable.notes]
                    )
                }
    }
}

/** Data class representing an odometer reading record. */
data class OdometerReading(
        val id: UUID,
        val vehicleId: VehicleId,
        val readingKm: Int,
        val recordedByUserId: UUID?,
        val recordedAt: Instant,
        val notes: String?
)
