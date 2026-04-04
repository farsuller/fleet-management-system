package com.solodev.fleet.modules.tracking.infrastructure.persistence

import com.solodev.fleet.shared.domain.model.Location
import com.solodev.fleet.shared.infrastructure.persistence.PostGISColumnType
import com.solodev.fleet.shared.infrastructure.persistence.SpatialFunctions
import org.jetbrains.exposed.sql.DoubleColumnType
import org.jetbrains.exposed.sql.Expression
import org.jetbrains.exposed.sql.QueryParameter
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.transactions.transaction
import org.postgis.PGgeometry
import org.postgis.Point
import java.util.UUID

/** Adapter for PostGIS spatial operations. */
open class PostGisAdapter {
    /**
     * Snaps a raw location to the nearest point on a given route. Returns a pair of (Snapped
     * Location, Progress [0-1]).
     */
    open fun snapToRoute(
        location: Location,
        routeId: UUID,
    ): Pair<Location, Double>? =
        transaction {
            val pointWkt = "SRID=4326;POINT(${location.longitude} ${location.latitude})"
            val geomPoint = PGgeometry(pointWkt)

            val route =
                RoutesTable
                    .select(RoutesTable.polyline)
                    .where { RoutesTable.id eq routeId }
                    .singleOrNull()
                    ?: return@transaction null

            val polyline = route[RoutesTable.polyline]

            // Calculate progress along the line (0.0 to 1.0)
            val progress =
                RoutesTable
                    .select(
                        SpatialFunctions.lineLocatePoint(
                            polylineExpression(polyline),
                            pointExpression(geomPoint),
                        ),
                    ).where { RoutesTable.id eq routeId }
                    .single()[
                    SpatialFunctions.lineLocatePoint(
                        polylineExpression(polyline),
                        pointExpression(geomPoint),
                    ),
                ]

            // Interpolate the point at that progress to get the snapped coordinate
            val snappedGeom =
                RoutesTable
                    .select(
                        SpatialFunctions.lineInterpolatePoint(
                            polylineExpression(polyline),
                            progressExpression(progress),
                        ),
                    ).where { RoutesTable.id eq routeId }
                    .single()[
                    SpatialFunctions.lineInterpolatePoint(
                        polylineExpression(polyline),
                        progressExpression(progress),
                    ),
                ]

            val snappedPoint = snappedGeom.geometry as Point
            Pair(Location(snappedPoint.y, snappedPoint.x), progress)
        }

    /** Retrieves all routes from the database, including the WKT polyline for frontend rendering. */
    fun findAllRoutes(): List<com.solodev.fleet.modules.tracking.application.dto.RouteDTO> =
        transaction {
            val wktExpr = SpatialFunctions.asText(RoutesTable.polyline)
            RoutesTable
                .select(
                    RoutesTable.id,
                    RoutesTable.name,
                    RoutesTable.description,
                    wktExpr,
                ).map {
                    com.solodev.fleet.modules.tracking.application.dto.RouteDTO(
                        id = it[RoutesTable.id].value.toString(),
                        name = it[RoutesTable.name],
                        description = it[RoutesTable.description],
                        lineString = it[wktExpr],
                    )
                }
        }

    /**
     * Creates a new route from a WKT LINESTRING and persists it to the database.
     * Returns the persisted RouteDTO including the auto-generated id.
     */
    fun createRoute(
        name: String,
        description: String?,
        wktLineString: String,
    ): com.solodev.fleet.modules.tracking.application.dto.RouteDTO =
        transaction {
            val geom = PGgeometry("SRID=4326;$wktLineString")
            val id =
                RoutesTable.insertAndGetId {
                    it[RoutesTable.name] = name
                    it[RoutesTable.description] = description
                    it[RoutesTable.polyline] = geom
                }
            com.solodev.fleet.modules.tracking.application.dto.RouteDTO(
                id = id.value.toString(),
                name = name,
                description = description,
                lineString = wktLineString,
            )
        }

    /** Checks if a location is inside any geofence of a specific type. */
    open fun isInsideGeofence(
        location: Location,
        type: String,
    ): Boolean =
        transaction {
            val pointWkt = "SRID=4326;POINT(${location.longitude} ${location.latitude})"
            val geomPoint = PGgeometry(pointWkt)

            GeofencesTable
                .select(GeofencesTable.id)
                .where {
                    (GeofencesTable.type eq type) and
                        SpatialFunctions.contains(
                            GeofencesTable.boundary,
                            pointExpression(geomPoint),
                        )
                }.any()
        }

    // Helper functions for Exposed expressions
    private fun pointExpression(point: PGgeometry): Expression<PGgeometry> =
        QueryParameter(
            point,
            PostGISColumnType(),
        )

    private fun polylineExpression(polyline: PGgeometry): Expression<PGgeometry> =
        QueryParameter(
            polyline,
            PostGISColumnType(),
        )

    private fun progressExpression(progress: Double): Expression<Double> = QueryParameter(progress, DoubleColumnType())
}
