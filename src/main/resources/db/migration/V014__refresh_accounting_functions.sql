-- V014: Refresh accounting triggers and functions
-- This migration explicitly updates functions that may have been left using legacy column names.

-- Refresh function to use new column names
CREATE OR REPLACE FUNCTION validate_ledger_entry_balance()
RETURNS TRIGGER AS $$
DECLARE
    total_debits BIGINT;
    total_credits BIGINT;
BEGIN
    -- Calculate total debits and credits for this entry using new column names (debit_amount, credit_amount)
    -- This was previously using debit_amount_cents and credit_amount_cents.
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
