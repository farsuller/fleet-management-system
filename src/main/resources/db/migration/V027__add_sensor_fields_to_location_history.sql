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
