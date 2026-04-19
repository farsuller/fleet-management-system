-- V039: Add CAR to allowed vehicle types
-- Modifies the check constraint to include CAR as a valid vehicle_type

-- Drop the old constraint
ALTER TABLE vehicles
    DROP CONSTRAINT vehicles_vehicle_type_check;

-- Add new constraint that includes CAR
ALTER TABLE vehicles
    ADD CONSTRAINT vehicles_vehicle_type_check
        CHECK (vehicle_type IN ('CAR', 'SEDAN', 'SUV', 'VAN', 'TRUCK', 'BUS', 'MOTORCYCLE', 'AMBULANCE', 'OTHER'));

-- Update vehicles that were set to CAR by V038 (if they exist and migration retries)
UPDATE vehicles SET vehicle_type = 'CAR'
WHERE vehicle_type = 'OTHER' AND make NOT IN ('Volvo', 'ISUZU');

COMMENT ON CONSTRAINT vehicles_vehicle_type_check ON vehicles IS
    'Vehicle type classification: CAR, SEDAN, SUV, VAN, TRUCK, BUS, MOTORCYCLE, AMBULANCE, or OTHER.';
