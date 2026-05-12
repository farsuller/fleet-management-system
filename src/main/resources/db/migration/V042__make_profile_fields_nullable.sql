-- V042: Make profile fields nullable for progressive registration
-- This allows users to create accounts with only email/password initially.

-- Drivers table
ALTER TABLE drivers ALTER COLUMN first_name DROP NOT NULL;
ALTER TABLE drivers ALTER COLUMN last_name DROP NOT NULL;
ALTER TABLE drivers ALTER COLUMN phone DROP NOT NULL;
ALTER TABLE drivers ALTER COLUMN license_number DROP NOT NULL;
ALTER TABLE drivers ALTER COLUMN license_expiry DROP NOT NULL;

-- Customers table
ALTER TABLE customers ALTER COLUMN first_name DROP NOT NULL;
ALTER TABLE customers ALTER COLUMN last_name DROP NOT NULL;
ALTER TABLE customers ALTER COLUMN phone DROP NOT NULL;
ALTER TABLE customers ALTER COLUMN driver_license_number DROP NOT NULL;
ALTER TABLE customers ALTER COLUMN driver_license_expiry DROP NOT NULL;

-- Users table (core auth account)
ALTER TABLE users ALTER COLUMN first_name DROP NOT NULL;
ALTER TABLE users ALTER COLUMN last_name DROP NOT NULL;
