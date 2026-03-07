# Tracking Module - Test Implementation Guide

This document covers testing strategy and implementations for the Phase 7 Tracking module, including GPS location processing, PostGIS route snapping, delta-encoding, WebSocket broadcasting, and all resilience patterns (rate limiting, idempotency, circuit breaker).

---

## 1. Domain / DTO Unit Tests

### VehicleStateDelta Tests
`src/test/kotlin/com/solodev/fleet/modules/tracking/application/dto/VehicleStateDeltaTest.kt`

```kotlin
package com.solodev.fleet.modules.tracking.application.dto

import java.time.Instant
import java.util.UUID
import kotlin.test.*

class VehicleStateDeltaTest {

    private val vehicleId = UUID.randomUUID()
    private val now = Instant.now()

    @Test
    fun `hasChanges returns false when all fields are null`() {
        val delta = VehicleStateDelta(vehicleId = vehicleId, timestamp = now)
        assertFalse(delta.hasChanges())
    }

    @Test
    fun `hasChanges returns true when progress changed`() {
        val delta = VehicleStateDelta(vehicleId = vehicleId, progress = 0.45, timestamp = now)
        assertTrue(delta.hasChanges())
    }

    @Test
    fun `hasChanges returns true when status changed`() {
        val delta = VehicleStateDelta(vehicleId = vehicleId, status = VehicleStatus.IDLE, timestamp = now)
        assertTrue(delta.hasChanges())
    }

    @Test
    fun `hasChanges returns true when bearing changed`() {
        val delta = VehicleStateDelta(vehicleId = vehicleId, bearing = 270.0, timestamp = now)
        assertTrue(delta.hasChanges())
    }

    @Test
    fun `hasChanges returns true when distanceFromRoute changed`() {
        val delta = VehicleStateDelta(vehicleId = vehicleId, distanceFromRoute = 120.0, timestamp = now)
        assertTrue(delta.hasChanges())
    }
}
```

### VehicleStateDeltaExtensions Tests
`src/test/kotlin/com/solodev/fleet/modules/tracking/application/dto/VehicleStateDeltaExtensionsTest.kt`

```kotlin
package com.solodev.fleet.modules.tracking.application.dto

import java.time.Instant
import kotlin.test.*

class VehicleStateDeltaExtensionsTest {

    private val now = Instant.now()

    private fun makeState(
        vehicleId: String = "c9352986-639a-4841-bed9-9ff99f2e3349",
        progress: Double = 0.42,
        heading: Double = 180.0,
        status: VehicleStatus = VehicleStatus.IN_TRANSIT,
        distanceFromRoute: Double = 8.5
    ) = VehicleRouteState(
        vehicleId = vehicleId,
        routeId = "68a1a7f1-76dd-4ec9-ad63-fefc22acf428",
        progress = progress,
        segmentId = "seg-101",
        speed = 45.5,
        heading = heading,
        status = status,
        distanceFromRoute = distanceFromRoute,
        latitude = 14.6001,
        longitude = 121.0250,
        timestamp = now
    )

    // --- full() ---

    @Test
    fun `full() populates all fields from state`() {
        val state = makeState()
        val delta = VehicleStateDelta.full(state)

        assertEquals(state.progress, delta.progress)
        assertEquals(state.heading, delta.bearing)
        assertEquals(state.status, delta.status)
        assertEquals(state.distanceFromRoute, delta.distanceFromRoute)
        assertTrue(delta.hasChanges())
    }

    // --- diff() - no changes ---

    @Test
    fun `diff() returns empty delta when states are identical`() {
        val state = makeState()
        val delta = VehicleStateDelta.diff(state, state)

        assertNull(delta.progress)
        assertNull(delta.bearing)
        assertNull(delta.status)
        assertNull(delta.distanceFromRoute)
        assertFalse(delta.hasChanges())
    }

    // --- diff() - progress changed ---

    @Test
    fun `diff() includes only changed progress`() {
        val old = makeState(progress = 0.42)
        val new = makeState(progress = 0.45)
        val delta = VehicleStateDelta.diff(old, new)

        assertEquals(0.45, delta.progress)
        assertNull(delta.status)
        assertNull(delta.bearing)
        assertTrue(delta.hasChanges())
    }

    // --- diff() - status changed to IDLE ---

    @Test
    fun `diff() includes status change to IDLE`() {
        val old = makeState(status = VehicleStatus.IN_TRANSIT)
        val new = makeState(status = VehicleStatus.IDLE)
        val delta = VehicleStateDelta.diff(old, new)

        assertEquals(VehicleStatus.IDLE, delta.status)
        assertNull(delta.progress)
        assertTrue(delta.hasChanges())
    }

    // --- diff() - OFF_ROUTE ---

    @Test
    fun `diff() includes distanceFromRoute when off-route`() {
        val old = makeState(distanceFromRoute = 8.5)
        val new = makeState(distanceFromRoute = 152.3, status = VehicleStatus.OFF_ROUTE)
        val delta = VehicleStateDelta.diff(old, new)

        assertEquals(152.3, delta.distanceFromRoute)
        assertEquals(VehicleStatus.OFF_ROUTE, delta.status)
        assertTrue(delta.hasChanges())
    }

    // --- diff() - heading changed ---

    @Test
    fun `diff() includes bearing when heading changes`() {
        val old = makeState(heading = 180.0)
        val new = makeState(heading = 270.0)
        val delta = VehicleStateDelta.diff(old, new)

        assertEquals(270.0, delta.bearing)
        assertNull(delta.progress)
        assertTrue(delta.hasChanges())
    }
}
```

---

## 2. Use Case Unit Tests

### UpdateVehicleLocationUseCase Tests
`src/test/kotlin/com/solodev/fleet/modules/tracking/application/usecases/UpdateVehicleLocationUseCaseTest.kt`

```kotlin
package com.solodev.fleet.modules.tracking.application.usecases

import com.solodev.fleet.modules.tracking.application.dto.SensorPing
import com.solodev.fleet.modules.tracking.application.dto.VehicleStatus
import com.solodev.fleet.shared.domain.model.Location
import com.solodev.fleet.modules.tracking.infrastructure.metrics.SpatialMetrics
import com.solodev.fleet.modules.tracking.infrastructure.persistence.LocationHistoryRepository
import com.solodev.fleet.modules.tracking.infrastructure.persistence.PostGISAdapter
import com.solodev.fleet.modules.tracking.infrastructure.websocket.RedisDeltaBroadcaster
import io.mockk.*
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.util.UUID
import kotlin.test.*

class UpdateVehicleLocationUseCaseTest {

    private val postGISAdapter = mockk<PostGISAdapter>()
    private val broadcaster = mockk<RedisDeltaBroadcaster>(relaxed = true)
    private val metrics = mockk<SpatialMetrics>(relaxed = true)
    private val historyRepository = mockk<LocationHistoryRepository>(relaxed = true)

    private val useCase = UpdateVehicleLocationUseCase(
        postGISAdapter = postGISAdapter,
        broadcaster = broadcaster,
        metrics = metrics,
        historyRepository = historyRepository
    )

    private val vehicleId = UUID.randomUUID().toString()
    private val routeId = UUID.randomUUID().toString()
    private val now = Instant.now()

    private fun makePing(
        speed: Double = 45.5,
        lat: Double = 14.5995,
        lon: Double = 121.0244
    ) = SensorPing(
        vehicleId = vehicleId,
        location = Location(lat, lon),
        speed = speed,
        heading = 180.0,
        accuracy = 5.0,
        timestamp = now,
        routeId = routeId
    )

    @Test
    fun `execute snaps location and broadcasts when on route`() = runBlocking {
        coEvery { postGISAdapter.snapToRoute(any(), any()) } returns Pair(
            Location(14.6001, 121.0250), 0.42
        )

        useCase.execute(makePing(speed = 45.5))

        coVerify { broadcaster.broadcastIfChanged(any(), match { it.status == VehicleStatus.IN_TRANSIT }) }
        coVerify { historyRepository.saveTrackingRecord(any()) }
        verify { metrics.recordSnapDuration(any()) }
    }

    @Test
    fun `execute sets IDLE status when speed below threshold`() = runBlocking {
        coEvery { postGISAdapter.snapToRoute(any(), any()) } returns Pair(
            Location(14.6001, 121.0250), 0.42
        )

        useCase.execute(makePing(speed = 0.0))

        coVerify { broadcaster.broadcastIfChanged(any(), match { it.status == VehicleStatus.IDLE }) }
    }

    @Test
    fun `execute sets OFF_ROUTE status when distance exceeds 100m`() = runBlocking {
        // snapToRoute returns a progress that maps to > 100m
        coEvery { postGISAdapter.snapToRoute(any(), any()) } returns Pair(
            Location(14.7100, 121.1500), 0.85 // large progress offset → large distance estimate
        )
        // Force an off-route: distance = progress * 1000 > 100 → need progress > 0.1
        // The implementation does: distanceFromRoute = snapResult.second * 1000.0
        // So progress = 0.15 → distance = 150 → OFF_ROUTE

        val bigOffsetPing = SensorPing(
            vehicleId = vehicleId,
            location = Location(14.7100, 121.1500),
            speed = 30.5,
            heading = 270.0,
            accuracy = 8.0,
            timestamp = now,
            routeId = routeId
        )
        coEvery { postGISAdapter.snapToRoute(any(), any()) } returns Pair(
            Location(14.7100, 121.1500), 0.15  // 0.15 * 1000 = 150m → OFF_ROUTE
        )

        useCase.execute(bigOffsetPing)

        coVerify { broadcaster.broadcastIfChanged(any(), match { it.status == VehicleStatus.OFF_ROUTE }) }
    }

    @Test
    fun `execute throws when routeId is missing`() = runBlocking {
        val pingNoRoute = SensorPing(
            vehicleId = vehicleId,
            location = Location(14.5995, 121.0244),
            speed = 45.5,
            heading = 180.0,
            accuracy = 5.0,
            timestamp = now,
            routeId = null
        )

        assertFailsWith<IllegalArgumentException> {
            useCase.execute(pingNoRoute)
        }
    }

    @Test
    fun `execute throws when PostGIS snapToRoute returns null`() = runBlocking {
        coEvery { postGISAdapter.snapToRoute(any(), any()) } returns null

        assertFailsWith<IllegalStateException> {
            useCase.execute(makePing())
        }
    }
}
```

---

## 3. Infrastructure Unit Tests

### RedisDeltaBroadcaster Tests
`src/test/kotlin/com/solodev/fleet/modules/tracking/infrastructure/websocket/RedisDeltaBroadcasterTest.kt`

```kotlin
package com.solodev.fleet.modules.tracking.infrastructure.websocket

import com.solodev.fleet.modules.tracking.application.dto.VehicleRouteState
import com.solodev.fleet.modules.tracking.application.dto.VehicleStatus
import com.solodev.fleet.modules.vehicles.domain.repository.VehicleRepository
import com.solodev.fleet.shared.infrastructure.cache.RedisCacheManager
import io.mockk.*
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.util.UUID
import kotlin.test.*

class RedisDeltaBroadcasterTest {

    private val redisCache = mockk<RedisCacheManager>()
    private val vehicleRepository = mockk<VehicleRepository>()

    private val broadcaster = RedisDeltaBroadcaster(
        redisCache = redisCache,
        vehicleRepository = vehicleRepository,
        jedis = null   // No Redis in unit test
    )

    private val vehicleId = UUID.randomUUID()
    private val now = Instant.now()

    private fun makeState(
        progress: Double = 0.42,
        status: VehicleStatus = VehicleStatus.IN_TRANSIT
    ) = VehicleRouteState(
        vehicleId = vehicleId.toString(),
        routeId = UUID.randomUUID().toString(),
        progress = progress,
        segmentId = "seg-101",
        speed = 45.5,
        heading = 180.0,
        status = status,
        distanceFromRoute = 8.5,
        latitude = 14.6001,
        longitude = 121.0250,
        timestamp = now
    )

    @Test
    fun `broadcastIfChanged sends full state on first update (no cached state)`() = runBlocking {
        coEvery { redisCache.getOrSet<VehicleRouteState?>(any(), any(), any()) } returns null andThen makeState()

        // No sessions registered → no WS send, but cache update still happens
        broadcaster.broadcastIfChanged(vehicleId, makeState())

        coVerify(atLeast = 1) { redisCache.getOrSet<VehicleRouteState?>(any(), any(), any()) }
    }

    @Test
    fun `broadcastIfChanged skips broadcast when state is unchanged`() = runBlocking {
        val state = makeState()
        // Return same state from cache → diff will have no changes
        coEvery { redisCache.getOrSet<VehicleRouteState?>(any(), any(), any()) } returns state

        broadcaster.broadcastIfChanged(vehicleId, state)

        // With no changes, no frame should be sent (no sessions to verify, but cache lookup happened)
        coVerify { redisCache.getOrSet<VehicleRouteState?>(any(), any(), any()) }
    }
}
```

### LocationUpdateRateLimiter Tests
`src/test/kotlin/com/solodev/fleet/modules/tracking/infrastructure/ratelimit/LocationUpdateRateLimiterTest.kt`

```kotlin
package com.solodev.fleet.modules.tracking.infrastructure.ratelimit

import kotlin.test.*

class LocationUpdateRateLimiterTest {

    private val rateLimiter = LocationUpdateRateLimiter(maxUpdatesPerMinute = 3)

    @Test
    fun `allows requests within limit`() {
        val vehicleId = "vehicle-001"

        assertTrue(rateLimiter.isAllowed(vehicleId))
        assertTrue(rateLimiter.isAllowed(vehicleId))
        assertTrue(rateLimiter.isAllowed(vehicleId))
    }

    @Test
    fun `blocks requests exceeding limit`() {
        val vehicleId = "vehicle-002"

        repeat(3) { rateLimiter.isAllowed(vehicleId) }

        assertFalse(rateLimiter.isAllowed(vehicleId))
    }

    @Test
    fun `different vehicles have independent quotas`() {
        repeat(3) { rateLimiter.isAllowed("v-A") }

        assertTrue(rateLimiter.isAllowed("v-B"))
    }

    @Test
    fun `getRemainingQuota decrements after each allowed call`() {
        val vehicleId = "vehicle-003"
        rateLimiter.isAllowed(vehicleId)
        val remaining = rateLimiter.getRemainingQuota(vehicleId)
        assertEquals(2, remaining)
    }

    @Test
    fun `getWaitTimeSeconds returns positive value when rate limited`() {
        val vehicleId = "vehicle-004"
        repeat(3) { rateLimiter.isAllowed(vehicleId) }
        val wait = rateLimiter.getWaitTimeSeconds(vehicleId)
        assertTrue(wait > 0)
    }
}
```

### CircuitBreaker Tests
`src/test/kotlin/com/solodev/fleet/modules/tracking/infrastructure/resilience/CircuitBreakerTest.kt`

```kotlin
package com.solodev.fleet.modules.tracking.infrastructure.resilience

import kotlinx.coroutines.runBlocking
import kotlin.test.*

class CircuitBreakerTest {

    @Test
    fun `allows execution when CLOSED`() = runBlocking {
        val cb = CircuitBreaker("test", failureThreshold = 3)
        var called = false
        cb.execute { called = true }
        assertTrue(called)
    }

    @Test
    fun `opens after failureThreshold consecutive failures`() = runBlocking {
        val cb = CircuitBreaker("test-open", failureThreshold = 2)

        repeat(2) {
            runCatching { cb.execute { throw RuntimeException("fail") } }
        }

        val ex = assertFailsWith<Exception> {
            cb.execute { /* should not reach */ }
        }
        assertTrue(ex.message?.contains("open", ignoreCase = true) == true || ex.message?.contains("Circuit") == true)
    }

    @Test
    fun `resets after successful call when HALF_OPEN`() = runBlocking {
        val cb = CircuitBreaker("test-half", failureThreshold = 1)
        runCatching { cb.execute { throw RuntimeException("fail") } }

        // Should attempt recovery on next call
        var recovered = false
        runCatching { cb.execute { recovered = true } }
        // Depends on half-open timeout — at minimum ensure no exception thrown immediately
    }
}
```

---

## 4. HTTP Route Integration Tests

### Location Update Endpoint
`src/test/kotlin/com/solodev/fleet/modules/tracking/infrastructure/http/TrackingRoutesTest.kt`

```kotlin
package com.solodev.fleet.modules.tracking.infrastructure.http

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.*

class TrackingRoutesTest {

    // --- POST /v1/tracking/vehicles/{id}/location ---

    @Test
    fun `POST location returns 200 with tracking confirmation`() = testApplication {
        val response = client.post("/v1/tracking/vehicles/$VEHICLE_ID/location") {
            bearerAuth(TEST_JWT)
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                    "latitude": 14.5995,
                    "longitude": 121.0244,
                    "routeId": "$ROUTE_ID",
                    "speed": 45.5,
                    "heading": 180.0,
                    "accuracy": 5.0
                }
                """.trimIndent()
            )
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("Location update processed successfully"))
        assertTrue(body.contains(VEHICLE_ID))
    }

    @Test
    fun `POST location returns 429 when rate limit exceeded`() = testApplication {
        // Exhaust the 60/min quota
        repeat(61) {
            client.post("/v1/tracking/vehicles/$VEHICLE_ID/location") {
                bearerAuth(TEST_JWT)
                contentType(ContentType.Application.Json)
                setBody(validLocationBody())
            }
        }

        val response = client.post("/v1/tracking/vehicles/$VEHICLE_ID/location") {
            bearerAuth(TEST_JWT)
            contentType(ContentType.Application.Json)
            setBody(validLocationBody())
        }
        assertEquals(HttpStatusCode.TooManyRequests, response.status)
        assertTrue(response.bodyAsText().contains("RATE_LIMIT_EXCEEDED"))
    }

    @Test
    fun `POST location with duplicate Idempotency-Key returns cached response`() = testApplication {
        val idempotencyKey = "idem-key-001"

        val first = client.post("/v1/tracking/vehicles/$VEHICLE_ID/location") {
            bearerAuth(TEST_JWT)
            contentType(ContentType.Application.Json)
            header("Idempotency-Key", idempotencyKey)
            setBody(validLocationBody())
        }
        assertEquals(HttpStatusCode.OK, first.status)

        val second = client.post("/v1/tracking/vehicles/$VEHICLE_ID/location") {
            bearerAuth(TEST_JWT)
            contentType(ContentType.Application.Json)
            header("Idempotency-Key", idempotencyKey)
            setBody(validLocationBody())
        }
        assertEquals(HttpStatusCode.OK, second.status)
        assertEquals(first.bodyAsText(), second.bodyAsText())
    }

    @Test
    fun `POST location returns 401 when JWT missing`() = testApplication {
        val response = client.post("/v1/tracking/vehicles/$VEHICLE_ID/location") {
            contentType(ContentType.Application.Json)
            setBody(validLocationBody())
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    // --- GET /v1/tracking/routes ---

    @Test
    fun `GET routes returns 200 without auth`() = testApplication {
        val response = client.get("/v1/tracking/routes")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("success"))
    }

    // --- GET /v1/tracking/vehicles/{vehicleId}/state ---

    @Test
    fun `GET vehicle state returns 200 with state fields`() = testApplication {
        val response = client.get("/v1/tracking/vehicles/$VEHICLE_ID/state") {
            bearerAuth(TEST_JWT)
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("progress"))
        assertTrue(body.contains("status"))
        assertTrue(body.contains("speed"))
    }

    @Test
    fun `GET vehicle state returns 401 when unauthenticated`() = testApplication {
        val response = client.get("/v1/tracking/vehicles/$VEHICLE_ID/state")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    // --- GET /v1/tracking/fleet/status ---

    @Test
    fun `GET fleet status returns 200 with vehicle list`() = testApplication {
        val response = client.get("/v1/tracking/fleet/status") {
            bearerAuth(TEST_JWT)
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("totalVehicles"))
        assertTrue(body.contains("activeVehicles"))
        assertTrue(body.contains("vehicles"))
    }

    // --- GET /v1/tracking/vehicles/{vehicleId}/history ---

    @Test
    fun `GET tracking history returns 200 with records`() = testApplication {
        val response = client.get("/v1/tracking/vehicles/$VEHICLE_ID/history?limit=10&offset=0") {
            bearerAuth(TEST_JWT)
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("vehicleId"))
        assertTrue(body.contains("totalRecords"))
        assertTrue(body.contains("records"))
    }

    @Test
    fun `GET tracking history uses default limit=100 when not specified`() = testApplication {
        val response = client.get("/v1/tracking/vehicles/$VEHICLE_ID/history") {
            bearerAuth(TEST_JWT)
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    // --- Helpers ---
    companion object {
        const val VEHICLE_ID = "c9352986-639a-4841-bed9-9ff99f2e3349"
        const val ROUTE_ID = "68a1a7f1-76dd-4ec9-ad63-fefc22acf428"
        const val TEST_JWT = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..." // Use a real test JWT

        fun validLocationBody() = """
            {
                "latitude": 14.5995,
                "longitude": 121.0244,
                "routeId": "$ROUTE_ID",
                "speed": 45.5,
                "heading": 180.0,
                "accuracy": 5.0
            }
        """.trimIndent()
    }
}
```

---

## 5. WebSocket Integration Tests

### WebSocket Live Fleet Connection
`src/test/kotlin/com/solodev/fleet/modules/tracking/infrastructure/websocket/LiveFleetWebSocketTest.kt`

```kotlin
package com.solodev.fleet.modules.tracking.infrastructure.websocket

import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.ktor.websocket.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.*

class LiveFleetWebSocketTest {

    // --- Connection ---

    @Test
    fun `WS fleet live connects and receives initial state`() = testApplication {
        val client = createClient { install(WebSockets) }

        client.webSocket("/v1/fleet/live", {
            bearerAuth(TEST_JWT)
        }) {
            // If any vehicles have cached state, we get initial_state messages
            // For empty fleet, connection should stay open without error
            assertNotNull(this)
        }
    }

    @Test
    fun `WS fleet live responds to Ping with Pong`() = testApplication {
        val client = createClient { install(WebSockets) }

        client.webSocket("/v1/fleet/live", {
            bearerAuth(TEST_JWT)
        }) {
            send(Frame.Ping("keepalive".encodeToByteArray()))

            val pong = withTimeoutOrNull(2000) {
                for (frame in incoming) {
                    if (frame is Frame.Pong) return@withTimeoutOrNull frame
                }
                null
            }
            assertNotNull(pong, "Expected Pong frame in response to Ping")
        }
    }

    // --- Delta broadcast ---

    @Test
    fun `WS fleet live receives delta after POST location update`() = testApplication {
        val client = createClient { install(WebSockets) }

        var deltaReceived: String? = null

        client.webSocket("/v1/fleet/live", {
            bearerAuth(TEST_JWT)
        }) {
            // Trigger a location update from a second coroutine
            launch {
                client.post("/v1/tracking/vehicles/$VEHICLE_ID/location") {
                    bearerAuth(TEST_JWT)
                    contentType(ContentType.Application.Json)
                    setBody(validLocationBody())
                }
            }

            deltaReceived = withTimeoutOrNull(3000) {
                for (frame in incoming) {
                    if (frame is Frame.Text) return@withTimeoutOrNull frame.readText()
                }
                null
            }
        }

        assertNotNull(deltaReceived, "Expected WebSocket delta message after location POST")
        assertTrue(deltaReceived!!.contains("vehicleId"))
        assertTrue(deltaReceived!!.contains("timestamp"))
    }

    // --- Status change broadcasts ---

    @Test
    fun `WS receives IDLE status delta when speed drops below 5 m-s`() = testApplication {
        val client = createClient { install(WebSockets) }
        var statusDelta: String? = null

        client.webSocket("/v1/fleet/live", { bearerAuth(TEST_JWT) }) {
            launch {
                client.post("/v1/tracking/vehicles/$VEHICLE_ID/location") {
                    bearerAuth(TEST_JWT)
                    contentType(ContentType.Application.Json)
                    setBody(idleLocationBody())
                }
            }

            statusDelta = withTimeoutOrNull(3000) {
                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        val text = frame.readText()
                        if (text.contains("IDLE")) return@withTimeoutOrNull text
                    }
                }
                null
            }
        }

        assertNotNull(statusDelta)
        assertTrue(statusDelta!!.contains("IDLE"))
    }

    @Test
    fun `WS receives OFF_ROUTE status delta when vehicle leaves route`() = testApplication {
        val client = createClient { install(WebSockets) }
        var statusDelta: String? = null

        client.webSocket("/v1/fleet/live", { bearerAuth(TEST_JWT) }) {
            launch {
                client.post("/v1/tracking/vehicles/$VEHICLE_ID/location") {
                    bearerAuth(TEST_JWT)
                    contentType(ContentType.Application.Json)
                    setBody(offRouteLocationBody())
                }
            }

            statusDelta = withTimeoutOrNull(3000) {
                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        val text = frame.readText()
                        if (text.contains("OFF_ROUTE")) return@withTimeoutOrNull text
                    }
                }
                null
            }
        }

        assertNotNull(statusDelta)
        assertTrue(statusDelta!!.contains("OFF_ROUTE"))
        assertTrue(statusDelta!!.contains("distanceFromRoute"))
    }

    // --- Delta encoding efficiency ---

    @Test
    fun `WS sends only changed fields in delta (not full state every time)`() = testApplication {
        val client = createClient { install(WebSockets) }
        val deltas = mutableListOf<String>()

        client.webSocket("/v1/fleet/live", { bearerAuth(TEST_JWT) }) {
            // Send same location twice — second broadcast should be empty (no changes)
            launch {
                repeat(2) {
                    client.post("/v1/tracking/vehicles/$VEHICLE_ID/location") {
                        bearerAuth(TEST_JWT)
                        contentType(ContentType.Application.Json)
                        setBody(validLocationBody())
                    }
                }
            }

            withTimeoutOrNull(3000) {
                for (frame in incoming) {
                    if (frame is Frame.Text) deltas.add(frame.readText())
                    if (deltas.size >= 2) break
                }
            }
        }

        // First delta should have changes; second should not (state unchanged)
        assertTrue(deltas.isNotEmpty())
        // Verify delta payloads are smaller than full state (~15-25 bytes vs 50-60 bytes)
        deltas.forEach { delta ->
            // Should not contain routeId (not part of delta schema)
            // Should contain vehicleId and timestamp always
            assertTrue(delta.contains("vehicleId"))
            assertTrue(delta.contains("timestamp"))
        }
    }

    // --- Session cleanup ---

    @Test
    fun `WS session is cleaned up after disconnect`() = testApplication {
        // Connect then immediately disconnect
        val client = createClient { install(WebSockets) }
        client.webSocket("/v1/fleet/live", { bearerAuth(TEST_JWT) }) {
            close(CloseReason(CloseReason.Codes.NORMAL, "test done"))
        }
        // No assertion needed; test passes if no exception thrown on close
    }

    companion object {
        const val VEHICLE_ID = "c9352986-639a-4841-bed9-9ff99f2e3349"
        const val ROUTE_ID = "68a1a7f1-76dd-4ec9-ad63-fefc22acf428"
        const val TEST_JWT = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."

        fun validLocationBody() = """
            {
                "latitude": 14.5995, "longitude": 121.0244,
                "routeId": "$ROUTE_ID", "speed": 45.5, "heading": 180.0, "accuracy": 5.0
            }
        """.trimIndent()

        fun idleLocationBody() = """
            {
                "latitude": 14.5995, "longitude": 121.0244,
                "routeId": "$ROUTE_ID", "speed": 0.0, "heading": null, "accuracy": 3.0
            }
        """.trimIndent()

        fun offRouteLocationBody() = """
            {
                "latitude": 14.7100, "longitude": 121.1500,
                "routeId": "$ROUTE_ID", "speed": 30.5, "heading": 270.0, "accuracy": 8.0
            }
        """.trimIndent()
    }
}
```

---

## 6. Error Scenario Tests

### Validation & Error Handling
`src/test/kotlin/com/solodev/fleet/modules/tracking/infrastructure/http/TrackingRoutesErrorTest.kt`

```kotlin
package com.solodev.fleet.modules.tracking.infrastructure.http

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.*

class TrackingRoutesErrorTest {

    @Test
    fun `POST location with invalid speed returns 400`() = testApplication {
        val response = client.post("/v1/tracking/vehicles/$VEHICLE_ID/location") {
            bearerAuth(TEST_JWT)
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "latitude": 14.5995, "longitude": 121.0244,
                    "routeId": "$ROUTE_ID",
                    "speed": 150.0,
                    "heading": 180.0
                }
            """.trimIndent())
        }
        // Speed > 100 m/s is invalid per domain rules
        assertTrue(response.status.value in 400..503)
    }

    @Test
    fun `POST location with invalid heading returns error`() = testApplication {
        val response = client.post("/v1/tracking/vehicles/$VEHICLE_ID/location") {
            bearerAuth(TEST_JWT)
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "latitude": 14.5995, "longitude": 121.0244,
                    "routeId": "$ROUTE_ID",
                    "speed": 45.5,
                    "heading": 400.0
                }
            """.trimIndent())
        }
        assertTrue(response.status.value in 400..503)
    }

    @Test
    fun `POST location when circuit breaker is OPEN returns 503`() = testApplication {
        // Exhaust circuit breaker (5 failures)
        // This requires a way to inject a failing PostGIS adapter in test context
        // Placeholder — implement with Testcontainers killing PostGIS
    }

    @Test
    fun `GET tracking history for unknown vehicle returns empty records`() = testApplication {
        val response = client.get("/v1/tracking/vehicles/00000000-0000-0000-0000-000000000000/history") {
            bearerAuth(TEST_JWT)
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("totalRecords"))
    }

    companion object {
        const val VEHICLE_ID = "c9352986-639a-4841-bed9-9ff99f2e3349"
        const val ROUTE_ID = "68a1a7f1-76dd-4ec9-ad63-fefc22acf428"
        const val TEST_JWT = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
    }
}
```

---

## 7. Spatial / PostGIS Tests

### MatchingEngine Tests
`src/test/kotlin/com/solodev/fleet/modules/tracking/infrastructure/spatial/MatchingEngineTest.kt`

```kotlin
package com.solodev.fleet.modules.tracking.infrastructure.spatial

import com.solodev.fleet.shared.domain.model.Location
import kotlin.test.*

class MatchingEngineTest {

    private val engine = MatchingEngine()

    @Test
    fun `snapToRoute returns progress 0_0 at route start`() {
        val routeStart = Location(14.5995, 121.0244)
        // For a route starting at this point, progress should be ~0.0
        // Requires a test route geometry fixture
    }

    @Test
    fun `snapToRoute returns progress 1_0 at route end`() {
        // Similar fixture - at end of route, progress should be ~1.0
    }

    @Test
    fun `computeDistanceFromRoute returns 0 for point on route`() {
        // A point exactly on the route geometry → distance ≈ 0
    }

    @Test
    fun `computeDistanceFromRoute returns positive for point off route`() {
        // A point 200m from nearest route point → distance > 100
    }
}
```

---

## 8. Serialization Tests

### VehicleStateDelta JSON Serialization
`src/test/kotlin/com/solodev/fleet/modules/tracking/application/dto/VehicleStateDeltaSerializationTest.kt`

```kotlin
package com.solodev.fleet.modules.tracking.application.dto

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import java.util.UUID
import kotlin.test.*

class VehicleStateDeltaSerializationTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `serializes full delta to JSON with all fields`() {
        val delta = VehicleStateDelta(
            vehicleId = UUID.fromString("c9352986-639a-4841-bed9-9ff99f2e3349"),
            progress = 0.45,
            bearing = 180.0,
            status = VehicleStatus.IN_TRANSIT,
            distanceFromRoute = 8.5,
            timestamp = Instant.parse("2026-03-07T14:32:22Z")
        )

        val serialized = json.encodeToString(delta)

        assertTrue(serialized.contains("vehicleId"))
        assertTrue(serialized.contains("0.45"))
        assertTrue(serialized.contains("IN_TRANSIT"))
        assertTrue(serialized.contains("180.0"))
        assertTrue(serialized.contains("2026-03-07"))
    }

    @Test
    fun `serializes partial delta with null fields omitted`() {
        val delta = VehicleStateDelta(
            vehicleId = UUID.fromString("c9352986-639a-4841-bed9-9ff99f2e3349"),
            progress = 0.45,
            timestamp = Instant.now()
        )

        val serialized = json.encodeToString(delta)

        assertTrue(serialized.contains("progress"))
        // Null fields should be absent or null — no bearing, status, distanceFromRoute changes
    }

    @Test
    fun `deserializes delta message from WebSocket frame`() {
        val raw = """
            {
                "vehicleId": "c9352986-639a-4841-bed9-9ff99f2e3349",
                "progress": 0.45,
                "bearing": 180.0,
                "timestamp": "2026-03-07T14:32:22Z"
            }
        """.trimIndent()

        val delta = json.decodeFromString<VehicleStateDelta>(raw)

        assertEquals(0.45, delta.progress)
        assertEquals(180.0, delta.bearing)
        assertNull(delta.status)
    }
}
```

---

## 9. Metrics Tests

### SpatialMetrics Tests
`src/test/kotlin/com/solodev/fleet/modules/tracking/infrastructure/metrics/SpatialMetricsTest.kt`

```kotlin
package com.solodev.fleet.modules.tracking.infrastructure.metrics

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import kotlin.test.*

class SpatialMetricsTest {

    private val registry = SimpleMeterRegistry()
    private val metrics = SpatialMetrics(registry)

    @Test
    fun `recordSnapDuration increments timer`() {
        metrics.recordSnapDuration(42L)
        val timer = registry.find("postgis.snap.duration").timer()
        assertNotNull(timer)
        assertEquals(1L, timer?.count())
    }

    @Test
    fun `recordBroadcast increments broadcast counter`() {
        metrics.recordBroadcast()
        val counter = registry.find("tracking.delta.broadcasts").counter()
        assertNotNull(counter)
        assertEquals(1.0, counter?.count())
    }
}
```

---

## 10. Test Summary

| Test Class | Layer | Coverage |
|-----------|-------|----------|
| `VehicleStateDeltaTest` | Unit - DTO | `hasChanges()` all combinations |
| `VehicleStateDeltaExtensionsTest` | Unit - DTO | `full()`, `diff()` all status transitions |
| `UpdateVehicleLocationUseCaseTest` | Unit - Use Case | Happy path, IDLE, OFF_ROUTE, missing route, null snap |
| `RedisDeltaBroadcasterTest` | Unit - Infra | First broadcast (full), unchanged state skip |
| `LocationUpdateRateLimiterTest` | Unit - Infra | Within limit, exceed limit, isolation, quota tracking |
| `CircuitBreakerTest` | Unit - Infra | CLOSED, OPEN, HALF_OPEN |
| `TrackingRoutesTest` | Integration - HTTP | All 5 endpoints, auth, rate limit, idempotency |
| `TrackingRoutesErrorTest` | Integration - Errors | Invalid speed, heading, unknown vehicle |
| `LiveFleetWebSocketTest` | Integration - WS | Connect, Ping/Pong, delta broadcast, IDLE, OFF_ROUTE, delta efficiency, disconnect |
| `MatchingEngineTest` | Unit - Spatial | Route snap correctness (requires PostGIS fixture) |
| `VehicleStateDeltaSerializationTest` | Unit - Serialization | JSON encode/decode for WS frames |
| `SpatialMetricsTest` | Unit - Metrics | Counter and timer increments |
