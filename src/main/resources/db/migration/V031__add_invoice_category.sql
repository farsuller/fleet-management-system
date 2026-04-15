-- V031__add_invoice_category.sql
-- Add category column to invoices table to distinguish RENTAL / MAINTENANCE / DIRECT invoices

ALTER TABLE invoices
    ADD COLUMN IF NOT EXISTS category TEXT NOT NULL DEFAULT 'RENTAL'
        CHECK (category IN ('RENTAL', 'MAINTENANCE', 'DIRECT', 'UNKNOWN'));

CREATE INDEX IF NOT EXISTS idx_invoices_category ON invoices(category);

-- Backfill: any invoice with a rental_id gets RENTAL (already the default, but explicit for clarity)
UPDATE invoices SET category = 'RENTAL' WHERE rental_id IS NOT NULL AND category = 'RENTAL';

-- Invoices with no rental linkage default to DIRECT
UPDATE invoices SET category = 'DIRECT' WHERE rental_id IS NULL AND category = 'RENTAL';
