package com.solodev.fleet.shared.infrastructure.persistence

import java.sql.ResultSet
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ColumnType
import org.jetbrains.exposed.sql.Table
import org.postgis.PGgeometry
import org.postgresql.util.PGobject

/** Custom [ColumnType] for PostGIS geometry columns. */
class PostGISColumnType(val srid: Int = 4326) : ColumnType<PGgeometry>() {
    override fun sqlType(): String = "GEOMETRY"

    override fun valueFromDB(value: Any): PGgeometry? =
            when (value) {
                is PGgeometry -> value
                is PGobject -> PGgeometry(value.value)
                is String -> PGgeometry(value)
                else -> null
            }

    override fun notNullValueToDB(value: PGgeometry): Any {
        if (value.geometry.srid == 0) value.geometry.srid = srid
        return value
    }

    override fun readObject(rs: ResultSet, index: Int): Any? {
        return rs.getObject(index)
    }
}

/** Extension for Exposed Table to support geometry columns. */
fun Table.geometry(name: String, srid: Int = 4326): Column<PGgeometry> =
        registerColumn(name, PostGISColumnType(srid))
