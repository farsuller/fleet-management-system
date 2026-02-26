# Web Frontend — Schematic Visualization (Kotlin/JS + Compose for Web)

## Status
- Overall: **Ready for Implementation**
- Refined Date: 2026-02-26
- **Verification Responsibility**:
    - **Lead Developer (USER)**: Frontend unit tests, visual regression tests, E2E tests
    - **Architect (Antigravity)**: Performance benchmarks (FPS, render time, bundle size)

---

## Purpose
Implement the frontend visualization system that renders real-time vehicle tracking on a custom schematic map using SVG. This system receives delta-encoded WebSocket updates from the backend and renders smooth, animated vehicle movements along routes.

---

## Technical Strategy

### 1. Technology Stack
- **Kotlin/JS**: Type-safe frontend with code sharing from backend
- **Compose for Web**: Declarative UI with reactive state management
- **Ktor Client**: WebSocket communication with automatic reconnection
- **SVG**: Custom schematic rendering without third-party map libraries

### 2. Architecture Principles
- **Reactive State**: MutableStateFlow triggers automatic UI recomposition
- **Delta Decoding**: Merge partial updates to minimize bandwidth
- **Smooth Animations**: 500ms transitions using Compose animation APIs
- **Performance First**: Target 55+ FPS with 50 vehicles, <16ms render time

### 3. Observability

#### Frontend Metrics (Performance API)
```kotlin
// web/src/jsMain/kotlin/com/solodev/fleet/web/metrics/FrontendMetrics.kt
object FrontendMetrics {
    fun recordRenderTime(vehicleId: UUID, duration: Double) {
        performance.measure("vehicle_render", mapOf(
            "vehicleId" to vehicleId.toString(),
            "duration" to duration
        ))
    }
    
    fun recordWebSocketLatency(latency: Double) {
        performance.measure("ws_latency", mapOf("latency" to latency))
    }
    
    fun recordAnimationFrame(fps: Double) {
        performance.measure("animation_fps", mapOf("fps" to fps))
    }
}
```

#### Key Metrics
| Metric | Target | Critical | Action |
|--------|--------|----------|--------|
| **FPS** | >55 fps | <30 fps | Reduce vehicle count, optimize SVG |
| **Render Time** | <16ms | >33ms | Batch updates, use requestAnimationFrame |
| **WebSocket Latency** | <100ms | >500ms | Check network, increase buffer |
| **Time to First Render** | <2s | >5s | Optimize bundle size, lazy load |
| **Memory Usage** | <100MB | >500MB | Clear old vehicle states, GC tuning |

#### Error Tracking (Sentry)
```kotlin
object ErrorTracker {
    fun init() {
        Sentry.init { options ->
            options.dsn = "https://your-sentry-dsn"
            options.environment = "production"
            options.tracesSampleRate = 0.1
            
            options.setBeforeSend { event, hint ->
                event.setTag("component", "visualization-engine")
                event.setTag("browser", window.navigator.userAgent)
                event
            }
        }
    }
    
    fun captureWebSocketError(error: Throwable, context: Map<String, Any>) {
        Sentry.captureException(error) {
            it.setTag("error_type", "websocket")
            context.forEach { (key, value) ->
                it.setExtra(key, value.toString())
            }
        }
    }
}
```

---

## Dependencies & Setup

### Project Structure
```
web/
├── src/
│   └── jsMain/
│       └── kotlin/
│           └── com/solodev/fleet/web/
│               ├── App.kt                    # Main Compose app
│               ├── components/
│               │   ├── FleetMap.kt           # SVG route renderer
│               │   ├── VehicleIcon.kt        # Animated vehicle marker
│               │   └── RouteLayer.kt         # Route polyline rendering
│               ├── state/
│               │   ├── FleetState.kt         # MutableStateFlow for vehicles
│               │   └── WebSocketClient.kt    # WS connection manager
│               ├── utils/
│               │   ├── SvgUtils.kt           # Path generation helpers
│               │   └── DeltaDecoder.kt       # Delta decoding logic
│               └── metrics/
│                   └── FrontendMetrics.kt    # Performance tracking
└── build.gradle.kts
```

### web/build.gradle.kts
```kotlin
plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("org.jetbrains.compose") version "1.5.11"
}

kotlin {
    js(IR) {
        browser {
            commonWebpackConfig {
                cssSupport {
                    enabled.set(true)
                }
            }
            testTask {
                useKarma {
                    useChromeHeadless()
                }
            }
        }
        binaries.executable()
    }
    
    sourceSets {
        val jsMain by getting {
            dependencies {
                implementation(compose.html.core)
                implementation(compose.runtime)
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-js:1.7.3")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json-js:1.6.0")
                implementation("io.ktor:ktor-client-js:3.0.3")
                implementation("io.ktor:ktor-client-websockets:3.0.3")
            }
        }
        
        val jsTest by getting {
            dependencies {
                implementation(kotlin("test-js"))
            }
        }
    }
}
```

### Development Commands
```bash
# Start development server with hot reload
./gradlew :web:jsBrowserDevelopmentRun --continuous

# Build production bundle
./gradlew :web:jsBrowserProductionWebpack

# Run frontend tests
./gradlew :web:jsTest

# Bundle size analysis
./gradlew :web:jsBrowserProductionWebpack --info | grep "bundle size"
```

---

## Code Implementation

### 1. WebSocket Client with Delta Decoding
```kotlin
// web/src/jsMain/kotlin/com/solodev/fleet/web/state/WebSocketClient.kt
import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class WebSocketClient(
    private val serverUrl: String = "ws://localhost:8080/v1/fleet/live"
) {
    private val client = HttpClient {
        install(WebSockets)
    }
    
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    
    private val _vehicleUpdates = MutableSharedFlow<VehicleStateDelta>(replay = 0)
    val vehicleUpdates: SharedFlow<VehicleStateDelta> = _vehicleUpdates.asSharedFlow()
    
    private var reconnectJob: Job? = null
    private var session: DefaultClientWebSocketSession? = null
    
    suspend fun connect() {
        if (_connectionState.value == ConnectionState.CONNECTED) return
        
        _connectionState.value = ConnectionState.CONNECTING
        
        try {
            client.webSocket(serverUrl) {
                session = this
                _connectionState.value = ConnectionState.CONNECTED
                console.log("WebSocket connected")
                
                // Start heartbeat
                launch { sendHeartbeat() }
                
                // Process incoming messages
                for (frame in incoming) {
                    when (frame) {
                        is Frame.Text -> {
                            val text = frame.readText()
                            handleMessage(text)
                        }
                        is Frame.Close -> {
                            console.log("WebSocket closed: ${frame.readReason()}")
                            break
                        }
                        else -> {}
                    }
                }
            }
        } catch (e: Exception) {
            console.error("WebSocket error: ${e.message}")
            _connectionState.value = ConnectionState.ERROR
            scheduleReconnect()
        } finally {
            _connectionState.value = ConnectionState.DISCONNECTED
            session = null
        }
    }
    
    private suspend fun handleMessage(text: String) {
        try {
            val startTime = performance.now()
            
            // Decode delta payload
            val delta = Json.decodeFromString<VehicleStateDelta>(text)
            _vehicleUpdates.emit(delta)
            
            // Record WebSocket latency
            val latency = performance.now() - startTime
            FrontendMetrics.recordWebSocketLatency(latency)
            
        } catch (e: Exception) {
            console.error("Failed to decode message: ${e.message}")
            ErrorTracker.captureWebSocketError(e, mapOf("message" to text))
        }
    }
    
    private suspend fun sendHeartbeat() {
        while (session?.isActive == true) {
            try {
                session?.send(Frame.Ping(ByteArray(0)))
                delay(30_000) // 30 seconds
            } catch (e: Exception) {
                console.error("Heartbeat failed: ${e.message}")
                break
            }
        }
    }
    
    private fun scheduleReconnect() {
        reconnectJob?.cancel()
        reconnectJob = CoroutineScope(Dispatchers.Default).launch {
            delay(5_000) // Wait 5 seconds before reconnecting
            console.log("Attempting to reconnect...")
            connect()
        }
    }
    
    fun disconnect() {
        reconnectJob?.cancel()
        session?.cancel()
        _connectionState.value = ConnectionState.DISCONNECTED
    }
}

enum class ConnectionState {
    DISCONNECTED, CONNECTING, CONNECTED, ERROR
}
```

### 2. Delta Decoder (State Merger)
```kotlin
// web/src/jsMain/kotlin/com/solodev/fleet/web/utils/DeltaDecoder.kt
object DeltaDecoder {
    /**
     * Merge delta update into existing vehicle state.
     * Only updates fields that are present in the delta.
     */
    fun merge(current: VehicleRouteState?, delta: VehicleStateDelta): VehicleRouteState {
        return VehicleRouteState(
            vehicleId = delta.vehicleId,
            routeId = delta.routeId ?: current?.routeId ?: throw IllegalStateException("Unknown route for vehicle"),
            progress = delta.progress ?: current?.progress ?: 0.0,
            bearing = delta.bearing ?: current?.bearing,
            status = delta.status ?: current?.status ?: VehicleStatus.IDLE,
            distanceFromRoute = delta.distanceFromRoute ?: current?.distanceFromRoute,
            timestamp = delta.timestamp
        )
    }
}
```

### 3. Fleet State Manager
```kotlin
// web/src/jsMain/kotlin/com/solodev/fleet/web/state/FleetState.kt
class FleetState(private val wsClient: WebSocketClient) {
    private val _vehicles = MutableStateFlow<Map<UUID, VehicleRouteState>>(emptyMap())
    val vehicles: StateFlow<Map<UUID, VehicleRouteState>> = _vehicles.asStateFlow()
    
    private val _routes = MutableStateFlow<Map<UUID, Route>>(emptyMap())
    val routes: StateFlow<Map<UUID, Route>> = _routes.asStateFlow()
    
    init {
        // Listen to WebSocket updates
        CoroutineScope(Dispatchers.Default).launch {
            wsClient.vehicleUpdates.collect { delta ->
                updateVehicle(delta)
            }
        }
    }
    
    private fun updateVehicle(delta: VehicleStateDelta) {
        _vehicles.update { currentVehicles ->
            val current = currentVehicles[delta.vehicleId]
            val updated = DeltaDecoder.merge(current, delta)
            currentVehicles + (delta.vehicleId to updated)
        }
    }
    
    suspend fun loadRoutes(routes: List<Route>) {
        _routes.value = routes.associateBy { it.id }
    }
}
```

### 4. SVG Rendering Engine
```kotlin
// web/src/jsMain/kotlin/com/solodev/fleet/web/components/FleetMap.kt
import androidx.compose.runtime.*
import org.jetbrains.compose.web.svg.*

@Composable
fun FleetMap(
    routes: Map<UUID, Route>,
    vehicles: Map<UUID, VehicleRouteState>,
    viewBox: String = "0 0 1000 1000"
) {
    Svg({
        attr("viewBox", viewBox)
        attr("width", "100%")
        attr("height", "100%")
        style {
            property("background-color", "#1a1a1a")
        }
    }) {
        // Render routes
        routes.values.forEach { route ->
            RouteLayer(route)
        }
        
        // Render vehicles
        vehicles.values.forEach { vehicle ->
            val route = routes[vehicle.routeId]
            if (route != null) {
                VehicleIcon(vehicle = vehicle, route = route)
            }
        }
    }
}

@Composable
fun RouteLayer(route: Route) {
    val pathData = remember(route.polyline) {
        SvgUtils.polylineToPath(route.polyline)
    }
    
    Path({
        attr("d", pathData)
        attr("stroke", "#4CAF50")
        attr("stroke-width", "3")
        attr("fill", "none")
    })
}

@Composable
fun VehicleIcon(vehicle: VehicleRouteState, route: Route) {
    val position = remember(vehicle.progress, route.polyline) {
        SvgUtils.getPointAtProgress(route.polyline, vehicle.progress)
    }
    
    // Animate position changes
    var animatedX by remember { mutableStateOf(position.x) }
    var animatedY by remember { mutableStateOf(position.y) }
    
    LaunchedEffect(position) {
        animateFloatAsState(
            targetValue = position.x,
            animationSpec = tween(durationMillis = 500)
        ).collect { animatedX = it }
        
        animateFloatAsState(
            targetValue = position.y,
            animationSpec = tween(durationMillis = 500)
        ).collect { animatedY = it }
    }
    
    G({
        attr("transform", "translate($animatedX, $animatedY) rotate(${vehicle.bearing ?: 0})")
    }) {
        Polygon({
            attr("points", "0,-10 -6,10 6,10")
            attr("fill", when (vehicle.status) {
                VehicleStatus.IN_TRANSIT -> "#2196F3"
                VehicleStatus.IDLE -> "#FFC107"
                VehicleStatus.OFF_ROUTE -> "#F44336"
                else -> "#9E9E9E"
            })
            attr("stroke", "#FFFFFF")
            attr("stroke-width", "1")
        })
    }
}
```

### 5. SVG Utilities
```kotlin
// web/src/jsMain/kotlin/com/solodev/fleet/web/utils/SvgUtils.kt
import org.w3c.dom.svg.SVGPathElement

object SvgUtils {
    /**
     * Convert PostGIS LineString to SVG path data.
     */
    fun polylineToPath(lineString: String): String {
        val coords = lineString
            .removePrefix("LINESTRING(")
            .removeSuffix(")")
            .split(",")
            .map { it.trim().split(" ") }
        
        if (coords.isEmpty()) return ""
        
        val first = coords.first()
        return buildString {
            append("M ${first[0]} ${first[1]}")
            coords.drop(1).forEach { coord ->
                append(" L ${coord[0]} ${coord[1]}")
            }
        }
    }
    
    /**
     * Get point at specific progress (0.0 to 1.0) along SVG path.
     */
    fun getPointAtProgress(lineString: String, progress: Double): Point {
        val pathData = polylineToPath(lineString)
        val path = document.createElementNS("http://www.w3.org/2000/svg", "path") as SVGPathElement
        path.setAttribute("d", pathData)
        
        val totalLength = path.getTotalLength()
        val targetLength = totalLength * progress.coerceIn(0.0, 1.0)
        val point = path.getPointAtLength(targetLength)
        
        return Point(point.x, point.y)
    }
}

data class Point(val x: Double, val y: Double)
```

---

## Testing Strategy

### 1. Unit Tests
```kotlin
import com.solodev.fleet.shared.VehicleRouteState
import com.solodev.fleet.shared.VehicleStatus
import com.solodev.fleet.web.utils.DeltaDecoder
import com.solodev.fleet.web.utils.VehicleStateDelta
import kotlinx.datetime.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class DeltaDecoderTest {
    @Test
    fun `should merge delta with null current state`() {
        val delta = VehicleStateDelta(
            vehicleId = UUID.randomUUID(),
            progress = 0.5,
            bearing = 90.0,
            status = VehicleStatus.IN_TRANSIT,
            timestamp = Instant.now()
        )
        
        val merged = DeltaDecoder.merge(null, delta)
        assertEquals(0.5, merged.progress)
    }
}
```

### 2. WebSocket Integration Tests
```kotlin
class WebSocketClientTest {
    @Test
    fun `should reconnect after connection loss`() = runTest {
        val client = WebSocketClient()
        client.connect()
        
        mockServer.close()
        delay(100)
        assertEquals(ConnectionState.DISCONNECTED, client.connectionState.value)
        
        delay(6000)
        assertEquals(ConnectionState.CONNECTED, client.connectionState.value)
    }
}
```

### 3. Performance Tests
```kotlin
class RenderPerformanceTest {
    @Test
    fun `should maintain 55+ FPS with 50 vehicles`() {
        val fps = measureFPS(vehicleCount = 50, duration = 5000)
        assertTrue(fps >= 55.0)
    }
}
```

### 4. E2E Tests (Playwright)
```kotlin
class FleetTrackingE2ETest {
    @Test
    fun `should display real-time vehicle tracking`() {
        page.navigate("http://localhost:8080/fleet-map")
        sendSensorPing(vehicleId, location)
        page.waitForSelector("g[data-vehicle-id='$vehicleId']", timeout = 1000)
        assertEquals("#2196F3", page.locator("polygon").getAttribute("fill"))
    }
}
```

---

## Implementation Steps

### Phase 1: Project Setup (2-3 hours)
- [ ] Create `web/` module with Kotlin/JS + Compose
- [ ] Configure webpack and dependencies
- [ ] **Verification**: Dev server runs

### Phase 2: WebSocket Client (3-4 hours)
- [ ] Implement WebSocketClient with reconnection
- [ ] Implement DeltaDecoder
- [ ] **Verification**: Integration tests pass

### Phase 3: State Management (2-3 hours)
- [ ] Implement FleetState with MutableStateFlow
- [ ] **Verification**: Unit tests pass

### Phase 4: SVG Rendering (4-5 hours)
- [ ] Implement SvgUtils
- [ ] Implement FleetMap, RouteLayer, VehicleIcon
- [ ] **Verification**: Visual tests show animated vehicles

### Phase 5: Testing (4-5 hours)
- [ ] Write unit, integration, performance, E2E tests
- [ ] **Verification**: All tests pass, >75% coverage

### Phase 6: Deployment (1-2 hours)
- [ ] Build production bundle
- [ ] Deploy to CDN/hosting
- [ ] **Verification**: Production URL loads

**Total Effort**: 20-30 hours

---

## 🏁 Definition of Done

### Frontend Setup
- [ ] Kotlin/JS module configured
- [ ] Production bundle <500KB gzipped

### WebSocket
- [ ] Connects to `/v1/fleet/live`
- [ ] Reconnection works (5s delay)
- [ ] Delta decoding merges updates

### SVG Rendering
- [ ] Routes render from LineString
- [ ] Vehicles positioned via `getPointAtLength()`
- [ ] Smooth 500ms animations
- [ ] Status-based coloring

### Performance
- [ ] 55+ FPS with 50 vehicles
- [ ] <16ms render time
- [ ] <100ms WebSocket latency

### Testing
- [ ] All tests pass
- [ ] >75% coverage

---

## References

- [Kotlin/JS Documentation](https://kotlinlang.org/docs/js-overview.html)
- [Compose for Web](https://github.com/JetBrains/compose-multiplatform#compose-for-web)
- [Phase 7 - Backend WebSocket](file:///e:/Antigravity%20Projects/fleet-management/docs/implementations/phase-7-schematic-visualization-engine.md)
- [Feature - Coordinate Reception Toggle](file:///e:/Antigravity%20Projects/fleet-management/docs/implementations/feature-coordinate-reception-toggle.md)
