package com.solodev.fleet.modules.vehicles.infrastructure.persistence

import com.solodev.fleet.modules.vehicles.domain.model.Bus
import com.solodev.fleet.modules.vehicles.domain.model.Vehicle
import com.solodev.fleet.modules.vehicles.domain.model.VehicleId
import com.solodev.fleet.modules.vehicles.domain.model.VehicleState
import com.solodev.fleet.modules.vehicles.domain.model.VehicleType
import com.solodev.fleet.modules.vehicles.domain.repository.BusRepository
import com.solodev.fleet.modules.vehicles.domain.repository.VehicleRepository
import com.solodev.fleet.shared.domain.model.Location
import com.solodev.fleet.shared.helpers.dbQuery
import com.solodev.fleet.shared.models.PaginationParams
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greater
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.postgis.PGgeometry
import org.postgis.Point
import java.time.Instant
import java.util.UUID

class BusRepositoryImpl(
    private val vehicleRepository: VehicleRepository,
) : BusRepository {
    private fun PGgeometry.toLocation(): Location {
        val point = this.geometry as Point
        return Location(point.y, point.x)
    }

    private fun ResultRow.toVehicle(): Vehicle =
        Vehicle(
            id = VehicleId(this[VehiclesTable.id].value.toString()),
            vin = this[VehiclesTable.vin],
            licensePlate = this[VehiclesTable.plateNumber],
            make = this[VehiclesTable.make],
            model = this[VehiclesTable.model],
            year = this[VehiclesTable.year],
            color = this[VehiclesTable.color],
            vehicleType = VehicleType.fromName(this[VehiclesTable.vehicleType]),
            state = VehicleState.fromName(this[VehiclesTable.status]),
            mileageKm = this[VehiclesTable.currentOdometerKm],
            dailyRateAmount = this[VehiclesTable.dailyRate],
            currencyCode = this[VehiclesTable.currencyCode],
            passengerCapacity = this[VehiclesTable.passengerCapacity],
            lastLocation = this[VehiclesTable.lastLocation]?.toLocation(),
            routeProgress = this[VehiclesTable.routeProgress],
            bearing = this[VehiclesTable.bearing],
            lastServiceMileage = this[VehiclesTable.lastServiceMileage],
            nextServiceMileage = this[VehiclesTable.nextServiceMileage],
            version = this[VehiclesTable.version],
        )

    private fun ResultRow.toBus(): Bus =
        Bus(
            vehicle = toVehicle(),
            routeNumber = this[BusesTable.routeNumber],
            doorCount = this[BusesTable.doorCount],
            standingCapacity = this[BusesTable.standingCapacity],
            hasAccessibilityRamp = this[BusesTable.hasAccessibilityRamp],
            hasAirConditioning = this[BusesTable.hasAirConditioning],
        )

    override suspend fun findById(id: String): Bus? =
        dbQuery {
            val vehicleUuid = UUID.fromString(id)
            BusesTable
                .innerJoin(VehiclesTable)
                .selectAll()
                .where { BusesTable.vehicleId eq vehicleUuid }
                .singleOrNull()
                ?.toBus()
        }

    override suspend fun findAll(params: PaginationParams): Pair<List<Bus>, Long> =
        dbQuery {
            var baseQuery =
                BusesTable
                    .innerJoin(VehiclesTable)
                    .selectAll()

            params.filters["state"]?.let { stateValue ->
                baseQuery = baseQuery.where { VehiclesTable.status eq stateValue }
            }

            val totalCount = baseQuery.count()

            var query = baseQuery
            params.cursor?.let { lastId ->
                query.andWhere { BusesTable.vehicleId greater UUID.fromString(lastId) }
            }

            val items =
                query
                    .orderBy(BusesTable.vehicleId to SortOrder.ASC)
                    .limit(params.limit)
                    .map { it.toBus() }

            Pair(items, totalCount)
        }

    override suspend fun save(bus: Bus): Bus =
        dbQuery {
            val now = Instant.now()
            val savedVehicle = vehicleRepository.save(bus.vehicle)
            val vehicleUuid = UUID.fromString(savedVehicle.id.value)

            val exists = BusesTable.selectAll().where { BusesTable.vehicleId eq vehicleUuid }.count() > 0
            if (exists) {
                BusesTable.update({ BusesTable.vehicleId eq vehicleUuid }) {
                    it[routeNumber] = bus.routeNumber
                    it[doorCount] = bus.doorCount
                    it[standingCapacity] = bus.standingCapacity
                    it[hasAccessibilityRamp] = bus.hasAccessibilityRamp
                    it[hasAirConditioning] = bus.hasAirConditioning
                    it[updatedAt] = now
                }
            } else {
                BusesTable.insert {
                    it[vehicleId] = vehicleUuid
                    it[routeNumber] = bus.routeNumber
                    it[doorCount] = bus.doorCount
                    it[standingCapacity] = bus.standingCapacity
                    it[hasAccessibilityRamp] = bus.hasAccessibilityRamp
                    it[hasAirConditioning] = bus.hasAirConditioning
                    it[createdAt] = now
                    it[updatedAt] = now
                }
            }

            bus.copy(vehicle = savedVehicle)
        }

    override suspend fun deleteById(id: String): Boolean = vehicleRepository.deleteById(VehicleId(id))
}
