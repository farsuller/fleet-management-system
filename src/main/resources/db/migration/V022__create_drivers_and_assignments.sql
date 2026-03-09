-- V022: Drivers table and Vehicle-Driver assignment table
-- Drivers are a separate domain entity from users.
-- A driver optionally links to a users account (for mobile app auth).
-- vehicle_driver_assignments is the join table for the many-to-many
-- relationship between vehicles and drivers (only one ACTIVE assignment
-- per vehicle at a time enforced by partial unique index).

CREATE TABLE IF NOT EXISTS drivers (
    id                      UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id                 UUID UNIQUE REFERENCES users(id) ON DELETE SET NULL,
    first_name              VARCHAR(100) NOT NULL,
    last_name               VARCHAR(100) NOT NULL,
    email                   VARCHAR(255) NOT NULL UNIQUE,
    phone                   VARCHAR(20)  NOT NULL,
    license_number          VARCHAR(50)  NOT NULL UNIQUE,
    license_expiry          DATE         NOT NULL,
    license_class           VARCHAR(20),
    address                 TEXT,
    city                    VARCHAR(100),
    state                   VARCHAR(100),
    postal_code             VARCHAR(20),
    country                 VARCHAR(100),
    is_active               BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Assignment history: one row per assignment period
CREATE TABLE IF NOT EXISTS vehicle_driver_assignments (
    id          UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    vehicle_id  UUID NOT NULL REFERENCES vehicles(id) ON DELETE CASCADE,
    driver_id   UUID NOT NULL REFERENCES drivers(id)  ON DELETE CASCADE,
    assigned_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    released_at TIMESTAMP WITH TIME ZONE,           -- NULL means currently active
    notes       TEXT,
    CONSTRAINT chk_released_after_assigned CHECK (released_at IS NULL OR released_at > assigned_at)
);

-- Only one active (released_at IS NULL) assignment per vehicle at a time
CREATE UNIQUE INDEX IF NOT EXISTS idx_vehicle_active_assignment
    ON vehicle_driver_assignments (vehicle_id)
    WHERE released_at IS NULL;

-- Only one active assignment per driver at a time
CREATE UNIQUE INDEX IF NOT EXISTS idx_driver_active_assignment
    ON vehicle_driver_assignments (driver_id)
    WHERE released_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_drivers_email        ON drivers(email);
CREATE INDEX IF NOT EXISTS idx_drivers_is_active    ON drivers(is_active);
CREATE INDEX IF NOT EXISTS idx_vda_vehicle_id       ON vehicle_driver_assignments(vehicle_id);
CREATE INDEX IF NOT EXISTS idx_vda_driver_id        ON vehicle_driver_assignments(driver_id);

CREATE OR REPLACE TRIGGER update_drivers_updated_at BEFORE UPDATE ON drivers
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Seed DRIVER role (idempotent – V015 may have added it already; delete was done in V021)
INSERT INTO roles (id, name, description)
SELECT gen_random_uuid(), 'DRIVER', 'Mobile app driver with vehicle telemetry access'
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'DRIVER');

-- Seed CUSTOMER role for the customer self-registration endpoint (mobile app)
INSERT INTO roles (id, name, description)
SELECT gen_random_uuid(), 'CUSTOMER', 'Mobile app customer with self-service rental access'
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'CUSTOMER');
