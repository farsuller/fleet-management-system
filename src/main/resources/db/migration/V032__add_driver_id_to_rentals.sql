-- V032: Add driver_id to rentals table
-- Links a rental to the driver assigned to deliver/operate during the rental period.
-- Separate from vehicle_driver_assignments (operational) — this is a billing/booking reference.

ALTER TABLE rentals
    ADD COLUMN IF NOT EXISTS driver_id UUID REFERENCES drivers(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_rentals_driver_id ON rentals(driver_id);

COMMENT ON COLUMN rentals.driver_id IS
    'Driver assigned to this rental booking. SET NULL on driver deletion to preserve rental history.';
