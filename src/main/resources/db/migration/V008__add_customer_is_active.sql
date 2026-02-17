-- V008: Add is_active to customers table
-- This column tracks whether a customer account is currently active.

ALTER TABLE customers ADD COLUMN is_active BOOLEAN NOT NULL DEFAULT TRUE;

-- Update comment for clarity
COMMENT ON COLUMN customers.is_active IS 'Indicates if the customer is active and allowed to rent vehicles';
