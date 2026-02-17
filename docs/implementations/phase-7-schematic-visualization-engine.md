# Phase 7 — Schematic Visualization Engine (Backend)

## Status
- Overall: **Planned**
- Implementation Date: TBD
- **Verification Responsibility**:
    - **Lead Developer (USER)**: Unit testing matching logic, WebSocket integration tests
    - **Architect (Antigravity)**: Validate Delta-encoding efficiency and Redis Pub/Sub concurrency safety

---

## Purpose
Implement the backend logic that transforms raw GPS/Sensor pings into schematic progress values (0.0-1.0) and broadcasts these updates via Delta-Encoded WebSockets to connected frontend clients. This is the server-side component of the real-time tracking system.

---

## Data Flow Integration

### From GPS Coordinates to Real-Time Visualization

This phase builds on [Phase 6 PostGIS](file:///e:/Antigravity%20Projects/fleet-management/docs/implementations/phase-6-postgis-spatial-extensions.md) spatial matching:

```
┌──────────────────────────────────────────────────────────┐
│ 1. Driver sends GPS ping (lat, lng)                     │
├──────────────────────────────────────────────────────────┤
│ 2. Coordinate Reception Guard (Feature Toggle)          │
│    ├─ Global OFF → Reject (503) ✗                       │
│    ├─ Vehicle OFF → Reject (503) ✗                      │
│    └─ Both ON → Continue ✓                              │
├──────────────────────────────────────────────────────────┤
│ 3. PostGIS Spatial Matching (Phase 6)                   │
│    ├─ ST_ClosestPoint → Find nearest road segment       │
│    ├─ ST_LineLocatePoint → Calculate progress (0.0-1.0) │
│    └─ UPDATE vehicles (current_location, current_progress) │
├──────────────────────────────────────────────────────────┤
│ 4. Delta Encoding (Phase 7 - This Phase)                │
│    ├─ Compare with last broadcast state                 │
│    ├─ If changed → Encode delta                         │
│    └─ If unchanged → Skip broadcast (efficiency)        │
├──────────────────────────────────────────────────────────┤
│ 5. WebSocket Broadcasting                               │
│    ├─ Redis Pub/Sub → All backend nodes                 │
│    └─ WebSocket → All connected frontend clients        │
├──────────────────────────────────────────────────────────┤
│ 6. Frontend Rendering                                   │
│    ├─ Decode delta update                               │
│    ├─ Update vehicle state                              │
│    └─ Draw vehicle icon on pre-loaded map (SVG)         │
└──────────────────────────────────────────────────────────┘
```

### What Gets Broadcast

**NOT broadcast**: Raw GPS coordinates (lat, lng)  
**Broadcast**: Spatial references and progress

```json
{
  "vehicleId": "uuid",
  "routeId": "uuid",
  "progress": 0.42,           // 42% along route (from PostGIS)
  "segmentId": "segment_123", // Current road segment
  "speed": 45.5,
  "heading": 180,
  "timestamp": "2026-02-17T11:58:00Z"
}
```

**Why?**
- **Efficiency**: Progress value (0.0-1.0) is 8 bytes vs coordinates (16 bytes)
- **Privacy**: Frontend doesn't need exact GPS coordinates
- **Consistency**: All clients see same schematic view
- **Delta Encoding**: Only broadcast when progress changes significantly

### Integration with Coordinate Toggle

The [Coordinate Reception Toggle](file:///e:/Antigravity%20Projects/fleet-management/docs/implementations/feature-coordinate-reception-toggle.md) protects the entire pipeline:

- **Toggle OFF** → No coordinates accepted → No PostGIS matching → **No WebSocket broadcasts**
- **Result**: Complete shutdown of tracking pipeline, zero database/CPU usage

---

## Technical Strategy (Senior Level)

### 1. Clean Architecture & Use Cases
- **Logic Isolation**: Route matching is a pure domain function; `MatchingEngine` is an infrastructure implementation
- **Primary Use Case**: `ProcessLocationPingUseCase`
    1. Validate Ping (Idempotency + Rate Limit)
    2. Snap to Route (Phase 6 PostGISAdapter)
    3. Detect Anomaly (Signal Integrity logic)
    4. Broadcast Delta (WebSocketBroadcaster)

### 2. Structured Concurrency & Real-time Delivery
- Use Ktor's `launch` within the WebSocket session scope to manage concurrent stream traffic
- Ensure `RedisDeltaBroadcaster` uses a supervised coroutine scope to prevent subscriber leaks

### 3. Scaling & Observability

#### Backend Metrics (Micrometer)
- **`fleet_delta_efficiency`**: Ratio of incoming pings vs. actual delta broadcasts (target: >80% reduction)
- **`ws_active_sessions`**: Gauge for real-time tracking capacity
- **`ws_message_rate`**: Messages per second broadcasted
- **`ws_connection_duration`**: Histogram of session lifetimes

#### Alerting Thresholds
| Metric | Warning | Critical | Action |
|--------|---------|----------|--------|
| Delta efficiency | <70% | <50% | Review delta logic, check state cache |
| Active sessions | >500 | >1000 | Scale horizontally, add Redis Pub/Sub |
| Message rate | >1000/s | >5000/s | Batch updates, increase buffer |

#### Horizontal Scaling Strategy
- **Redis Pub/Sub**: Broadcast events across all backend nodes
- **Sticky Sessions**: Route WebSocket connections to same server (optional)
- **Load Balancer**: Distribute HTTP requests, maintain WebSocket affinity

---

## Dependencies & Setup

### build.gradle.kts
```kotlin
dependencies {
    // --- WebSocket Support ---
    implementation(libs.ktor.server.websockets) // Real-time bi-directional streaming
    
    // --- Redis (for horizontal scaling) ---
    implementation(libs.jedis) // Redis client for Pub/Sub
}
```

### libs.versions.toml
```toml
[versions]
jedis = "5.1.0"

[libraries]
jedis = { module = "redis.clients:jedis", version.ref = "jedis" }
```

---

## Technical Risks & Code-Level Solutions

### 1. State Synchronization (Redis Pub/Sub Solution)
**Risk**: Horizontal scaling on Render breaks WebSocket isolation.
**Solution**: Use Redis to broadcast events across all nodes.

#### Redis Broadcaster Implementation
```kotlin
// tracking/infrastructure/websocket/RedisDeltaBroadcaster.kt
class RedisDeltaBroadcaster(private val jedis: Jedis) {
    private val CHANNEL = "fleet_updates"

    fun publishUpdate(vehicleId: UUID, state: VehicleRouteState) {
        val message = Json.encodeToString(state)
        jedis.publish(CHANNEL, message)
    }

    fun subscribe(onUpdate: (VehicleRouteState) -> Unit) {
        thread {
            jedis.subscribe(object : JedisPubSub() {
                override fun onMessage(channel: String, message: String) {
                    val update = Json.decodeFromString<VehicleRouteState>(message)
                    onUpdate(update)
                }
            }, CHANNEL)
        }
    }
}
```

### 2. Serialization Compliance
**Risk**: Non-serializable DTOs fail at runtime.
**Solution**: Enforce `@Serializable` on all shared state models.

```kotlin
@Serializable
data class VehicleStateDelta(
    val vehicleId: @Serializable(with = UUIDSerializer::class) UUID,
    val progress: Double? = null,
    val bearing: Double? = null,
    val status: VehicleStatus? = null,
    val distanceFromRoute: Double? = null,
    val timestamp: Instant
) {
    fun hasChanges(): Boolean {
        return progress != null || bearing != null || status != null || distanceFromRoute != null
    }
}
```

---

## Code Implementation

### 1. Matching Engine (PostGIS Integration)
```kotlin
// tracking/infrastructure/spatial/MatchingEngine.kt
class MatchingEngine(private val database: Database) {
    suspend fun snapPointToRoute(routeId: UUID, lat: Double, lng: Double): Double {
        return database.transaction {
            // Uses the SpatialFunction created in Phase 6
            val progress = Routes
                .slice(SpatialFunctions.stLineLocatePoint(polyline, stPoint(lng, lat)))
                .select { Routes.id eq routeId }
                .single()[0] as Double
            progress
        }
    }
}
```

### 2. Delta Broadcaster (Backend)
```kotlin
// tracking/infrastructure/websocket/DeltaBroadcaster.kt
class DeltaBroadcaster {
    private val lastStates = ConcurrentHashMap<UUID, VehicleRouteState>()
    private val sessions = ConcurrentHashMap<String, DefaultWebSocketServerSession>()

    suspend fun broadcastIfChanged(vehicleId: UUID, newState: VehicleRouteState) {
        val lastState = lastStates[vehicleId]
        
        // Create delta payload (only changed fields)
        val delta = VehicleStateDelta(
            vehicleId = vehicleId,
            progress = if (lastState?.progress != newState.progress) newState.progress else null,
            bearing = if (lastState?.bearing != newState.bearing) newState.bearing else null,
            status = if (lastState?.status != newState.status) newState.status else null,
            distanceFromRoute = if (lastState?.distanceFromRoute != newState.distanceFromRoute) newState.distanceFromRoute else null,
            timestamp = newState.timestamp
        )
        
        // Only broadcast if there are changes
        if (delta.hasChanges()) {
            sessions.values.forEach { it.sendSerialized(delta) }
            lastStates[vehicleId] = newState
        }
    }
    
    fun addSession(sessionId: String, session: DefaultWebSocketServerSession) {
        sessions[sessionId] = session
    }
    
    fun removeSession(sessionId: String) {
        sessions.remove(sessionId)
    }
}
```

### 3. WebSocket Route Configuration
```kotlin
// tracking/infrastructure/websocket/WebSocketRoutes.kt
fun Route.configureWebSocketRoutes(broadcaster: DeltaBroadcaster) {
    webSocket("/v1/fleet/live") {
        val sessionId = UUID.randomUUID().toString()
        broadcaster.addSession(sessionId, this)
        
        try {
            // Send initial state for all vehicles
            sendInitialState()
            
            // Handle incoming frames (heartbeat)
            for (frame in incoming) {
                when (frame) {
                    is Frame.Ping -> send(Frame.Pong(frame.data))
                    is Frame.Text -> {
                        // Handle client messages if needed
                    }
                    else -> {}
                }
            }
        } finally {
            broadcaster.removeSession(sessionId)
        }
    }
}
```

### 4. Use Case Integration
```kotlin
// tracking/application/UpdateVehicleLocationUseCase.kt
class UpdateVehicleLocationUseCase(
    private val postGISAdapter: PostGISAdapter,
    private val broadcaster: DeltaBroadcaster,
    private val metrics: SpatialMetrics
) {
    suspend fun execute(ping: SensorPing) {
        val startTime = System.currentTimeMillis()
        
        // 1. Snap to route using PostGIS
        val snapResult = postGISAdapter.snapToRoute(
            vehicleId = ping.vehicleId,
            location = ping.location,
            routeId = getAssignedRoute(ping.vehicleId)
        )
        
        // 2. Determine status
        val status = when {
            snapResult.isOffRoute -> VehicleStatus.OFF_ROUTE
            ping.speed == null || ping.speed < 5.0 -> VehicleStatus.IDLE
            else -> VehicleStatus.IN_TRANSIT
        }
        
        // 3. Create state
        val state = VehicleRouteState(
            vehicleId = ping.vehicleId,
            routeId = snapResult.routeId,
            progress = snapResult.progress,
            bearing = ping.bearing,
            status = status,
            distanceFromRoute = snapResult.distanceFromRoute,
            timestamp = ping.timestamp
        )
        
        // 4. Broadcast delta
        broadcaster.broadcastIfChanged(ping.vehicleId, state)
        
        // 5. Record metrics
        val duration = System.currentTimeMillis() - startTime
        metrics.recordSnapDuration(duration)
    }
}
```

---

## Application Method

1. **Setup WebSocket Route**:
    ```kotlin
    routing {
        configureWebSocketRoutes(broadcaster)
    }
    ```

2. **Ingestion Hook**: In `SensorIngestionUseCase`, after persisting the ping, call:
    ```kotlin
    updateVehicleLocationUseCase.execute(ping)
    ```

---

## Testing Strategy

### 1. Unit Tests
```kotlin
class DeltaBroadcasterTest {
    @Test
    fun `should only broadcast changed fields`() {
        val broadcaster = DeltaBroadcaster()
        val vehicleId = UUID.randomUUID()
        
        // First update
        val state1 = VehicleRouteState(
            vehicleId = vehicleId,
            progress = 0.3,
            bearing = 45.0,
            status = VehicleStatus.IN_TRANSIT
        )
        broadcaster.broadcastIfChanged(vehicleId, state1)
        
        // Second update (only progress changed)
        val state2 = state1.copy(progress = 0.5)
        broadcaster.broadcastIfChanged(vehicleId, state2)
        
        // Verify only progress was in delta
        // (implementation would capture sent messages)
    }
}
```

### 2. WebSocket Integration Tests
```kotlin
class WebSocketIntegrationTest {
    @Test
    fun `should broadcast to all connected clients`() = testApplication {
        // Setup
        val client1 = createClient().webSocket("/v1/fleet/live")
        val client2 = createClient().webSocket("/v1/fleet/live")
        
        // Trigger update
        sendSensorPing(testVehicleId, testLocation)
        
        // Verify both clients receive update
        val message1 = client1.incoming.receive()
        val message2 = client2.incoming.receive()
        
        assertEquals(message1, message2)
    }
}
```

### 3. Performance Tests
```kotlin
class BroadcastPerformanceTest {
    @Test
    fun `should handle 1000 concurrent sessions`() {
        val broadcaster = DeltaBroadcaster()
        
        // Add 1000 mock sessions
        repeat(1000) { i ->
            broadcaster.addSession("session-$i", mockSession())
        }
        
        // Measure broadcast time
        val duration = measureTimeMillis {
            broadcaster.broadcastIfChanged(testVehicleId, testState)
        }
        
        assertTrue(duration < 100, "Broadcast should complete in <100ms")
    }
}
```

---

## Implementation Steps

### Phase 1: Delta Broadcasting (3-4 hours)
- [ ] Implement `DeltaBroadcaster` with delta logic
- [ ] Implement `VehicleStateDelta` model
- [ ] **Verification**: Unit tests pass

### Phase 2: WebSocket Route (2-3 hours)
- [ ] Configure `/v1/fleet/live` endpoint
- [ ] Add session management
- [ ] Add heartbeat handling
- [ ] **Verification**: Integration tests pass

### Phase 3: Use Case Integration (2-3 hours)
- [ ] Update `UpdateVehicleLocationUseCase`
- [ ] Integrate with PostGISAdapter (Phase 6)
- [ ] **Verification**: End-to-end test passes

### Phase 4: Redis Pub/Sub (3-4 hours)
- [ ] Implement `RedisDeltaBroadcaster`
- [ ] Configure Redis connection
- [ ] **Verification**: Multi-instance test passes

### Phase 5: Observability (2-3 hours)
- [ ] Add Micrometer metrics
- [ ] Configure alerting thresholds
- [ ] **Verification**: Metrics appear in `/metrics`

### Phase 6: Performance Testing (2-3 hours)
- [ ] Load test with 1000 concurrent sessions
- [ ] Verify delta efficiency >80%
- [ ] **Verification**: Performance targets met

**Total Estimated Effort**: 14-20 hours (2-3 days for a senior developer)

---

## 🏁 Definition of Done (Phase 7 - Backend)

### WebSocket Server
- [ ] `/v1/fleet/live` endpoint functional
- [ ] Session management (add/remove)
- [ ] Heartbeat/ping-pong handling
- [ ] Graceful connection cleanup

### Delta Broadcasting
- [ ] Delta encoding creates partial payloads
- [ ] Only changed fields included in delta
- [ ] Payload reduction >80% confirmed
- [ ] All connected clients receive updates

### Integration
- [ ] PostGISAdapter integration works (Phase 6)
- [ ] `UpdateVehicleLocationUseCase` orchestrates flow
- [ ] Sensor ping → WebSocket broadcast <500ms

### Horizontal Scaling
- [ ] Redis Pub/Sub broadcasts across nodes
- [ ] Multi-instance deployment tested
- [ ] Session affinity configured

### Observability
- [ ] Micrometer metrics exposed
- [ ] Delta efficiency tracked
- [ ] Active sessions monitored
- [ ] Alerting configured

### Testing
- [ ] Unit tests pass (delta logic)
- [ ] Integration tests pass (WebSocket)
- [ ] Performance tests pass (1000 sessions)
- [ ] Test coverage >80%

---

## Next Steps

After completing Phase 7 (Backend), proceed to:
1. **Web Frontend Implementation**: See [web-schematic-visualization.md](file:///e:/Antigravity%20Projects/fleet-management/docs/frontend-implementations/web-schematic-visualization.md)
2. **Android Driver App**: See [android-driver-app.md](file:///e:/Antigravity%20Projects/fleet-management/docs/frontend-implementations/android-driver-app.md)
2. **Phase 8 - Driving Event Detection**: Implement harsh brake/sharp turn detection
3. **Phase 9 - Analytics Dashboard**: Build route occupancy heatmaps

---

## References

- [Phase 6 - PostGIS Spatial Extensions](file:///e:/Antigravity%20Projects/fleet-management/docs/implementations/phase-6-postgis-spatial-extensions.md)
- [Web Frontend - Schematic Visualization](file:///e:/Antigravity%20Projects/fleet-management/docs/frontend-implementations/web-schematic-visualization.md)
- [Android Driver App](file:///e:/Antigravity%20Projects/fleet-management/docs/frontend-implementations/android-driver-app.md)
- [Feature - Coordinate Reception Toggle](file:///e:/Antigravity%20Projects/fleet-management/docs/implementations/feature-coordinate-reception-toggle.md)
- [Ktor WebSockets Documentation](https://ktor.io/docs/websocket.html)
- [Redis Pub/Sub](https://redis.io/docs/manual/pubsub/)
