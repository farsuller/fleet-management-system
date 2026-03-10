-- V025: Add invoice_id to rentals for direct invoice-rental linkage
-- Supports Phase B: Auto-invoice generation on rental completion

ALTER TABLE rentals
    ADD COLUMN IF NOT EXISTS invoice_id UUID REFERENCES invoices(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_rentals_invoice_id ON rentals(invoice_id);

COMMENT ON COLUMN rentals.invoice_id IS
    'Invoice automatically generated when the rental is completed. '
    'SET NULL on invoice deletion to preserve rental history.';
