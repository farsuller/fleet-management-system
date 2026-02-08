-- Migration: Update Payment Methods in payments table
-- Version: V010
-- Description: Updates the check constraint for payment_method to include local Philippine payment options

ALTER TABLE payments DROP CONSTRAINT IF EXISTS payments_payment_method_check;

ALTER TABLE payments ADD CONSTRAINT payments_payment_method_check 
    CHECK (payment_method IN (
        'CREDIT_CARD', 
        'DEBIT_CARD', 
        'CASH', 
        'BANK_TRANSFER', 
        'CHECK', 
        'GCASH', 
        'PAYMAYA', 
        'BPI_TRANSFER',
        'MAYA' -- Adding MAYA as an alias for PAYMAYA just in case
    ));

-- Update documentation notes if needed
COMMENT ON COLUMN payments.payment_method IS 'The method of payment. Updated in V010 to include GCASH, PAYMAYA, BPI_TRANSFER.';
