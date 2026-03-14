# Backend — Driver Shift Management Endpoints

> **Status:** ✅ Complete
> **Module:** `modules/drivers/infrastructure/http/DriverRoutes.kt`  
> **Purpose:** Track when a driver starts/ends a work shift. Required for the backoffice to show current driver status per vehicle, and to correlate tracking data with work sessions.

---

## HTTP Contract

```http
-- Start shift
POST /v1/drivers/{id}/shift/start
Authorization: Bearer <driver-jwt>
{ "vehicleId": "uuid", "notes": "Optional notes" }
→ 201 { shift object }

-- End shift
POST /v1/drivers/{id}/shift/end
Authorization: Bearer <driver-jwt>
{ "notes": "Optional end-of-shift notes" }
→ 200 { shift object with endedAt }

-- Current shift (polling by backoffice or driver app)
GET /v1/drivers/{id}/shift
Authorization: Bearer <jwt>
→ 200 { active shift or null }

-- Shift history
GET /v1/drivers/{id}/shift/history
Authorization: Bearer <jwt>
→ 200 [ list of completed shifts ]
```

---

## Domain Model

**File:** `modules/drivers/domain/model/DriverShift.kt` *(new)*

```kotlin
data class DriverShift(
    val id:        UUID,
    val driverId:  String,
    val vehicleId: String,
    val startedAt: Instant,
    val endedAt:   Instant? = null,    // null = active shift
    val notes:     String?  = null,
)
```

---

## DB Migration

**File:** `src/main/resources/db/migration/V024__create_driver_shifts.sql`

```sql
CREATE TABLE IF NOT EXISTS driver_shifts (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    driver_id   VARCHAR(255) NOT NULL REFERENCES drivers(id),
    vehicle_id  VARCHAR(255) NOT NULL,
    started_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ended_at    TIMESTAMPTZ,
    notes       TEXT,
    CONSTRAINT one_active_shift_per_driver
        EXCLUDE USING GIST (driver_id WITH =)
        WHERE (ended_at IS NULL)
);

CREATE INDEX idx_driver_shifts_driver ON driver_shifts(driver_id, started_at DESC);
CREATE INDEX idx_driver_shifts_vehicle ON driver_shifts(vehicle_id, started_at DESC);
```

---

## Exposed Table

**File:** `modules/drivers/infrastructure/persistence/DriverShiftsTable.kt` *(new)*

```kotlin
object DriverShiftsTable : Table("driver_shifts") {
    val id        = uuid("id").defaultExpression(CustomFunction("gen_random_uuid", UUIDColumnType()))
    val driverId  = varchar("driver_id", 255)
    val vehicleId = varchar("vehicle_id", 255)
    val startedAt = timestamp("started_at")
    val endedAt   = timestamp("ended_at").nullable()
    val notes     = text("notes").nullable()

    override val primaryKey = PrimaryKey(id)
}
```

---

## Route Additions in `DriverRoutes.kt`

```kotlin
route("/{id}/shift") {

    // Current active shift
    get {
        val driverId = call.parameters["id"] ?: return@get call.respond(
            HttpStatusCode.BadRequest, ApiResponse.error("MISSING_ID", "Driver ID required", call.requestId)
        )
        val shift = driverRepository.findActiveShift(driverId)
        call.respond(ApiResponse.success(shift?.let { ShiftResponse.fromDomain(it) }, call.requestId))
    }

    // Start shift
    post("start") {
        val driverId = call.parameters["id"] ?: return@post call.respond(
            HttpStatusCode.BadRequest, ApiResponse.error("MISSING_ID", "Driver ID required", call.requestId)
        )
        try {
            val req   = call.receive<StartShiftRequest>()
            val shift = driverRepository.startShift(driverId, req.vehicleId, req.notes)
            call.respond(HttpStatusCode.Created, ApiResponse.success(ShiftResponse.fromDomain(shift), call.requestId))
        } catch (e: IllegalStateException) {
            call.respond(HttpStatusCode.Conflict,
                ApiResponse.error("SHIFT_ACTIVE", e.message ?: "Driver already has an active shift", call.requestId))
        }
    }

    // End shift
    post("end") {
        val driverId = call.parameters["id"] ?: return@post call.respond(
            HttpStatusCode.BadRequest, ApiResponse.error("MISSING_ID", "Driver ID required", call.requestId)
        )
        val req   = call.receive<EndShiftRequest>()
        val shift = driverRepository.endShift(driverId, req.notes)
            ?: return@post call.respond(HttpStatusCode.NotFound,
                ApiResponse.error("NO_ACTIVE_SHIFT", "Driver has no active shift", call.requestId))
        call.respond(ApiResponse.success(ShiftResponse.fromDomain(shift), call.requestId))
    }

    // Shift history
    get("history") {
        val driverId = call.parameters["id"] ?: return@get call.respond(
            HttpStatusCode.BadRequest, ApiResponse.error("MISSING_ID", "Driver ID required", call.requestId)
        )
        val history = driverRepository.findShiftHistory(driverId)
        call.respond(ApiResponse.success(history.map { ShiftResponse.fromDomain(it) }, call.requestId))
    }
}
```

---

## DTOs

```kotlin
@Serializable
data class StartShiftRequest(val vehicleId: String, val notes: String? = null)

@Serializable
data class EndShiftRequest(val notes: String? = null)

@Serializable
data class ShiftResponse(
    val id:        String,
    val driverId:  String,
    val vehicleId: String,
    @Contextual val startedAt: Instant,
    @Contextual val endedAt:   Instant?,
    val notes:     String?,
    val isActive:  Boolean,
) {
    companion object {
        fun fromDomain(s: DriverShift) = ShiftResponse(
            id        = s.id.toString(),
            driverId  = s.driverId,
            vehicleId = s.vehicleId,
            startedAt = s.startedAt,
            endedAt   = s.endedAt,
            notes     = s.notes,
            isActive  = s.endedAt == null,
        )
    }
}
```

---

## Checklist

- [ ] Create `DriverShift.kt` domain model
- [ ] Create `V024__create_driver_shifts.sql` migration
- [ ] Create `DriverShiftsTable.kt` Exposed object
- [ ] Add `findActiveShift`, `startShift`, `endShift`, `findShiftHistory` to `DriverRepository`
- [ ] Implement repository methods in `DriverRepositoryImpl`
- [ ] Add shift routes (`start`, `end`, current, history) in `DriverRoutes.kt`
- [ ] Add `StartShiftRequest`, `EndShiftRequest`, `ShiftResponse` DTOs
