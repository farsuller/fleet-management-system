-- V002: Create Vehicles Schema
-- This migration creates tables for vehicle management and odometer tracking

-- Vehicles table: Core vehicle information
CREATE TABLE vehicles (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    plate_number VARCHAR(20) NOT NULL UNIQUE,
    make VARCHAR(100) NOT NULL,
    model VARCHAR(100) NOT NULL,
    year INTEGER NOT NULL CHECK (year >= 1900 AND year <= 2100),
    status VARCHAR(20) NOT NULL CHECK (status IN ('AVAILABLE', 'RENTED', 'MAINTENANCE', 'RETIRED')),
    passenger_capacity INTEGER CHECK (passenger_capacity > 0),
    current_odometer_km INTEGER NOT NULL DEFAULT 0 CHECK (current_odometer_km >= 0),
    vin VARCHAR(17) UNIQUE,
    color VARCHAR(50),
    fuel_type VARCHAR(20) CHECK (fuel_type IN ('GASOLINE', 'DIESEL', 'ELECTRIC', 'HYBRID')),
    transmission VARCHAR(20) CHECK (transmission IN ('MANUAL', 'AUTOMATIC')),
    daily_rate_cents INTEGER CHECK (daily_rate_cents >= 0),
    currency_code VARCHAR(3) DEFAULT 'PHP',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);

-- Odometer readings table: Track odometer history
CREATE TABLE odometer_readings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    vehicle_id UUID NOT NULL REFERENCES vehicles(id) ON DELETE CASCADE,
    reading_km INTEGER NOT NULL CHECK (reading_km >= 0),
    recorded_by_user_id UUID REFERENCES users(id),
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    notes TEXT
);

-- Constraint: Ensure odometer readings are non-decreasing for each vehicle
-- This is enforced at application level, but we add an index to help with queries
CREATE INDEX idx_odometer_readings_vehicle_reading ON odometer_readings(vehicle_id, reading_km DESC);

-- Function to validate odometer reading is non-decreasing
CREATE OR REPLACE FUNCTION validate_odometer_reading()
RETURNS TRIGGER AS $$
DECLARE
    last_reading INTEGER;
BEGIN
    -- Get the last odometer reading for this vehicle
    SELECT reading_km INTO last_reading
    FROM odometer_readings
    WHERE vehicle_id = NEW.vehicle_id
    ORDER BY recorded_at DESC
    LIMIT 1;
    
    -- If there's a previous reading, ensure new reading is not less
    IF last_reading IS NOT NULL AND NEW.reading_km < last_reading THEN
        RAISE EXCEPTION 'Odometer reading cannot decrease. Last reading: %, New reading: %', last_reading, NEW.reading_km;
    END IF;
    
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Apply trigger to odometer_readings table
CREATE TRIGGER check_odometer_reading_before_insert
    BEFORE INSERT ON odometer_readings
    FOR EACH ROW
    EXECUTE FUNCTION validate_odometer_reading();

-- Indexes for performance
CREATE INDEX idx_vehicles_status ON vehicles(status);
CREATE INDEX idx_vehicles_plate_number ON vehicles(plate_number);
CREATE INDEX idx_vehicles_vin ON vehicles(vin);
CREATE INDEX idx_odometer_readings_vehicle_id ON odometer_readings(vehicle_id);
CREATE INDEX idx_odometer_readings_recorded_at ON odometer_readings(recorded_at DESC);

-- Apply updated_at trigger to vehicles table
CREATE TRIGGER update_vehicles_updated_at BEFORE UPDATE ON vehicles
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Trigger to increment version on update (for optimistic locking)
CREATE OR REPLACE FUNCTION increment_version()
RETURNS TRIGGER AS $$
BEGIN
    NEW.version = OLD.version + 1;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER increment_vehicles_version BEFORE UPDATE ON vehicles
    FOR EACH ROW EXECUTE FUNCTION increment_version();
