-- V028__create_driver_shifts.sql
-- Create driver_shifts table to track work sessions.

CREATE TABLE IF NOT EXISTS driver_shifts (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    driver_id   UUID NOT NULL REFERENCES drivers(id),
    vehicle_id  UUID NOT NULL,
    started_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ended_at    TIMESTAMPTZ,
    notes       TEXT,
    CONSTRAINT one_active_shift_per_driver
        EXCLUDE USING GIST (driver_id WITH =)
        WHERE (ended_at IS NULL)
);

CREATE INDEX IF NOT EXISTS idx_driver_shifts_driver ON driver_shifts(driver_id, started_at DESC);
CREATE INDEX IF NOT EXISTS idx_driver_shifts_vehicle ON driver_shifts(vehicle_id, started_at DESC);
