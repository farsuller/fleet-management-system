-- V015: Add DRIVER role to the roles table
INSERT INTO roles (id, name, description)
VALUES (gen_random_uuid(), 'DRIVER', 'Vehicle telemetry and shift management')
ON CONFLICT (name) DO NOTHING;
