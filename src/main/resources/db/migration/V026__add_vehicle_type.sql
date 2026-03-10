-- V026: Add vehicle_type column to vehicles table
-- Classifies vehicles by body/use type: SEDAN, SUV, VAN, TRUCK, BUS, MOTORCYCLE, AMBULANCE, OTHER

ALTER TABLE vehicles
    ADD COLUMN IF NOT EXISTS vehicle_type VARCHAR(20) NOT NULL DEFAULT 'OTHER'
        CHECK (vehicle_type IN ('SEDAN', 'SUV', 'VAN', 'TRUCK', 'BUS', 'MOTORCYCLE', 'AMBULANCE', 'OTHER'));

CREATE INDEX IF NOT EXISTS idx_vehicles_type ON vehicles(vehicle_type);

COMMENT ON COLUMN vehicles.vehicle_type IS
    'Body/use classification: SEDAN, SUV, VAN, TRUCK, BUS, MOTORCYCLE, AMBULANCE, or OTHER.';
