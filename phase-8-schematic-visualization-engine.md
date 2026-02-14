# Phase 8 — Schematic Visualization Engine

## Status
- Overall: **Planned**
- Implementation Date: TBD
- **Verification Responsibility**:
    - **Lead Developer (USER)**: Manual live-tracking verification (Frontend UI) & unit testing matching logic.
    - **Architect (Antigravity)**: Validate Delta-encoding efficiency and Redis Pub/Sub concurrency safety.

---

## Purpose
Implement the core logic that transforms raw GPS/Sensor pings into a schematic progress value (0.0-1.0) and broadcasts these updates via Delta-Encoded WebSockets. This is the "Special Sauce" that powers the operational command center.

---

## Technical Strategy (Senior Level)

### 1. Clean Architecture & Use Cases
- **Logic Isolation**: Route matching is a pure domain function; `MatchingEngine` is an infrastructure implementation.
- **Primary Use Case**: `ProcessLocationPingUseCase`
    1. Validate Ping (Idempotency + Rate Limit).
    2. Snap to Route (Phase 7 infra).
    3. Detect Anomaly (Signal Integrity logic).
    4. Broadcast Delta (Broadcaster infra).

### 2. Structured Concurrency & Real-time Delivery
- Use Ktor's `launch` within the WebSocket session scope to manage concurrent stream traffic.
- Ensure `RedisDeltaBroadcaster` uses a supervised coroutine scope to prevent subscriber leaks.

### 3. Scaling & Observability
- **Metrics**: 
    - `fleet_delta_efficiency`: Ratio of incoming pings vs. actual delta broadcasts.
    - `ws_active_sessions`: Gauge for real-time tracking capacity.
- **Horizontal Scaling**: Redis Pub/Sub acts as the "Backplane" for cross-node synchronization.

---

---

## Dependencies & Setup

### build.gradle.kts
```kotlin
dependencies {
    // --- WebSocket Support ---
    implementation(libs.ktor.server.websockets) // Real-time bi-directional streaming
}
```

---

## Technical Risks & Code-Level Solutions

### 1. State Synchronization (Redis Pub/Sub Solution)
**Risk**: Horizontal scaling on Render breaks WebSocket isolation.
**Solution**: Use Redis to broadcast events across all nodes.

#### Redis Broadcaster Implementation
```kotlin
class RedisDeltaBroadcaster(private val jedis: Jedis) {
    private val CHANNEL = "fleet_updates"

    fun publishUpdate(vehicleId: UUID, progress: Double) {
        val message = Json.encodeToString(VehicleUpdate(vehicleId, progress))
        jedis.publish(CHANNEL, message)
    }

    fun subscribe(onUpdate: (VehicleUpdate) -> Unit) {
        thread {
            jedis.subscribe(object : JedisPubSub() {
                override fun onMessage(channel: String, message: String) {
                    val update = Json.decodeFromString<VehicleUpdate>(message)
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
data class VehicleUpdate(
    val vehicleId: @Serializable(with = UUIDSerializer::class) UUID,
    val progress: Double,
    val timestamp: Long = System.currentTimeMillis()
)
```

---

## Code Implementation
...

### 1. Matching Engine (Logic)
```kotlin
class MatchingEngine(private val database: Database) {
    suspend fun snapPointToRoute(routeId: UUID, lat: Double, lng: Double): Double {
        return database.transaction {
            // Uses the SpatialFunction created in Phase 7
            val progress = Routes
                .slice(SpatialFunctions.stLineLocatePoint(polyline, stPoint(lng, lat)))
                .select { Routes.id eq routeId }
                .single()[0] as Double
            progress
        }
    }
}
```

### 2. Delta Broadcaster (Efficiency)
```kotlin
class DeltaBroadcaster {
    private val lastStates = ConcurrentHashMap<UUID, Double>()
    private val sessions = ConcurrentHashMap<UUID, DefaultWebSocketServerSession>()

    suspend fun broadcastIfChanged(vehicleId: UUID, progress: Double) {
        val lastProgress = lastStates[vehicleId] ?: -1.0
        // Delta threshold: 1% progress change
        if (abs(progress - lastProgress) > 0.01) {
            val update = VehicleUpdate(vehicleId, progress)
            sessions.values.forEach { it.sendSerialized(update) }
            lastStates[vehicleId] = progress
        }
    }
}
```

---

## Application Method

1. **Setup WebSocket Route**:
    ```kotlin
    routing {
        webSocket("/v1/fleet/live") {
            val sessionId = UUID.randomUUID()
            broadcaster.register(sessionId, this)
            try {
                for (frame in incoming) { /* Handle heartbeats */ }
            } finally {
                broadcaster.unregister(sessionId)
            }
        }
    }
    ```
2. **Ingestion Hook**: In `SensorIngestionUseCase`, after persisting the ping, call `matchingEngine.snapPointToRoute(...)` followed by `broadcaster.broadcastIfChanged(...)`.

---

---

## Technical Risks & Blockers

### 1. WebSocket Serialization
- **Issue**: Using `sendSerialized` requires the model to be annotated with `@Serializable`.
- **Mitigation**: Ensure `VehicleUpdate` and other DTOs in the `shared` module are properly annotated.

### 2. State Synchronization (Horizontal Scaling)
- **Issue**: If we deploy multiple instances on Render, WebSocket sessions are pinned to a specific server.
- **Mitigation**: 
    - Users must use sticky sessions or we rely on Redis Pub/Sub to broadcast updates across ALL active sessions regardless of which server the user is connected to.
    - Added Redis Pub/Sub to the implementation steps.

---

## Implementation Steps
1. [ ] **Matching Logic**: Implement and unit test `ST_LineLocatePoint` integration.
2. [ ] **WebSocket Setup**: Create the Ktor WebSocket route and session manager.
3. [ ] **Delta Encoding**: Implement `broadcastIfChanged` with a configurable threshold.
4. [ ] **Anomaly Detection**: Implement Signal Integrity check (GPS vs Accel).

---

## 🏁 Definition of Done (Phase 8)
- [ ] Vehicles snap correctly to routes within 50m accuracy.
- [ ] WebSocket clients receive updates within <500ms of a sensor ping.
- [ ] Bandwidth reduction confirmed via header/payload size audit.
- [ ] GPS spoofing detects simulated location jumps.
