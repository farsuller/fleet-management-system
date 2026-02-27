package com.solodev.fleet.modules.tracking.infrastructure.persistence

import com.solodev.fleet.shared.infrastructure.persistence.geometry
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.javatime.timestamp

/** Exposed table definition for routes (Digital Rails). */
object RoutesTable : UUIDTable("routes") {
    val name = varchar("name", 255)
    val description = text("description").nullable()
    val polyline = geometry("polyline", 4326) // LineString
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
}

/** Exposed table definition for geofences. */
object GeofencesTable : UUIDTable("geofences") {
    val name = varchar("name", 255)
    val type = varchar("type", 50) // e.g., 'DEPOT', 'RESTRICTED'
    val boundary = geometry("boundary", 4326) // Polygon
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
}
