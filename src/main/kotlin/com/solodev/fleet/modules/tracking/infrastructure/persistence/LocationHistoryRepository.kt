package com.solodev.fleet.modules.tracking.infrastructure.persistence

import com.solodev.fleet.modules.tracking.application.dto.VehicleRouteState
import com.solodev.fleet.modules.tracking.application.dto.VehicleStatus
import com.solodev.fleet.shared.helpers.dbQuery
import java.time.Instant
import java.util.*
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.ResultRow

/**
 * LocationHistoryTable - Persists vehicle tracking records for historical analysis
 * Stores all location updates, snapshots, and state changes for audit trail
 */
object LocationHistoryTable : Table("location_history") {
    val id = uuid("id")
    val vehicleId = varchar("vehicle_id", 36)  // UUID as string for flexibility
    val routeId = varchar("route_id", 36).nullable()
    val progress = double("progress")  // 0.0-1.0 along route
    val segmentId = varchar("segment_id", 50).nullable()
    val speed = double("speed")  // m/s
    val heading = double("heading")  // 0-360 degrees
    val status = varchar("status", 20)  // IN_TRANSIT, IDLE, OFF_ROUTE
    val distanceFromRoute = double("distance_from_route")  // meters
    val latitude = double("latitude")
    val longitude = double("longitude")
    val timestamp = timestamp("timestamp")
    val createdAt = timestamp("created_at").default(Instant.now())

    override val primaryKey = PrimaryKey(id)
    init {
        index(false, vehicleId, timestamp)  // For querying history by vehicle
    }
}

/**
 * LocationHistoryRepository - Persists and retrieves location tracking records
 */
class LocationHistoryRepository {

    /**
     * Save a tracking record to the history table
     */
    suspend fun saveTrackingRecord(state: VehicleRouteState): UUID = dbQuery {
        val id = UUID.randomUUID()
        LocationHistoryTable.insert {
            it[LocationHistoryTable.id] = id
            it[LocationHistoryTable.vehicleId] = state.vehicleId
            it[LocationHistoryTable.routeId] = state.routeId
            it[LocationHistoryTable.progress] = state.progress
            it[LocationHistoryTable.segmentId] = state.segmentId
            it[LocationHistoryTable.speed] = state.speed
            it[LocationHistoryTable.heading] = state.heading
            it[LocationHistoryTable.status] = state.status.name
            it[LocationHistoryTable.distanceFromRoute] = state.distanceFromRoute
            it[LocationHistoryTable.latitude] = state.latitude
            it[LocationHistoryTable.longitude] = state.longitude
            it[LocationHistoryTable.timestamp] = state.timestamp
        }
        id
    }

    /**
     * Get tracking history for a vehicle with pagination
     */
    suspend fun getVehicleHistory(
        vehicleId: String,
        limit: Int = 100,
        offset: Int = 0
    ): List<VehicleRouteState> = dbQuery {
        LocationHistoryTable
            .selectAll()
            .where { LocationHistoryTable.vehicleId eq vehicleId }
            .orderBy(LocationHistoryTable.timestamp, SortOrder.DESC)  // Most recent first
            .limit(limit, offset.toLong())
            .map { row: ResultRow ->
                VehicleRouteState(
                    vehicleId = row[LocationHistoryTable.vehicleId],
                    routeId = row[LocationHistoryTable.routeId] ?: "",
                    progress = row[LocationHistoryTable.progress],
                    segmentId = row[LocationHistoryTable.segmentId] ?: "",
                    speed = row[LocationHistoryTable.speed],
                    heading = row[LocationHistoryTable.heading],
                    status = VehicleStatus.valueOf(row[LocationHistoryTable.status]),
                    distanceFromRoute = row[LocationHistoryTable.distanceFromRoute],
                    latitude = row[LocationHistoryTable.latitude],
                    longitude = row[LocationHistoryTable.longitude],
                    timestamp = row[LocationHistoryTable.timestamp]
                )
            }
    }

    /**
     * Get total count of tracking records for a vehicle
     */
    suspend fun getVehicleHistoryCount(vehicleId: String): Long = dbQuery {
        LocationHistoryTable
            .selectAll()
            .where { LocationHistoryTable.vehicleId eq vehicleId }
            .count()
    }

    /**
     * Delete old tracking records (for maintenance/cleanup)
     */
    suspend fun deleteOlderThan(before: Instant): Int = dbQuery {
        LocationHistoryTable.deleteWhere { LocationHistoryTable.timestamp less before }
    }
}

