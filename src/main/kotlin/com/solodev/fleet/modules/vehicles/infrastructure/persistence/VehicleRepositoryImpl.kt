package com.solodev.fleet.modules.vehicles.infrastructure.persistence

import com.solodev.fleet.modules.vehicles.domain.model.Vehicle
import com.solodev.fleet.modules.vehicles.domain.model.VehicleId
import com.solodev.fleet.modules.vehicles.domain.model.VehicleState
import com.solodev.fleet.modules.vehicles.domain.repository.VehicleRepository
import com.solodev.fleet.shared.domain.model.Location
import com.solodev.fleet.shared.exceptions.ConflictException
import com.solodev.fleet.shared.helpers.dbQuery
import com.solodev.fleet.shared.infrastructure.cache.RedisCacheManager
import com.solodev.fleet.shared.models.PaginationParams
import java.time.Instant
import java.util.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.postgis.PGgeometry
import org.postgis.Point

/**
 * PostgreSQL implementation of VehicleRepository using Exposed ORM.
 *
 * This is the infrastructure layer adapter that implements the domain port.
 */
class VehicleRepositoryImpl(private val cacheManager: RedisCacheManager? = null) :
        VehicleRepository {

    /** Execute database operations in a suspended transaction. */

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
                    dailyRateAmount = this[VehiclesTable.dailyRate],
                    currencyCode = this[VehiclesTable.currencyCode],
                    passengerCapacity = this[VehiclesTable.passengerCapacity],
                    lastLocation = this[VehiclesTable.lastLocation]?.toLocation(),
                    routeProgress = this[VehiclesTable.routeProgress],
                    bearing = this[VehiclesTable.bearing],
                    version = this[VehiclesTable.version]
            )

    private fun PGgeometry.toLocation(): Location {
        val point = this.geometry as Point
        return Location(point.y, point.x)
    }

    private fun Location.toPGgeometry(): PGgeometry {
        return PGgeometry("SRID=4326;POINT($longitude $latitude)")
    }

    override suspend fun findById(id: VehicleId): Vehicle? {
        // If caching is enabled, use Cache-Aside pattern
        return cacheManager?.getOrSet(
                key = "vehicle:${id.value}",
                ttlSeconds = 300 // 5 minutes
        ) {
            // Fallback to database if cache miss
            dbQuery {
                VehiclesTable.selectAll()
                        .where { VehiclesTable.id eq UUID.fromString(id.value) }
                        .singleOrNull()
                        ?.toVehicle()
            }
        }
                ?: dbQuery {
                    // No caching - direct DB query
                    VehiclesTable.selectAll()
                            .where { VehiclesTable.id eq UUID.fromString(id.value) }
                            .singleOrNull()
                            ?.toVehicle()
                }
    }

    override suspend fun findByPlateNumber(plateNumber: String): Vehicle? = dbQuery {
        VehiclesTable.selectAll()
                .where { VehiclesTable.plateNumber eq plateNumber }
                .map { it.toVehicle() }
                .singleOrNull()
    }

    override suspend fun save(vehicle: Vehicle): Vehicle = dbQuery {
        val vehicleUuid = UUID.fromString(vehicle.id.value)
        val now = Instant.now()

        // Check if vehicle exists
        val exists = VehiclesTable.selectAll().where { VehiclesTable.id eq vehicleUuid }.count() > 0

        if (exists) {
            // Update existing vehicle with optimistic locking check
            val updatedCount =
                    VehiclesTable.update({
                        (VehiclesTable.id eq vehicleUuid) and
                                (VehiclesTable.version eq vehicle.version)
                    }) {
                        it[vin] = vehicle.vin
                        it[plateNumber] = vehicle.licensePlate
                        it[make] = vehicle.make
                        it[model] = vehicle.model
                        it[year] = vehicle.year
                        it[color] = vehicle.color
                        it[status] = vehicle.state.name
                        it[currentOdometerKm] = vehicle.mileageKm
                        it[dailyRate] = vehicle.dailyRateAmount
                        it[currencyCode] = vehicle.currencyCode
                        it[passengerCapacity] = vehicle.passengerCapacity
                        it[lastLocation] = vehicle.lastLocation?.toPGgeometry()
                        it[routeProgress] = vehicle.routeProgress
                        it[bearing] = vehicle.bearing
                        it[updatedAt] = now
                        // version is incremented by DB trigger
                    }

            if (updatedCount == 0) {
                throw ConflictException(
                        "VEHICLE_OPTIMISTIC_LOCK_FAILURE",
                        "Vehicle has been modified by another process. Please refresh and try again."
                )
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
                it[dailyRate] = vehicle.dailyRateAmount
                it[currencyCode] = vehicle.currencyCode
                it[passengerCapacity] = vehicle.passengerCapacity
                it[lastLocation] = vehicle.lastLocation?.toPGgeometry()
                it[routeProgress] = vehicle.routeProgress
                it[bearing] = vehicle.bearing
                it[createdAt] = now
                it[updatedAt] = now
                it[version] = 0
            }
        }

        vehicle
    }

    override suspend fun findAll(params: PaginationParams): Pair<List<Vehicle>, Long> = dbQuery {
        val totalCount = VehiclesTable.selectAll().count()

        var query = VehiclesTable.selectAll()

        params.cursor?.let { lastId ->
            query = query.where { VehiclesTable.id greater UUID.fromString(lastId) }
        }

        val items =
                query.orderBy(VehiclesTable.id to SortOrder.ASC).limit(params.limit).map {
                    it.toVehicle()
                }

        Pair(items, totalCount)
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
        OdometerReadingsTable.selectAll()
                .where { OdometerReadingsTable.vehicleId eq UUID.fromString(vehicleId.value) }
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
