-- V024: Add driver payment fields to payments table and create driver remittance tables
-- Supports Phase C: Customer → Driver payment collection and remittance tracking

-- 1. Extend payments table with driver_id and collection_type
ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS driver_id      UUID REFERENCES drivers(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS collection_type TEXT NOT NULL DEFAULT 'DIRECT'
        CHECK (collection_type IN ('DIRECT', 'DRIVER_COLLECTED'));

CREATE INDEX IF NOT EXISTS idx_payments_driver_id      ON payments(driver_id);
CREATE INDEX IF NOT EXISTS idx_payments_collection_type ON payments(collection_type);

-- 2. Driver remittances: batch hand-over of driver-collected payments to back-office
CREATE TABLE IF NOT EXISTS driver_remittances (
    id                UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    remittance_number TEXT        NOT NULL UNIQUE,
    driver_id         UUID        NOT NULL REFERENCES drivers(id) ON DELETE RESTRICT,
    remittance_date   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    total_amount      INT         NOT NULL CHECK (total_amount > 0),
    status            TEXT        NOT NULL DEFAULT 'PENDING'
                                  CHECK (status IN ('PENDING', 'SUBMITTED', 'VERIFIED', 'DISCREPANCY')),
    notes             TEXT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_driver_remittances_driver_id ON driver_remittances(driver_id);
CREATE INDEX IF NOT EXISTS idx_driver_remittances_status    ON driver_remittances(status);

-- 3. Junction table: each remittance clears one or many driver-collected payments
CREATE TABLE IF NOT EXISTS driver_remittance_payments (
    remittance_id UUID NOT NULL REFERENCES driver_remittances(id) ON DELETE CASCADE,
    payment_id    UUID NOT NULL REFERENCES payments(id)           ON DELETE RESTRICT,
    PRIMARY KEY (remittance_id, payment_id)
);

CREATE INDEX IF NOT EXISTS idx_drp_payment_id ON driver_remittance_payments(payment_id);

COMMENT ON COLUMN payments.driver_id IS
    'Driver who collected this payment from the customer in the field. NULL for direct company payments.';
COMMENT ON COLUMN payments.collection_type IS
    'DIRECT = customer paid company; DRIVER_COLLECTED = driver collected on behalf of company, awaiting remittance.';
COMMENT ON TABLE driver_remittances IS
    'Represents the physical hand-over event when a driver submits collected customer payments to back-office. '
    'Each remittance triggers GL posting: DR Asset / CR Accounts Receivable for each included payment.';
