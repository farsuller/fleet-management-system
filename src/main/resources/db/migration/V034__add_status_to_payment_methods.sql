-- V034: Add status column to payment_methods table
-- Replaces the original is_active BOOLEAN with a status VARCHAR for richer state management.
-- PaymentMethodStatus enum: ACTIVE, INACTIVE, DEPRECATED

ALTER TABLE payment_methods
    ADD COLUMN IF NOT EXISTS status TEXT NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'INACTIVE', 'DEPRECATED'));

-- Backfill from is_active if that column still exists
UPDATE payment_methods SET status = CASE WHEN is_active = false THEN 'INACTIVE' ELSE 'ACTIVE' END
    WHERE status = 'ACTIVE';

CREATE INDEX IF NOT EXISTS idx_payment_methods_status ON payment_methods(status);

COMMENT ON COLUMN payment_methods.status IS
    'Lifecycle status of the payment method: ACTIVE (default), INACTIVE, or DEPRECATED.';
