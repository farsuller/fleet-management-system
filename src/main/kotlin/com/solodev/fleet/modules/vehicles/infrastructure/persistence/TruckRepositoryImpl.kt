package com.solodev.fleet.modules.vehicles.infrastructure.persistence

import com.solodev.fleet.modules.vehicles.domain.model.Truck
import com.solodev.fleet.modules.vehicles.domain.model.Vehicle
import com.solodev.fleet.modules.vehicles.domain.model.VehicleId
import com.solodev.fleet.modules.vehicles.domain.model.VehicleState
import com.solodev.fleet.modules.vehicles.domain.model.VehicleType
import com.solodev.fleet.modules.vehicles.domain.repository.TruckRepository
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
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class TruckRepositoryImpl(
    private val vehicleRepository: VehicleRepository,
) : TruckRepository {
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

    private fun ResultRow.toTruck(): Truck =
        Truck(
            vehicle = toVehicle(),
            payloadCapacityTons = this[TrucksTable.payloadCapacityTons]?.toDouble(),
            cargoType = this[TrucksTable.cargoType],
            axleCount = this[TrucksTable.axleCount],
            grossVehicleWeightKg = this[TrucksTable.grossVehicleWeightKg],
            hasTrailerHitch = this[TrucksTable.hasTrailerHitch],
        )

    override suspend fun findById(id: String): Truck? =
        dbQuery {
            val vehicleUuid = UUID.fromString(id)
            TrucksTable
                .innerJoin(VehiclesTable)
                .selectAll()
                .where { TrucksTable.vehicleId eq vehicleUuid }
                .singleOrNull()
                ?.toTruck()
        }

    override suspend fun findAll(params: PaginationParams): Pair<List<Truck>, Long> =
        dbQuery {
            var baseQuery =
                TrucksTable
                    .innerJoin(VehiclesTable)
                    .selectAll()

            params.filters["state"]?.let { stateValue ->
                baseQuery = baseQuery.where { VehiclesTable.status eq stateValue }
            }

            val totalCount = baseQuery.count()

            var query = baseQuery
            params.cursor?.let { lastId ->
                query.andWhere { TrucksTable.vehicleId greater UUID.fromString(lastId) }
            }

            val items =
                query
                    .orderBy(TrucksTable.vehicleId to SortOrder.ASC)
                    .limit(params.limit)
                    .map { it.toTruck() }

            Pair(items, totalCount)
        }

    override suspend fun save(truck: Truck): Truck =
        dbQuery {
            val now = Instant.now()
            val savedVehicle = vehicleRepository.save(truck.vehicle)
            val vehicleUuid = UUID.fromString(savedVehicle.id.value)

            val exists = TrucksTable.selectAll().where { TrucksTable.vehicleId eq vehicleUuid }.count() > 0
            if (exists) {
                TrucksTable.update({ TrucksTable.vehicleId eq vehicleUuid }) {
                    it[payloadCapacityTons] = truck.payloadCapacityTons?.let { value -> BigDecimal.valueOf(value) }
                    it[cargoType] = truck.cargoType
                    it[axleCount] = truck.axleCount
                    it[grossVehicleWeightKg] = truck.grossVehicleWeightKg
                    it[hasTrailerHitch] = truck.hasTrailerHitch
                    it[updatedAt] = now
                }
            } else {
                TrucksTable.insert {
                    it[vehicleId] = vehicleUuid
                    it[payloadCapacityTons] = truck.payloadCapacityTons?.let { value -> BigDecimal.valueOf(value) }
                    it[cargoType] = truck.cargoType
                    it[axleCount] = truck.axleCount
                    it[grossVehicleWeightKg] = truck.grossVehicleWeightKg
                    it[hasTrailerHitch] = truck.hasTrailerHitch
                    it[createdAt] = now
                    it[updatedAt] = now
                }
            }

            truck.copy(vehicle = savedVehicle)
        }

    override suspend fun deleteById(id: String): Boolean = vehicleRepository.deleteById(VehicleId(id))
}
