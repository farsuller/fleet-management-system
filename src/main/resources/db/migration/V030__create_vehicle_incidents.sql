-- Migration: V030__create_vehicle_incidents.sql
-- Description: Create table for vehicle incidents reported by drivers.

CREATE TABLE IF NOT EXISTS vehicle_incidents (
    id UUID PRIMARY KEY,
    vehicle_id UUID NOT NULL REFERENCES vehicles(id) ON DELETE CASCADE,
    maintenance_job_id UUID REFERENCES maintenance_jobs(id) ON DELETE SET NULL,
    
    title VARCHAR(200) NOT NULL,
    description TEXT NOT NULL,
    severity VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    
    reported_at TIMESTAMP WITH TIME ZONE NOT NULL,
    reported_by_user_id UUID,
    
    odometer_km INTEGER,
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Index for performance
CREATE INDEX idx_vehicle_incidents_vehicle_id ON vehicle_incidents(vehicle_id);
CREATE INDEX idx_vehicle_incidents_status ON vehicle_incidents(status);
CREATE INDEX idx_vehicle_incidents_reported_at ON vehicle_incidents(reported_at);
