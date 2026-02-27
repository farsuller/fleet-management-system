package com.solodev.fleet.shared.infrastructure.persistence

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.Function
import org.postgis.PGgeometry

/** Custom Exposed functions for PostGIS spatial operations. */
object SpatialFunctions {

    /** ST_Distance: Returns the 2D Cartesian distance between two geometries. */
    fun distance(geom1: Expression<*>, geom2: Expression<*>): Function<Double> =
            CustomFunction("ST_Distance", DoubleColumnType(), geom1, geom2)

    /**
     * ST_LineLocatePoint: Returns a float between 0 and 1 representing the location of the closest
     * point on LineString to the given Point.
     */
    fun lineLocatePoint(line: Expression<*>, point: Expression<*>): Function<Double> =
            CustomFunction("ST_LineLocatePoint", DoubleColumnType(), line, point)

    /** ST_LineInterpolatePoint: Returns a point interpolated along a line. */
    fun lineInterpolatePoint(
            line: Expression<*>,
            fraction: Expression<Double>
    ): Function<PGgeometry> =
            CustomFunction("ST_LineInterpolatePoint", PostGISColumnType(), line, fraction)

    /** ST_Contains: Returns true if geometry A contains geometry B. */
    fun contains(geomA: Expression<*>, geomB: Expression<*>): Function<Boolean> =
            CustomFunction("ST_Contains", BooleanColumnType(), geomA, geomB)
}
