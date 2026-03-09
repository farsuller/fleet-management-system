-- V021: Align roles table with back-office-only user model
-- CUSTOMER and DRIVER are now separate domain entities with their own tables.
-- Add CUSTOMER_SUPPORT role (was in enum but never seeded).
-- Remove CUSTOMER and DRIVER roles (cascade removes any user_roles assignments).

INSERT INTO roles (id, name, description)
SELECT gen_random_uuid(), 'CUSTOMER_SUPPORT', 'Handles customer inquiries and basic support tasks'
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'CUSTOMER_SUPPORT');

DELETE FROM roles WHERE name = 'CUSTOMER';
DELETE FROM roles WHERE name = 'DRIVER';
