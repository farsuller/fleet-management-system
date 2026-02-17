-- V005: Create Accounting Schema
-- This migration creates tables for double-entry bookkeeping and financial tracking

-- Chart of accounts: Define all accounts in the system
CREATE TABLE accounts (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    account_code VARCHAR(20) NOT NULL UNIQUE,
    account_name VARCHAR(255) NOT NULL,
    account_type VARCHAR(20) NOT NULL CHECK (account_type IN ('ASSET', 'LIABILITY', 'EQUITY', 'REVENUE', 'EXPENSE')),
    parent_account_id UUID REFERENCES accounts(id),
    is_active BOOLEAN NOT NULL DEFAULT true,
    description TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Ledger entries: Double-entry bookkeeping journal entries
CREATE TABLE ledger_entries (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    entry_number VARCHAR(50) NOT NULL UNIQUE,
    external_reference VARCHAR(255) NOT NULL UNIQUE, -- For idempotency (e.g., "rental-123-activation")
    entry_date TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    description TEXT NOT NULL,
    created_by_user_id UUID REFERENCES users(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Ledger entry lines: Individual debit/credit lines
CREATE TABLE ledger_entry_lines (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    entry_id UUID NOT NULL REFERENCES ledger_entries(id) ON DELETE CASCADE,
    account_id UUID NOT NULL REFERENCES accounts(id),
    debit_amount INTEGER NOT NULL DEFAULT 0 CHECK (debit_amount >= 0),
    credit_amount INTEGER NOT NULL DEFAULT 0 CHECK (credit_amount >= 0),
    currency_code VARCHAR(3) NOT NULL DEFAULT 'PHP',
    description TEXT,
    
    -- Constraint: Each line must be either debit or credit, not both
    CONSTRAINT debit_or_credit_not_both CHECK (
        (debit_amount > 0 AND credit_amount = 0) OR
        (debit_amount = 0 AND credit_amount > 0)
    )
);

-- Invoices table: Track customer invoices
CREATE TABLE invoices (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    invoice_number VARCHAR(50) NOT NULL UNIQUE,
    customer_id UUID NOT NULL REFERENCES customers(id),
    rental_id UUID REFERENCES rentals(id),
    status VARCHAR(20) NOT NULL CHECK (status IN ('DRAFT', 'ISSUED', 'PAID', 'OVERDUE', 'CANCELLED')),
    
    -- Amounts
    subtotal INTEGER NOT NULL CHECK (subtotal >= 0),
    tax INTEGER NOT NULL DEFAULT 0 CHECK (tax >= 0),
    total_amount INTEGER GENERATED ALWAYS AS (subtotal + tax) STORED,
    paid_amount INTEGER NOT NULL DEFAULT 0 CHECK (paid_amount >= 0),
    balance INTEGER GENERATED ALWAYS AS (subtotal + tax - paid_amount) STORED,
    currency_code VARCHAR(3) NOT NULL DEFAULT 'PHP',
    
    -- Dates
    issue_date DATE NOT NULL,
    due_date DATE NOT NULL,
    paid_date DATE,
    
    -- Metadata
    notes TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT invoice_dates_valid CHECK (due_date >= issue_date)
);

-- Invoice line items
CREATE TABLE invoice_line_items (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    invoice_id UUID NOT NULL REFERENCES invoices(id) ON DELETE CASCADE,
    description TEXT NOT NULL,
    quantity DECIMAL(10, 2) NOT NULL CHECK (quantity > 0),
    unit_price INTEGER NOT NULL CHECK (unit_price >= 0),
    total_amount INTEGER GENERATED ALWAYS AS (CAST(quantity * unit_price AS INTEGER)) STORED,
    currency_code VARCHAR(3) NOT NULL DEFAULT 'PHP'
);

-- Payments table: Track all payments
CREATE TABLE payments (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    payment_number VARCHAR(50) NOT NULL UNIQUE,
    customer_id UUID NOT NULL REFERENCES customers(id),
    invoice_id UUID REFERENCES invoices(id),
    payment_method VARCHAR(50) NOT NULL CHECK (payment_method IN ('CREDIT_CARD', 'DEBIT_CARD', 'CASH', 'BANK_TRANSFER', 'CHECK')),
    amount INTEGER NOT NULL CHECK (amount > 0),
    currency_code VARCHAR(3) NOT NULL DEFAULT 'PHP',
    transaction_reference VARCHAR(255),
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED', 'REFUNDED')),
    payment_date TIMESTAMP WITH TIME ZONE,
    notes TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Insert default chart of accounts
INSERT INTO accounts (account_code, account_name, account_type, description) VALUES
    -- Assets
    ('1000', 'Cash', 'ASSET', 'Cash on hand'),
    ('1010', 'Bank Account (BPI)', 'ASSET', 'Main operating bank account'),
    ('1020', 'GCash Wallet', 'ASSET', 'GCash merchant wallet'),
    ('1030', 'PayMaya Wallet', 'ASSET', 'PayMaya merchant wallet'),
    ('1040', 'Credit Card Clearing', 'ASSET', 'Payments via Stripe/Terminal awaiting settlement'),
    ('1100', 'Accounts Receivable', 'ASSET', 'Money owed by customers'),
    ('1500', 'Vehicle Fleet', 'ASSET', 'Fleet vehicles'),
    ('1600', 'Accumulated Depreciation - Vehicles', 'ASSET', 'Depreciation of fleet vehicles'),
    
    -- Liabilities
    ('2000', 'Accounts Payable', 'LIABILITY', 'Money owed to suppliers'),
    ('2100', 'Customer Deposits', 'LIABILITY', 'Deposits received from customers'),
    
    -- Equity
    ('3000', 'Owner Equity', 'EQUITY', 'Owner investment'),
    ('3100', 'Retained Earnings', 'EQUITY', 'Accumulated profits'),
    
    -- Revenue
    ('4000', 'Rental Revenue', 'REVENUE', 'Revenue from vehicle rentals'),
    ('4100', 'Late Fees', 'REVENUE', 'Late return fees'),
    ('4200', 'Damage Fees', 'REVENUE', 'Vehicle damage fees'),
    
    -- Expenses
    ('5000', 'Maintenance Costs', 'EXPENSE', 'Vehicle maintenance and repairs'),
    ('5100', 'Depreciation Expense', 'EXPENSE', 'Vehicle depreciation'),
    ('5200', 'Fuel Costs', 'EXPENSE', 'Fuel expenses'),
    ('5300', 'Insurance', 'EXPENSE', 'Vehicle insurance'),
    ('5400', 'Salaries', 'EXPENSE', 'Employee salaries');

-- Indexes for performance
CREATE INDEX idx_accounts_account_code ON accounts(account_code);
CREATE INDEX idx_accounts_account_type ON accounts(account_type);
CREATE INDEX idx_ledger_entries_external_reference ON ledger_entries(external_reference);
CREATE INDEX idx_ledger_entries_entry_date ON ledger_entries(entry_date);
CREATE INDEX idx_ledger_entry_lines_entry_id ON ledger_entry_lines(entry_id);
CREATE INDEX idx_ledger_entry_lines_account_id ON ledger_entry_lines(account_id);
CREATE INDEX idx_invoices_customer_id ON invoices(customer_id);
CREATE INDEX idx_invoices_status ON invoices(status);
CREATE INDEX idx_invoices_due_date ON invoices(due_date);
CREATE INDEX idx_invoice_line_items_invoice_id ON invoice_line_items(invoice_id);
CREATE INDEX idx_payments_customer_id ON payments(customer_id);
CREATE INDEX idx_payments_invoice_id ON payments(invoice_id);

-- Triggers for updated_at
CREATE TRIGGER update_accounts_updated_at BEFORE UPDATE ON accounts
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_invoices_updated_at BEFORE UPDATE ON invoices
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_payments_updated_at BEFORE UPDATE ON payments
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Function to validate double-entry balance
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

-- Note: This trigger is deferred to allow all lines to be inserted before validation
CREATE CONSTRAINT TRIGGER validate_ledger_balance
    AFTER INSERT OR UPDATE ON ledger_entry_lines
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW
    EXECUTE FUNCTION validate_ledger_entry_balance();
