-- Migration: Create Payment Methods Table
-- Version: V012
-- Description: Creates a dedicated table for managing payment methods dynamically

CREATE TABLE payment_methods (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    code VARCHAR(20) NOT NULL UNIQUE, -- e.g., 'CASH', 'GCASH', 'BPI'
    display_name VARCHAR(100) NOT NULL,
    target_account_code VARCHAR(20) NOT NULL REFERENCES accounts(account_code),
    is_active BOOLEAN NOT NULL DEFAULT true,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Seed initial payment methods linked to the Chart of Accounts
INSERT INTO payment_methods (code, display_name, target_account_code, description) VALUES
    ('CASH', 'Cash on Hand', '1000', 'Physical cash payments received'),
    ('BANK_TRANSFER', 'Bank Transfer', '1010', 'Direct deposit to BPI account'),
    ('BPI_TRANSFER', 'BPI Transfer', '1010', 'BPI to BPI app transfer'),
    ('GCASH', 'GCash', '1020', 'GCash merchant wallet'),
    ('MAYA', 'Maya (PayMaya)', '1030', 'Maya merchant wallet'),
    ('CREDIT_CARD', 'Credit Card', '1040', 'Payments via Stripe/Terminal'),
    ('DEBIT_CARD', 'Debit Card', '1010', 'Debit card via POS');

-- Create an index for faster lookups by code
CREATE INDEX idx_payment_methods_code ON payment_methods(code);

-- Trigger for updated_at
CREATE TRIGGER update_payment_methods_updated_at BEFORE UPDATE ON payment_methods
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Add a comment
COMMENT ON TABLE payment_methods IS 'Master list of supported payment methods and their GL account mappings';
