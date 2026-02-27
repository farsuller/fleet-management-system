-- V015: Add PostGIS Spatial Extensions and Columns
-- Enable PostGIS extension (requires superuser on local, usually pre-enabled on Supabase)
CREATE EXTENSION IF NOT EXISTS postgis;

-- Add location column to vehicles
-- SRID 4326 = WGS 84 (GPS standard coordinates)
ALTER TABLE vehicles ADD COLUMN IF NOT EXISTS last_location GEOMETRY(Point, 4326);
ALTER TABLE vehicles ADD COLUMN IF NOT EXISTS route_progress DOUBLE PRECISION DEFAULT 0.0;
ALTER TABLE vehicles ADD COLUMN IF NOT EXISTS bearing DOUBLE PRECISION DEFAULT 0.0;

-- Create routes table if it doesn't exist
CREATE TABLE IF NOT EXISTS routes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    description TEXT,
    polyline GEOMETRY(LineString, 4326),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Geofences table for Depot and restricted zones
CREATE TABLE IF NOT EXISTS geofences (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    type VARCHAR(50) NOT NULL, -- e.g., 'DEPOT', 'RESTRICTED', 'CLIENT_SITE'
    boundary GEOMETRY(Polygon, 4326) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Spatial Indexes for performance (GIST)
CREATE INDEX IF NOT EXISTS idx_vehicles_location ON vehicles USING GIST(last_location);
CREATE INDEX IF NOT EXISTS idx_routes_polyline ON routes USING GIST(polyline);
CREATE INDEX IF NOT EXISTS idx_geofences_boundary ON geofences USING GIST(boundary);

-- Trigger for updated_at in geofences
CREATE TRIGGER update_geofences_updated_at BEFORE UPDATE ON geofences
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
