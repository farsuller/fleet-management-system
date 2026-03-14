# Backend — DB Migration: Sensor Fields in `location_history`

> **Status:** ✅ Complete  
> **File:** `src/main/resources/db/migration/V027__add_sensor_fields_to_location_history.sql`

---

## Migration SQL

```sql
-- V027__add_sensor_fields_to_location_history.sql
-- Add accelerometer, gyroscope, battery, and driving-event columns
-- to the location_history table for driver telemetry storage.

ALTER TABLE location_history
    ADD COLUMN IF NOT EXISTS accel_x       DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS accel_y       DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS accel_z       DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS gyro_x        DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS gyro_y        DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS gyro_z        DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS battery_level SMALLINT,
    ADD COLUMN IF NOT EXISTS harsh_brake   BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS harsh_accel   BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS sharp_turn    BOOLEAN NOT NULL DEFAULT FALSE;

-- Partial index for quick driving-event queries
CREATE INDEX IF NOT EXISTS idx_location_history_harsh_events
    ON location_history (vehicle_id, timestamp)
    WHERE harsh_brake OR harsh_accel OR sharp_turn;

-- Index for battery monitoring queries per vehicle
CREATE INDEX IF NOT EXISTS idx_location_history_battery
    ON location_history (vehicle_id, timestamp)
    WHERE battery_level IS NOT NULL;
```

---

## Exposed ORM — `LocationHistoryTable` Update

**File:** `modules/tracking/infrastructure/persistence/LocationHistoryRepository.kt` (Table defined in same file)

```kotlin
object LocationHistoryTable : Table("location_history") {
    val id = uuid("id")
    val vehicleId = varchar("vehicle_id", 36)
    val routeId = varchar("route_id", 36).nullable()
    val progress = double("progress")
    val segmentId = varchar("segment_id", 50).nullable()
    val speed = double("speed")
    val heading = double("heading")
    val status = varchar("status", 20)
    val distanceFromRoute = double("distance_from_route")
    val latitude = double("latitude")
    val longitude = double("longitude")
    val timestamp = timestamp("timestamp")
    val createdAt = timestamp("created_at").default(Instant.now())

    // NEW columns
    val accelX       = double("accel_x").nullable()
    val accelY       = double("accel_y").nullable()
    val accelZ       = double("accel_z").nullable()
    val gyroX        = double("gyro_x").nullable()
    val gyroY        = double("gyro_y").nullable()
    val gyroZ        = double("gyro_z").nullable()
    val batteryLevel = short("battery_level").nullable()
    val harshBrake   = bool("harsh_brake").default(false)
    val harshAccel   = bool("harsh_accel").default(false)
    val sharpTurn    = bool("sharp_turn").default(false)

    override val primaryKey = PrimaryKey(id)
}
```

---

## Repository Update

**File:** `modules/tracking/infrastructure/persistence/LocationHistoryRepository.kt`

```kotlin
LocationHistoryTable.insert {
    it[id]        = UUID.randomUUID()
    it[vehicleId] = state.vehicleId
    it[routeId]   = state.routeId
    it[progress]  = state.progress
    // ... other fields
    it[timestamp] = state.timestamp
    // NEW
    it[accelX]       = state.accelX
    it[accelY]       = state.accelY
    it[accelZ]       = state.accelZ
    it[gyroX]        = state.gyroX
    it[gyroY]        = state.gyroY
    it[gyroZ]        = state.gyroZ
    it[batteryLevel] = state.batteryLevel?.toShort()
    it[harshBrake]   = state.harshBrake
    it[harshAccel]   = state.harshAccel
    it[sharpTurn]    = state.sharpTurn
}
```

---

## Implementation Checklist

- [x] Create `V027__add_sensor_fields_to_location_history.sql`
- [x] Add new columns to `LocationHistoryTable` Exposed object
- [x] Update `LocationHistoryRepository.saveTrackingRecord()` to write new fields
- [x] Verify Flyway picks up migration in both local and Render `jar`
- [x] Run migration on local dev DB and confirm columns created
