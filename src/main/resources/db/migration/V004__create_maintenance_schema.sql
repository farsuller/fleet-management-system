-- V004: Create Maintenance Schema
-- This migration creates tables for vehicle maintenance tracking

-- Maintenance jobs table: Track maintenance work on vehicles
CREATE TABLE maintenance_jobs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_number VARCHAR(50) NOT NULL UNIQUE,
    vehicle_id UUID NOT NULL REFERENCES vehicles(id),
    status VARCHAR(20) NOT NULL CHECK (status IN ('SCHEDULED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')),
    
    -- Job details
    job_type VARCHAR(50) NOT NULL CHECK (job_type IN ('ROUTINE', 'REPAIR', 'INSPECTION', 'RECALL', 'EMERGENCY')),
    description TEXT NOT NULL,
    priority VARCHAR(20) NOT NULL DEFAULT 'NORMAL' CHECK (priority IN ('LOW', 'NORMAL', 'HIGH', 'URGENT')),
    
    -- Scheduling
    scheduled_date TIMESTAMPTZ NOT NULL,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    
    -- Odometer at time of service
    odometer_km INTEGER CHECK (odometer_km >= 0),
    
    -- Cost tracking
    labor_cost_cents INTEGER DEFAULT 0 CHECK (labor_cost_cents >= 0),
    parts_cost_cents INTEGER DEFAULT 0 CHECK (parts_cost_cents >= 0),
    total_cost_cents INTEGER GENERATED ALWAYS AS (labor_cost_cents + parts_cost_cents) STORED,
    currency_code VARCHAR(3) NOT NULL DEFAULT 'USD',
    
    -- Personnel
    assigned_to_user_id UUID REFERENCES users(id),
    completed_by_user_id UUID REFERENCES users(id),
    
    -- Metadata
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    
    -- Constraints
    CONSTRAINT maintenance_dates_valid CHECK (
        (started_at IS NULL OR started_at >= scheduled_date) AND
        (completed_at IS NULL OR (started_at IS NOT NULL AND completed_at >= started_at))
    )
);

-- Parts used in maintenance table: Track parts inventory and usage
CREATE TABLE maintenance_parts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id UUID NOT NULL REFERENCES maintenance_jobs(id) ON DELETE CASCADE,
    part_number VARCHAR(100) NOT NULL,
    part_name VARCHAR(255) NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    unit_cost_cents INTEGER NOT NULL CHECK (unit_cost_cents >= 0),
    total_cost_cents INTEGER GENERATED ALWAYS AS (quantity * unit_cost_cents) STORED,
    currency_code VARCHAR(3) NOT NULL DEFAULT 'USD',
    supplier VARCHAR(255),
    notes TEXT,
    added_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Maintenance schedules table: Define recurring maintenance requirements
CREATE TABLE maintenance_schedules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    vehicle_id UUID NOT NULL REFERENCES vehicles(id) ON DELETE CASCADE,
    schedule_type VARCHAR(50) NOT NULL CHECK (schedule_type IN ('OIL_CHANGE', 'TIRE_ROTATION', 'BRAKE_INSPECTION', 'GENERAL_INSPECTION', 'CUSTOM')),
    description TEXT NOT NULL,
    
    -- Recurrence rules
    interval_type VARCHAR(20) NOT NULL CHECK (interval_type IN ('MILEAGE', 'TIME', 'BOTH')),
    mileage_interval_km INTEGER CHECK (mileage_interval_km > 0),
    time_interval_days INTEGER CHECK (time_interval_days > 0),
    
    -- Last service tracking
    last_service_date TIMESTAMPTZ,
    last_service_odometer_km INTEGER,
    
    -- Next service due
    next_service_date TIMESTAMPTZ,
    next_service_odometer_km INTEGER,
    
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Constraints
    CONSTRAINT schedule_interval_valid CHECK (
        (interval_type = 'MILEAGE' AND mileage_interval_km IS NOT NULL) OR
        (interval_type = 'TIME' AND time_interval_days IS NOT NULL) OR
        (interval_type = 'BOTH' AND mileage_interval_km IS NOT NULL AND time_interval_days IS NOT NULL)
    )
);

-- Indexes for performance
CREATE INDEX idx_maintenance_jobs_vehicle_id ON maintenance_jobs(vehicle_id);
CREATE INDEX idx_maintenance_jobs_status ON maintenance_jobs(status);
CREATE INDEX idx_maintenance_jobs_scheduled_date ON maintenance_jobs(scheduled_date);
CREATE INDEX idx_maintenance_jobs_job_number ON maintenance_jobs(job_number);
CREATE INDEX idx_maintenance_jobs_assigned_to ON maintenance_jobs(assigned_to_user_id);
CREATE INDEX idx_maintenance_parts_job_id ON maintenance_parts(job_id);
CREATE INDEX idx_maintenance_schedules_vehicle_id ON maintenance_schedules(vehicle_id);
CREATE INDEX idx_maintenance_schedules_next_service ON maintenance_schedules(next_service_date, next_service_odometer_km) WHERE is_active = true;

-- Triggers for updated_at
CREATE TRIGGER update_maintenance_jobs_updated_at BEFORE UPDATE ON maintenance_jobs
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_maintenance_schedules_updated_at BEFORE UPDATE ON maintenance_schedules
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Trigger for version increment
CREATE TRIGGER increment_maintenance_jobs_version BEFORE UPDATE ON maintenance_jobs
    FOR EACH ROW EXECUTE FUNCTION increment_version();

-- Function to update maintenance schedule after job completion
CREATE OR REPLACE FUNCTION update_maintenance_schedule_after_job()
RETURNS TRIGGER AS $$
BEGIN
    -- Only process when job is completed
    IF NEW.status = 'COMPLETED' AND OLD.status != 'COMPLETED' THEN
        -- Update any matching schedules
        UPDATE maintenance_schedules
        SET last_service_date = NEW.completed_at,
            last_service_odometer_km = NEW.odometer_km,
            next_service_date = CASE
                WHEN time_interval_days IS NOT NULL THEN NEW.completed_at + (time_interval_days || ' days')::INTERVAL
                ELSE next_service_date
            END,
            next_service_odometer_km = CASE
                WHEN mileage_interval_km IS NOT NULL AND NEW.odometer_km IS NOT NULL THEN NEW.odometer_km + mileage_interval_km
                ELSE next_service_odometer_km
            END
        WHERE vehicle_id = NEW.vehicle_id
          AND is_active = true;
    END IF;
    
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER update_schedule_after_job_completion
    AFTER UPDATE ON maintenance_jobs
    FOR EACH ROW
    EXECUTE FUNCTION update_maintenance_schedule_after_job();
