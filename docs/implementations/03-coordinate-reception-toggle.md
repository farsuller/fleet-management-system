# Backend — Coordinate Reception Toggle

> **Status:** ✅ Complete  
> **Purpose:** Fleet manager globally pauses driver GPS pings. When disabled, `POST /v1/sensors/ping` returns `503 COORDINATE_RECEPTION_DISABLED`. Driver app stops sensors, retries after 5 min.

---

## HTTP Contract

```http
POST /v1/tracking/admin/coordinate-reception
Authorization: Bearer <fleet-manager-jwt>
{ "enabled": false }
→ 200 { "enabled": false, "updatedAt": "...", "updatedBy": "user-uuid" }

GET /v1/tracking/admin/coordinate-reception
→ 200 { "enabled": true, "updatedAt": "...", "updatedBy": "user-uuid" }
```

**Role guard:** `FLEET_MANAGER` or `ADMIN` only.

---

## DTOs

**File:** `modules/tracking/application/dto/CoordinateReceptionDto.kt`

```kotlin
@Serializable
data class CoordinateReceptionRequest(val enabled: Boolean)

@Serializable
data class CoordinateReceptionStatus(
    val enabled:   Boolean,
    @Contextual val updatedAt: Instant,
    val updatedBy: String,
)
```

---

## Service (Redis-backed, cluster-safe)

**File:** `modules/tracking/application/usecases/CoordinateReceptionService.kt`

```kotlin
open class CoordinateReceptionService(private val redisCache: RedisCacheManager) {

    open suspend fun isReceptionEnabled(): Boolean =
        redisCache.getOrSet(KEY_ENABLED, 0) { true } ?: true

    open suspend fun setReceptionEnabled(enabled: Boolean, updatedBy: String): CoordinateReceptionStatus {
        val now = Instant.now()
        redisCache.set(KEY_ENABLED, enabled, 0)
        redisCache.set(KEY_UPDATED_AT, now.toString(), 0)
        redisCache.set(KEY_UPDATED_BY, updatedBy, 0)
        return CoordinateReceptionStatus(enabled, now, updatedBy)
    }

    open suspend fun getStatus(): CoordinateReceptionStatus {
        val enabled = isReceptionEnabled()
        val updatedAtStr = redisCache.getOrSet(KEY_UPDATED_AT, 0) { Instant.EPOCH.toString() } ?: Instant.EPOCH.toString()
        val updatedBy = redisCache.getOrSet(KEY_UPDATED_BY, 0) { "system" } ?: "system"
        return CoordinateReceptionStatus(enabled, Instant.parse(updatedAtStr), updatedBy)
    }
}
```

---

## Routes in `TrackingRoutes.kt`

```kotlin
route("/v1/tracking/admin/coordinate-reception") {
    install(Authorization) {
        requiredRoles = listOf(UserRole.ADMIN, UserRole.FLEET_MANAGER)
    }
    get {
        call.respond(ApiResponse.success(receptionService.getStatus(), call.requestId))
    }
    post {
        val req    = call.receive<CoordinateReceptionRequest>()
        val userId = call.principal<JWTPrincipal>()?.payload?.getClaim("id")?.asString() ?: "unknown"
        call.respond(ApiResponse.success(receptionService.setReceptionEnabled(req.enabled, userId), call.requestId))
    }
}
```

The `POST /v1/sensors/ping` handler checks `receptionService.isReceptionEnabled()` first and returns `503` if disabled (see `01-sensor-ping-endpoint.md`).

---

## Checklist

- [x] Create `CoordinateReceptionDto.kt`
- [x] Create `CoordinateReceptionService.kt` (Redis-backed via `RedisCacheManager`)
- [x] Add GET + POST admin routes in `TrackingRoutes.kt`
- [x] Add role guard (`FLEET_MANAGER` / `ADMIN`) via `Authorization` plugin
- [x] Wire service into manual DI in `Routing.kt`
- [x] Guard `POST /v1/sensors/ping` with `503` when disabled
- [x] Integration test: toggle off → 503; toggle on → 202
