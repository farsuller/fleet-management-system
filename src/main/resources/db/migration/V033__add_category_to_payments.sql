-- V033: Add category column to payments table
-- Mirrors InvoiceCategory enum in the Payment domain entity.
-- Allows grouping/filtering payments by business category (RENTAL, MAINTENANCE, DIRECT, UNKNOWN).

ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS category TEXT NOT NULL DEFAULT 'RENTAL'
        CHECK (category IN ('RENTAL', 'MAINTENANCE', 'DIRECT', 'UNKNOWN'));

CREATE INDEX IF NOT EXISTS idx_payments_category ON payments(category);

COMMENT ON COLUMN payments.category IS
    'Business category of the payment, matching the InvoiceCategory enum: '
    'RENTAL, MAINTENANCE, DIRECT, UNKNOWN.';
