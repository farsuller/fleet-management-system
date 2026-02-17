-- Migration: Seed Chart of Accounts (Idempotent)
-- Version: V011
-- Description: Ensures the required chart of accounts is present for GL entries. 
-- Using ON CONFLICT to allow re-running safely if some accounts already exist.

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
    ('5400', 'Salaries', 'EXPENSE', 'Employee salaries')
ON CONFLICT (account_code) DO NOTHING;
