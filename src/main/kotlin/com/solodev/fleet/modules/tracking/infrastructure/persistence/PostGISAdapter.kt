package com.solodev.fleet.modules.tracking.infrastructure.persistence

import com.solodev.fleet.shared.domain.model.Location
import com.solodev.fleet.shared.infrastructure.persistence.SpatialFunctions
import java.util.UUID
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.postgis.PGgeometry
import org.postgis.Point

/** Adapter for PostGIS spatial operations. */
open class PostGISAdapter {

    /**
     * Snaps a raw location to the nearest point on a given route. Returns a pair of (Snapped
     * Location, Progress [0-1]).
     */
    open fun snapToRoute(location: Location, routeId: UUID): Pair<Location, Double>? = transaction {
        val pointWkt = "SRID=4326;POINT(${location.longitude} ${location.latitude})"
        val geomPoint = PGgeometry(pointWkt)

        val route =
                RoutesTable.select(RoutesTable.polyline)
                        .where { RoutesTable.id eq routeId }
                        .singleOrNull()
                        ?: return@transaction null

        val polyline = route[RoutesTable.polyline]

        // Calculate progress along the line (0.0 to 1.0)
        val progress =
                RoutesTable.select(
                                SpatialFunctions.lineLocatePoint(
                                        polyline_expr(polyline),
                                        point_expr(geomPoint)
                                )
                        )
                        .where { RoutesTable.id eq routeId }
                        .single()[
                        SpatialFunctions.lineLocatePoint(
                                polyline_expr(polyline),
                                point_expr(geomPoint)
                        )]

        // Interpolate the point at that progress to get the snapped coordinate
        val snappedGeom =
                RoutesTable.select(
                                SpatialFunctions.lineInterpolatePoint(
                                        polyline_expr(polyline),
                                        progress_expr(progress)
                                )
                        )
                        .where { RoutesTable.id eq routeId }
                        .single()[
                        SpatialFunctions.lineInterpolatePoint(
                                polyline_expr(polyline),
                                progress_expr(progress)
                        )]

        val snappedPoint = snappedGeom.geometry as Point
        Pair(Location(snappedPoint.y, snappedPoint.x), progress)
    }

    /** Retrieves all routes from the database. */
    fun findAllRoutes(): List<com.solodev.fleet.modules.tracking.application.dto.RouteDTO> =
            transaction {
                RoutesTable.selectAll().map {
                    com.solodev.fleet.modules.tracking.application.dto.RouteDTO(
                            id = it[RoutesTable.id].value.toString(),
                            name = it[RoutesTable.name],
                            description = it[RoutesTable.description]
                    )
                }
            }

    /** Checks if a location is inside any geofence of a specific type. */
    open fun isInsideGeofence(location: Location, type: String): Boolean = transaction {
        val pointWkt = "SRID=4326;POINT(${location.longitude} ${location.latitude})"
        val geomPoint = PGgeometry(pointWkt)

        GeofencesTable.select(GeofencesTable.id)
                .where {
                    (GeofencesTable.type eq type) and
                            SpatialFunctions.contains(
                                    GeofencesTable.boundary,
                                    point_expr(geomPoint)
                            )
                }
                .any()
    }

    // Helper functions for Exposed expressions
    private fun point_expr(point: PGgeometry): Expression<PGgeometry> =
            QueryParameter(
                    point,
                    com.solodev.fleet.shared.infrastructure.persistence.PostGISColumnType()
            )
    private fun polyline_expr(polyline: PGgeometry): Expression<PGgeometry> =
            QueryParameter(
                    polyline,
                    com.solodev.fleet.shared.infrastructure.persistence.PostGISColumnType()
            )
    private fun progress_expr(progress: Double): Expression<Double> =
            QueryParameter(progress, DoubleColumnType())
}
