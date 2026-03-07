package com.solodev.fleet.modules.tracking.application.dto

import com.solodev.fleet.shared.infrastructure.serialization.InstantSerializer
import com.solodev.fleet.shared.infrastructure.serialization.UUIDSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import java.time.Instant
import java.util.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SerializationTest {

    private val json = Json {
        serializersModule = SerializersModule {
            contextual(UUID::class, UUIDSerializer)
            contextual(Instant::class, InstantSerializer)
        }
    }

    @Test
    fun `should serialize VehicleStateDelta with UUID and Instant`() {
        val delta = VehicleStateDelta(
            vehicleId = UUID.randomUUID(),
            progress = 0.42,
            bearing = 180.0,
            status = VehicleStatus.IN_TRANSIT,
            distanceFromRoute = 5.0,
            timestamp = Instant.now()
        )

        val jsonStr = json.encodeToString(delta)
        val deserialized = json.decodeFromString<VehicleStateDelta>(jsonStr)

        assertEquals(delta.vehicleId, deserialized.vehicleId)
        assertEquals(delta.progress, deserialized.progress)
        assertEquals(delta.timestamp, deserialized.timestamp)
    }

    @Test
    fun `should handle null optional fields in delta`() {
        val delta = VehicleStateDelta(
            vehicleId = UUID.randomUUID(),
            progress = null,
            bearing = null,
            status = null,
            distanceFromRoute = null,
            timestamp = Instant.now()
        )

        val jsonStr = json.encodeToString(delta)
        val deserialized = json.decodeFromString<VehicleStateDelta>(jsonStr)

        assertNull(deserialized.progress)
        assertNull(deserialized.bearing)
        assertNull(deserialized.status)
    }

    @Test
    fun `should serialize VehicleRouteState with all fields`() {
        val state = VehicleRouteState(
            vehicleId = "v-123",
            routeId = UUID.randomUUID().toString(),
            progress = 0.42,
            segmentId = "seg-1",
            speed = 30.0,
            heading = 180.0,
            status = VehicleStatus.IN_TRANSIT,
            distanceFromRoute = 5.0,
            latitude = 14.5995,
            longitude = 121.0244,
            timestamp = Instant.now()
        )

        val jsonStr = json.encodeToString(state)
        val deserialized = json.decodeFromString<VehicleRouteState>(jsonStr)

        assertEquals(state.vehicleId, deserialized.vehicleId)
        assertEquals(state.progress, deserialized.progress)
        assertEquals(state.status, deserialized.status)
    }

    @Test
    fun `should serialize VehicleStatus enum`() {
        val statuses = listOf(
            VehicleStatus.IN_TRANSIT,
            VehicleStatus.IDLE,
            VehicleStatus.OFF_ROUTE
        )

        statuses.forEach { status ->
            val jsonStr = json.encodeToString(status)
            assertEquals(true, jsonStr.isNotEmpty())
            // Verify all enum values are serializable
            assertEquals(status.name, status.toString().substringBefore("@"))
        }
    }

    @Test
    fun `should serialize SensorPing with all fields`() {
        val ping = SensorPing(
            vehicleId = "v-123",
            location = com.solodev.fleet.shared.domain.model.Location(14.5, 121.5),
            speed = 30.0,
            heading = 180.0,
            accuracy = 5.0,
            timestamp = Instant.now(),
            routeId = UUID.randomUUID().toString()
        )

        val jsonStr = json.encodeToString(ping)
        val deserialized = json.decodeFromString<SensorPing>(jsonStr)

        assertEquals(ping.vehicleId, deserialized.vehicleId)
        assertEquals(ping.speed, deserialized.speed)
        assertEquals(ping.heading, deserialized.heading)
    }
}

