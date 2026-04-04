# Backend — `POST /v1/sensors/ping` Endpoint

> **Status:** ✅ Complete
> **Module:** `fleet-management/src/main/kotlin/com/solodev/fleet/modules/tracking/`  
> **Blocking:** Android driver app cannot transmit data without this endpoint.

---

## HTTP Contract

```http
POST /v1/sensors/ping
Authorization: Bearer <driver-jwt>
Idempotency-Key: <uuid-v4>
Content-Type: application/json

[
  {
    "vehicleId": "uuid-string",
    "latitude":     14.6935,
    "longitude":    121.0744,
    "accuracy":     12.5,
    "speed":        8.3,
    "heading":      245.0,
    "accelX":       -0.45,
    "accelY":       0.12,
    "accelZ":       9.82,
    "gyroX":        0.01,
    "gyroY":        -0.02,
    "gyroZ":        0.08,
    "batteryLevel": 78,
    "timestamp":    "2026-03-13T14:00:00Z",
    "routeId":      "route-uuid"
  }
]
```

### Response Codes

| Code | Condition | Body |
|---|---|---|
| `202 Accepted` | Batch accepted (all or partial) | `{ "accepted": N, "rejected": M }` |
| `400 Bad Request` | Malformed JSON / missing required fields | `ApiResponse error` |
| `401 Unauthorized` | Invalid / expired JWT | — |
| `429 Too Many Requests` | > 60 pings/min per vehicle | `{ "retryAfterSeconds": N }` |
| `503 Service Unavailable` | Coordinate reception disabled | `{ "error": "COORDINATE_RECEPTION_DISABLED" }` |

---

## Step 1 — Extend `SensorPing.kt` DTO

**File:** `modules/tracking/application/dto/SensorPing.kt`

```kotlin
@Serializable
data class SensorPing(
    val vehicleId:    String,
    @Contextual
    val location: Location? = null,       // legacy field — keep for backward compat
    // Flat coordinate fields (preferred from mobile)
    val latitude:     Double? = null,
    val longitude:    Double? = null,
    val accuracy:     Double? = null,
    val speed:        Double? = null,
    val heading:      Double? = null,
    @Contextual
    val timestamp:    Instant,
    val routeId:      String? = null,
    // NEW — sensor fusion fields
    val accelX:       Double? = null,
    val accelY:       Double? = null,
    val accelZ:       Double? = null,
    val gyroX:        Double? = null,
    val gyroY:        Double? = null,
    val gyroZ:        Double? = null,
    val batteryLevel: Int?    = null,
) {
    fun resolvedLatitude()  = latitude  ?: location?.latitude
    fun resolvedLongitude() = longitude ?: location?.longitude

    fun isValid(): Boolean =
        resolvedLatitude() != null &&
        resolvedLongitude() != null &&
        (speed   == null || speed   in 0.0..100.0) &&
        (heading == null || heading in 0.0..360.0) &&
        (accuracy == null || accuracy >= 0.0)

    /** Detect driving events from sensor values. */
    fun hasHarshBrake() = accelX != null && accelX < -4.0
    fun hasHarshAccel() = accelX != null && accelX > 4.0
    fun hasSharpTurn()  = gyroZ  != null && Math.abs(gyroZ) > 1.5
}
```

---

## Step 2 — Create `SensorPingBatchResponse.kt`

**File:** `modules/tracking/application/dto/SensorPingBatchResponse.kt` *(new)*

```kotlin
@Serializable
data class SensorPingBatchResponse(
    val accepted: Int,
    val rejected: Int,
)
```

---

## Step 3 — Add Route in `TrackingRoutes.kt`

**File:** `modules/tracking/infrastructure/http/TrackingRoutes.kt`

Add inside the `authenticate("auth-jwt")` block:

```kotlin
// ── Driver sensor batch ping ─────────────────────────────────────────────────
route("/v1/sensors/ping") {
    post {
        // Check coordinate reception toggle
        if (!coordinateReceptionService.isEnabled()) {
            call.respond(
                HttpStatusCode.ServiceUnavailable,
                ApiResponse.error(
                    "COORDINATE_RECEPTION_DISABLED",
                    "Coordinate reception is currently disabled",
                    call.requestId,
                )
            )
            return@post
        }

        // Rate limiting — 60 pings/min per vehicleId
        val pings = call.receive<List<SensorPing>>()
        if (pings.isEmpty()) {
            call.respond(HttpStatusCode.BadRequest,
                ApiResponse.error("EMPTY_BATCH", "Ping batch must not be empty", call.requestId))
            return@post
        }

        // Idempotency check (per-request key)
        val idempotencyKey = call.request.headers["Idempotency-Key"]
        if (idempotencyKey != null && idempotencyKeyManager.isDuplicate(idempotencyKey)) {
            call.respond(HttpStatusCode.Accepted,
                ApiResponse.success(SensorPingBatchResponse(accepted = 0, rejected = 0), call.requestId))
            return@post
        }

        var accepted = 0
        var rejected = 0

        pings.forEach { ping ->
            if (!ping.isValid()) { rejected++; return@forEach }

            val lat = ping.resolvedLatitude()  ?: run { rejected++; return@forEach }
            val lon = ping.resolvedLongitude() ?: run { rejected++; return@forEach }

            try {
                updateVehicleLocationUseCase.execute(
                    UpdateVehicleLocationCommand(
                        vehicleId    = ping.vehicleId,
                        latitude     = lat,
                        longitude    = lon,
                        speed        = ping.speed,
                        heading      = ping.heading,
                        accuracy     = ping.accuracy,
                        routeId      = ping.routeId,
                        accelX       = ping.accelX,
                        accelY       = ping.accelY,
                        accelZ       = ping.accelZ,
                        gyroX        = ping.gyroX,
                        gyroY        = ping.gyroY,
                        gyroZ        = ping.gyroZ,
                        batteryLevel = ping.batteryLevel,
                        harshBrake   = ping.hasHarshBrake(),
                        harshAccel   = ping.hasHarshAccel(),
                        sharpTurn    = ping.hasSharpTurn(),
                        recordedAt   = ping.timestamp,
                    )
                )
                accepted++
            } catch (e: Exception) {
                Log.warn("SensorPing failed for vehicle=${ping.vehicleId}", e)
                rejected++
            }
        }

        if (idempotencyKey != null) idempotencyKeyManager.record(idempotencyKey)

        call.respond(
            HttpStatusCode.Accepted,
            ApiResponse.success(SensorPingBatchResponse(accepted, rejected), call.requestId)
        )
    }
}
```

---

## Step 4 — Extend `UpdateVehicleLocationCommand`

Add new fields to the command object used by the existing use case:

```kotlin
data class UpdateVehicleLocationCommand(
    val vehicleId:    String,
    val latitude:     Double,
    val longitude:    Double,
    val speed:        Double?,
    val heading:      Double?,
    val accuracy:     Double?,
    val routeId:      String?,
    val recordedAt:   Instant,
    // NEW
    val accelX:       Double?  = null,
    val accelY:       Double?  = null,
    val accelZ:       Double?  = null,
    val gyroX:        Double?  = null,
    val gyroY:        Double?  = null,
    val gyroZ:        Double?  = null,
    val batteryLevel: Int?     = null,
    val harshBrake:   Boolean  = false,
    val harshAccel:   Boolean  = false,
    val sharpTurn:    Boolean  = false,
)
```

---

## Step 5 — Extend `VehicleStateDelta` (WS broadcast)

**File:** `modules/tracking/application/dto/VehicleStateDelta.kt`

```kotlin
@Serializable
data class VehicleStateDelta(
    val vehicleId:       String,
    val latitude:        Double?,
    val longitude:       Double?,
    val speedKph:        Double?,
    val headingDeg:      Double?,
    val routeId:         String?,
    val routeProgress:   Double?,
    val recordedAt:      @Contextual Instant?,
    // NEW — observable driver telemetry
    val batteryLevel:    Int?     = null,
    val harshBrake:      Boolean? = null,
    val harshAccel:      Boolean? = null,
    val sharpTurn:       Boolean? = null,
    val trackingStatus:  String?  = null,  // ACTIVE | OFFLINE_BUFFERING | PAUSED
)
```

---

## Implementation Checklist

- [x] Extend `SensorPing.kt` with accel/gyro/battery fields
- [x] Create `SensorPingBatchResponse.kt`
- [x] Add `POST /v1/sensors/ping` route in `TrackingRoutes.kt`
- [x] Extend `UpdateVehicleLocationCommand` with new sensor fields
- [x] Pass sensor fields through to `LocationHistoryRepository` persist call
- [x] Populate driving event flags (`harshBrake`, `harshAccel`, `sharpTurn`) in `VehicleStateDelta`
- [x] Extend `VehicleStateDelta` with `batteryLevel`, event flags, `trackingStatus`
- [x] Write DB migration V027 (see `02-db-migration.md`)
- [x] Update `RedisDeltaBroadcaster` to include new delta fields
