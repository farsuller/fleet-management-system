package com.solodev.fleet.modules.drivers.infrastructure.persistence

import com.solodev.fleet.modules.drivers.domain.model.DriverShift
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.SqlExpressionBuilder.plus
import java.util.UUID

object DriverShiftsTable : Table("driver_shifts") {
    val id        = uuid("id")
    val driverId  = uuid("driver_id").references(DriversTable.id)
    val vehicleId = uuid("vehicle_id")
    val startedAt = timestamp("started_at")
    val endedAt   = timestamp("ended_at").nullable()
    val notes     = text("notes").nullable()

    override val primaryKey = PrimaryKey(id)
}
