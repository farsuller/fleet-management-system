-- Migration: Update Currency from USD to PHP
-- Version: V007
-- Description: Updates all currency_code defaults from USD to Philippine Pesos (PHP)
-- Date: 2026-02-03

-- ============================================================================
-- PART 1: Update Column Defaults
-- ============================================================================
-- This ensures all NEW records will default to PHP

-- Migration Note: This migration is largely representative as V002-V005 
-- have been refactored to use PHP by default.

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
