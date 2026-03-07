-- V019: Create Location History Table for Vehicle Tracking
-- Stores all vehicle location updates for audit trail and historical analysis

CREATE TABLE IF NOT EXISTS location_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    vehicle_id VARCHAR(36) NOT NULL,
    route_id VARCHAR(36),
    progress DOUBLE PRECISION NOT NULL,
    segment_id VARCHAR(50),
    speed DOUBLE PRECISION NOT NULL,
    heading DOUBLE PRECISION NOT NULL,
    status VARCHAR(20) NOT NULL,
    distance_from_route DOUBLE PRECISION NOT NULL,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Index for efficient querying by vehicle and timestamp
CREATE INDEX IF NOT EXISTS idx_location_history_vehicle_timestamp
    ON location_history(vehicle_id, timestamp DESC);

-- Index for querying by status (e.g., find all OFF_ROUTE events)
CREATE INDEX IF NOT EXISTS idx_location_history_status
    ON location_history(status, timestamp DESC);

-- Partial index for current tracking sessions
CREATE INDEX IF NOT EXISTS idx_location_history_in_transit
    ON location_history(vehicle_id, timestamp DESC)
    WHERE status IN ('IN_TRANSIT', 'OFF_ROUTE');

COMMENT ON TABLE location_history IS 'Real-time tracking history for all vehicles - records GPS updates, snapping results, and state changes';
COMMENT ON COLUMN location_history.progress IS '0.0-1.0 indicating position along the route';
COMMENT ON COLUMN location_history.distance_from_route IS 'Distance in meters from nearest route point';
COMMENT ON COLUMN location_history.status IS 'Vehicle status: IN_TRANSIT, IDLE, or OFF_ROUTE';

