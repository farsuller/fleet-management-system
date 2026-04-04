-- V029__add_vehicle_service_mileage.sql
-- Add columns for tracking vehicle maintenance/service intervals

ALTER TABLE vehicles ADD COLUMN last_service_mileage INTEGER;
ALTER TABLE vehicles ADD COLUMN next_service_mileage INTEGER;

COMMENT ON COLUMN vehicles.last_service_mileage IS 'The odometer reading (km) when the vehicle was last serviced (e.g., oil change).';
COMMENT ON COLUMN vehicles.next_service_mileage IS 'The odometer reading (km) when the next service is due.';
