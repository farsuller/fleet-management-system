package com.solodev.fleet

import com.solodev.fleet.modules.tracking.infrastructure.persistence.PostGISAdapter
import com.solodev.fleet.modules.tracking.infrastructure.persistence.RoutesTable
import com.solodev.fleet.shared.domain.model.Location
import java.util.*
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.postgis.PGgeometry

class PostGISAdapterTest : BaseSpatialTest() {

    private val adapter = PostGISAdapter()

    @Test
    fun `should snap location to route`() {
        val routeId = UUID.randomUUID()

        transaction {
            RoutesTable.insert {
                it[id] = routeId
                it[name] = "Test Route"
                // A horizontal line from (1, 1) to (5, 1)
                it[polyline] = PGgeometry("SRID=4326;LINESTRING(121.103 14.702, 121.105 14.702)")
            }
        }

        // Test point slightly off the line: (121.104, 14.703)
        // Expected snapping: (121.104, 14.702) which is 50% along the line
        val rawLocation = Location(14.703, 121.104)
        val result = adapter.snapToRoute(rawLocation, routeId)

        assertTrue(result != null)
        val (snapped, progress) = result!!

        assertEquals(14.702, snapped.latitude, 0.0001)
        assertEquals(121.104, snapped.longitude, 0.0001)
        assertEquals(0.5, progress, 0.01)
    }

    @Test
    fun `should detect geofence entry`() {
        transaction {
            com.solodev.fleet.modules.tracking.infrastructure.persistence.GeofencesTable.insert {
                it[id] = UUID.randomUUID()
                it[name] = "Test Depot"
                it[type] = "DEPOT"
                it[boundary] =
                        PGgeometry(
                                "SRID=4326;POLYGON((121.100 14.700, 121.110 14.700, 121.110 14.710, 121.100 14.710, 121.100 14.700))"
                        )
            }
        }

        val locationInside = Location(14.705, 121.105)
        val locationOutside = Location(14.720, 121.105)

        assertTrue(adapter.isInsideGeofence(locationInside, "DEPOT"))
        assertTrue(!adapter.isInsideGeofence(locationOutside, "DEPOT"))
    }
}
