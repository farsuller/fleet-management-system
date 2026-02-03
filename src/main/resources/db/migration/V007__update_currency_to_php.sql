-- Migration: Update Currency from USD to PHP
-- Version: V007
-- Description: Updates all currency_code defaults from USD to Philippine Pesos (PHP)
-- Date: 2026-02-03

-- ============================================================================
-- PART 1: Update Column Defaults
-- ============================================================================
-- This ensures all NEW records will default to PHP

-- Vehicles Module
ALTER TABLE vehicles 
    ALTER COLUMN currency_code SET DEFAULT 'PHP';

-- Rentals Module
ALTER TABLE rentals 
    ALTER COLUMN currency_code SET DEFAULT 'PHP';

ALTER TABLE rental_charges 
    ALTER COLUMN currency_code SET DEFAULT 'PHP';

ALTER TABLE rental_payments 
    ALTER COLUMN currency_code SET DEFAULT 'PHP';

-- Maintenance Module
ALTER TABLE maintenance_jobs 
    ALTER COLUMN currency_code SET DEFAULT 'PHP';

ALTER TABLE maintenance_parts 
    ALTER COLUMN currency_code SET DEFAULT 'PHP';

-- Accounting Module
ALTER TABLE ledger_entry_lines 
    ALTER COLUMN currency_code SET DEFAULT 'PHP';

ALTER TABLE invoices 
    ALTER COLUMN currency_code SET DEFAULT 'PHP';

ALTER TABLE invoice_line_items 
    ALTER COLUMN currency_code SET DEFAULT 'PHP';

ALTER TABLE payments 
    ALTER COLUMN currency_code SET DEFAULT 'PHP';

-- ============================================================================
-- PART 2: Update Existing Records (Optional - Uncomment if needed)
-- ============================================================================
-- WARNING: Only uncomment these if you want to convert existing USD records to PHP
-- This will update ALL existing records in the database

-- Vehicles Module
-- UPDATE vehicles 
--     SET currency_code = 'PHP' 
--     WHERE currency_code = 'USD';

-- Rentals Module
-- UPDATE rentals 
--     SET currency_code = 'PHP' 
--     WHERE currency_code = 'USD';

-- UPDATE rental_charges 
--     SET currency_code = 'PHP' 
--     WHERE currency_code = 'USD';

-- UPDATE rental_payments 
--     SET currency_code = 'PHP' 
--     WHERE currency_code = 'USD';

-- Maintenance Module
-- UPDATE maintenance_jobs 
--     SET currency_code = 'PHP' 
--     WHERE currency_code = 'USD';

-- UPDATE maintenance_parts 
--     SET currency_code = 'PHP' 
--     WHERE currency_code = 'USD';

-- Accounting Module
-- UPDATE ledger_entry_lines 
--     SET currency_code = 'PHP' 
--     WHERE currency_code = 'USD';

-- UPDATE invoices 
--     SET currency_code = 'PHP' 
--     WHERE currency_code = 'USD';

-- UPDATE invoice_line_items 
--     SET currency_code = 'PHP' 
--     WHERE currency_code = 'USD';

-- UPDATE payments 
--     SET currency_code = 'PHP' 
--     WHERE currency_code = 'USD';

-- ============================================================================
-- PART 3: Verification Queries
-- ============================================================================
-- Run these queries after migration to verify the changes

-- Check default values (PostgreSQL specific)
-- SELECT 
--     table_name, 
--     column_name, 
--     column_default
-- FROM information_schema.columns
-- WHERE column_name = 'currency_code'
--     AND table_schema = 'public'
-- ORDER BY table_name;

-- Check existing data distribution
-- SELECT 'vehicles' as table_name, currency_code, COUNT(*) as count FROM vehicles GROUP BY currency_code
-- UNION ALL
-- SELECT 'rentals', currency_code, COUNT(*) FROM rentals GROUP BY currency_code
-- UNION ALL
-- SELECT 'rental_charges', currency_code, COUNT(*) FROM rental_charges GROUP BY currency_code
-- UNION ALL
-- SELECT 'rental_payments', currency_code, COUNT(*) FROM rental_payments GROUP BY currency_code
-- UNION ALL
-- SELECT 'maintenance_jobs', currency_code, COUNT(*) FROM maintenance_jobs GROUP BY currency_code
-- UNION ALL
-- SELECT 'maintenance_parts', currency_code, COUNT(*) FROM maintenance_parts GROUP BY currency_code
-- UNION ALL
-- SELECT 'ledger_entry_lines', currency_code, COUNT(*) FROM ledger_entry_lines GROUP BY currency_code
-- UNION ALL
-- SELECT 'invoices', currency_code, COUNT(*) FROM invoices GROUP BY currency_code
-- UNION ALL
-- SELECT 'invoice_line_items', currency_code, COUNT(*) FROM invoice_line_items GROUP BY currency_code
-- UNION ALL
-- SELECT 'payments', currency_code, COUNT(*) FROM payments GROUP BY currency_code
-- ORDER BY table_name, currency_code;

-- ============================================================================
-- Migration Notes
-- ============================================================================
-- 
-- 1. This migration updates the DEFAULT value for currency_code columns
--    - All NEW records will automatically use 'PHP'
--    - Existing records remain unchanged unless you uncomment PART 2
--
-- 2. No data conversion is performed on amounts
--    - Centavos values remain the same
--    - Only the currency code label changes
--
-- 3. Tables affected:
--    - vehicles (1 column)
--    - rentals, rental_charges, rental_payments (3 columns)
--    - maintenance_jobs, maintenance_parts (2 columns)
--    - ledger_entry_lines, invoices, invoice_line_items, payments (4 columns)
--    Total: 10 columns across 10 tables
--
-- 4. Rollback:
--    To rollback this migration, change 'PHP' back to 'USD' in the ALTER statements
--
-- 5. Testing:
--    After migration, insert a test record to verify the default is applied:
--    INSERT INTO vehicles (id, plate_number, make, model, year, status) 
--    VALUES (gen_random_uuid(), 'TEST-123', 'Toyota', 'Vios', 2024, 'ACTIVE');
--    
--    SELECT currency_code FROM vehicles WHERE plate_number = 'TEST-123';
--    -- Should return 'PHP'
--
-- ============================================================================
