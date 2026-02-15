-- V013: Rename currency columns to whole units
-- This migration ensures existing databases are synchronized with the refactored code naming.
-- Uses DO blocks to be safe for both existing and new installations.

DO $$ 
BEGIN
    -- Vehicles
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='vehicles' AND column_name='daily_rate_cents') THEN
        ALTER TABLE vehicles RENAME COLUMN daily_rate_cents TO daily_rate;
    END IF;

    -- Rentals
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='rentals' AND column_name='daily_rate_cents') THEN
        ALTER TABLE rentals RENAME COLUMN daily_rate_cents TO daily_rate;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='rentals' AND column_name='total_amount_cents') THEN
        ALTER TABLE rentals RENAME COLUMN total_amount_cents TO total_amount;
    END IF;

    -- Rental Charges
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='rental_charges' AND column_name='amount_cents') THEN
        ALTER TABLE rental_charges RENAME COLUMN amount_cents TO amount;
    END IF;

    -- Rental Payments
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='rental_payments' AND column_name='amount_cents') THEN
        ALTER TABLE rental_payments RENAME COLUMN amount_cents TO amount;
    END IF;

    -- Maintenance Jobs
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='maintenance_jobs' AND column_name='labor_cost_cents') THEN
        ALTER TABLE maintenance_jobs RENAME COLUMN labor_cost_cents TO labor_cost;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='maintenance_jobs' AND column_name='parts_cost_cents') THEN
        ALTER TABLE maintenance_jobs RENAME COLUMN parts_cost_cents TO parts_cost;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='maintenance_jobs' AND column_name='total_cost_cents') THEN
        ALTER TABLE maintenance_jobs RENAME COLUMN total_cost_cents TO total_cost;
    END IF;

    -- Maintenance Parts
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='maintenance_parts' AND column_name='unit_cost_cents') THEN
        ALTER TABLE maintenance_parts RENAME COLUMN unit_cost_cents TO unit_cost;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='maintenance_parts' AND column_name='total_cost_cents') THEN
        ALTER TABLE maintenance_parts RENAME COLUMN total_cost_cents TO total_cost;
    END IF;

    -- Ledger Entry Lines
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='ledger_entry_lines' AND column_name='debit_amount_cents') THEN
        ALTER TABLE ledger_entry_lines RENAME COLUMN debit_amount_cents TO debit_amount;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='ledger_entry_lines' AND column_name='credit_amount_cents') THEN
        ALTER TABLE ledger_entry_lines RENAME COLUMN credit_amount_cents TO credit_amount;
    END IF;

    -- Invoices
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='invoices' AND column_name='subtotal_cents') THEN
        ALTER TABLE invoices RENAME COLUMN subtotal_cents TO subtotal;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='invoices' AND column_name='tax_cents') THEN
        ALTER TABLE invoices RENAME COLUMN tax_cents TO tax;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='invoices' AND column_name='total_cents') THEN
        ALTER TABLE invoices RENAME COLUMN total_cents TO total_amount;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='invoices' AND column_name='paid_cents') THEN
        ALTER TABLE invoices RENAME COLUMN paid_cents TO paid_amount;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='invoices' AND column_name='balance_cents') THEN
        ALTER TABLE invoices RENAME COLUMN balance_cents TO balance;
    END IF;

    -- Invoice Line Items
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='invoice_line_items' AND column_name='unit_price_cents') THEN
        ALTER TABLE invoice_line_items RENAME COLUMN unit_price_cents TO unit_price;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='invoice_line_items' AND column_name='total_cents') THEN
        ALTER TABLE invoice_line_items RENAME COLUMN total_cents TO total_amount;
    END IF;

    -- Payments
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='payments' AND column_name='amount_cents') THEN
        ALTER TABLE payments RENAME COLUMN amount_cents TO amount;
    END IF;

END $$;

-- Refresh function to use new column names
CREATE OR REPLACE FUNCTION validate_ledger_entry_balance()
RETURNS TRIGGER AS $$
DECLARE
    total_debits BIGINT;
    total_credits BIGINT;
BEGIN
    -- Calculate total debits and credits for this entry
    SELECT 
        COALESCE(SUM(debit_amount), 0),
        COALESCE(SUM(credit_amount), 0)
    INTO total_debits, total_credits
    FROM ledger_entry_lines
    WHERE entry_id = NEW.entry_id;
    
    -- Ensure debits equal credits
    IF total_debits != total_credits THEN
        RAISE EXCEPTION 'Ledger entry % is unbalanced: debits = %, credits = %', 
            NEW.entry_id, total_debits, total_credits;
    END IF;
    
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

