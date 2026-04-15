-- V036: Seed January and February 2026 revenue ledger entries
-- Extends the seed dataset started in V035 (March–April 2026).
-- All amounts in whole PHP pesos. Double-entry: DR Cash/Bank/GCash, CR Revenue.
-- Idempotent via ON CONFLICT on external_reference.

DO $$
DECLARE
    cash_id        UUID;
    bank_id        UUID;
    gcash_id       UUID;
    rev_rental_id  UUID;
    rev_late_id    UUID;
    rev_damage_id  UUID;
    ar_id          UUID;
    entry_id       UUID;
BEGIN
    SELECT id INTO cash_id       FROM accounts WHERE account_code = '1000';
    SELECT id INTO bank_id       FROM accounts WHERE account_code = '1010';
    SELECT id INTO gcash_id      FROM accounts WHERE account_code = '1020';
    SELECT id INTO rev_rental_id FROM accounts WHERE account_code = '4000';
    SELECT id INTO rev_late_id   FROM accounts WHERE account_code = '4100';
    SELECT id INTO rev_damage_id FROM accounts WHERE account_code = '4200';
    SELECT id INTO ar_id         FROM accounts WHERE account_code = '1100';

    -- ── JANUARY 2026 ────────────────────────────────────────────────────────

    INSERT INTO ledger_entries (entry_number, external_reference, entry_date, description)
        VALUES ('JE-2026-J001', 'SEED-JAN-02-RENTAL', '2026-01-02 08:00:00+08', 'Daily rental collections - Jan 2')
        ON CONFLICT (external_reference) DO NOTHING RETURNING id INTO entry_id;
    IF entry_id IS NOT NULL THEN
        INSERT INTO ledger_entry_lines (entry_id, account_id, debit_amount, credit_amount) VALUES
            (entry_id, bank_id,       16000, 0), (entry_id, rev_rental_id, 0, 16000);
    END IF; entry_id := NULL;

    INSERT INTO ledger_entries (entry_number, external_reference, entry_date, description)
        VALUES ('JE-2026-J002', 'SEED-JAN-05-RENTAL', '2026-01-05 09:00:00+08', 'Daily rental collections - Jan 5')
        ON CONFLICT (external_reference) DO NOTHING RETURNING id INTO entry_id;
    IF entry_id IS NOT NULL THEN
        INSERT INTO ledger_entry_lines (entry_id, account_id, debit_amount, credit_amount) VALUES
            (entry_id, cash_id,       14500, 0), (entry_id, rev_rental_id, 0, 14500);
    END IF; entry_id := NULL;

    INSERT INTO ledger_entries (entry_number, external_reference, entry_date, description)
        VALUES ('JE-2026-J003', 'SEED-JAN-07-LATE', '2026-01-07 15:00:00+08', 'Late return fees - Jan 7')
        ON CONFLICT (external_reference) DO NOTHING RETURNING id INTO entry_id;
    IF entry_id IS NOT NULL THEN
        INSERT INTO ledger_entry_lines (entry_id, account_id, debit_amount, credit_amount) VALUES
            (entry_id, cash_id,    2000, 0), (entry_id, rev_late_id, 0, 2000);
    END IF; entry_id := NULL;

    INSERT INTO ledger_entries (entry_number, external_reference, entry_date, description)
        VALUES ('JE-2026-J004', 'SEED-JAN-09-RENTAL', '2026-01-09 08:30:00+08', 'Daily rental collections - Jan 9')
        ON CONFLICT (external_reference) DO NOTHING RETURNING id INTO entry_id;
    IF entry_id IS NOT NULL THEN
        INSERT INTO ledger_entry_lines (entry_id, account_id, debit_amount, credit_amount) VALUES
            (entry_id, gcash_id,      19500, 0), (entry_id, rev_rental_id, 0, 19500);
    END IF; entry_id := NULL;

    INSERT INTO ledger_entries (entry_number, external_reference, entry_date, description)
        VALUES ('JE-2026-J005', 'SEED-JAN-12-DAMAGE', '2026-01-12 11:00:00+08', 'Vehicle damage fees - Jan 12')
        ON CONFLICT (external_reference) DO NOTHING RETURNING id INTO entry_id;
    IF entry_id IS NOT NULL THEN
        INSERT INTO ledger_entry_lines (entry_id, account_id, debit_amount, credit_amount) VALUES
            (entry_id, ar_id,         7000, 0), (entry_id, rev_damage_id, 0, 7000);
    END IF; entry_id := NULL;

    INSERT INTO ledger_entries (entry_number, external_reference, entry_date, description)
        VALUES ('JE-2026-J006', 'SEED-JAN-14-RENTAL', '2026-01-14 09:00:00+08', 'Weekend rental collections - Jan 14')
        ON CONFLICT (external_reference) DO NOTHING RETURNING id INTO entry_id;
    IF entry_id IS NOT NULL THEN
        INSERT INTO ledger_entry_lines (entry_id, account_id, debit_amount, credit_amount) VALUES
            (entry_id, bank_id,       25000, 0), (entry_id, rev_rental_id, 0, 25000);
    END IF; entry_id := NULL;

    INSERT INTO ledger_entries (entry_number, external_reference, entry_date, description)
        VALUES ('JE-2026-J007', 'SEED-JAN-16-RENTAL', '2026-01-16 08:00:00+08', 'Daily rental collections - Jan 16')
        ON CONFLICT (external_reference) DO NOTHING RETURNING id INTO entry_id;
    IF entry_id IS NOT NULL THEN
        INSERT INTO ledger_entry_lines (entry_id, account_id, debit_amount, credit_amount) VALUES
            (entry_id, cash_id,       17000, 0), (entry_id, rev_rental_id, 0, 17000);
    END IF; entry_id := NULL;

    INSERT INTO ledger_entries (entry_number, external_reference, entry_date, description)
        VALUES ('JE-2026-J008', 'SEED-JAN-19-LATE', '2026-01-19 14:00:00+08', 'Late return fees - Jan 19')
        ON CONFLICT (external_reference) DO NOTHING RETURNING id INTO entry_id;
    IF entry_id IS NOT NULL THEN
        INSERT INTO ledger_entry_lines (entry_id, account_id, debit_amount, credit_amount) VALUES
            (entry_id, cash_id,    1500, 0), (entry_id, rev_late_id, 0, 1500);
    END IF; entry_id := NULL;

    INSERT INTO ledger_entries (entry_number, external_reference, entry_date, description)
        VALUES ('JE-2026-J009', 'SEED-JAN-21-RENTAL', '2026-01-21 09:30:00+08', 'Weekend rental collections - Jan 21')
        ON CONFLICT (external_reference) DO NOTHING RETURNING id INTO entry_id;
    IF entry_id IS NOT NULL THEN
        INSERT INTO ledger_entry_lines (entry_id, account_id, debit_amount, credit_amount) VALUES
            (entry_id, gcash_id,      28500, 0), (entry_id, rev_rental_id, 0, 28500);
    END IF; entry_id := NULL;

    INSERT INTO ledger_entries (entry_number, external_reference, entry_date, description)
        VALUES ('JE-2026-J010', 'SEED-JAN-23-RENTAL', '2026-01-23 08:00:00+08', 'Daily rental collections - Jan 23')
        ON CONFLICT (external_reference) DO NOTHING RETURNING id INTO entry_id;
    IF entry_id IS NOT NULL THEN
        INSERT INTO ledger_entry_lines (entry_id, account_id, debit_amount, credit_amount) VALUES
            (entry_id, bank_id,       21000, 0), (entry_id, rev_rental_id, 0, 21000);
    END IF; entry_id := NULL;

    INSERT INTO ledger_entries (entry_number, external_reference, entry_date, description)
        VALUES ('JE-2026-J011', 'SEED-JAN-25-DAMAGE', '2026-01-25 12:00:00+08', 'Vehicle damage fees - Jan 25')
        ON CONFLICT (external_reference) DO NOTHING RETURNING id INTO entry_id;
    IF entry_id IS NOT NULL THEN
        INSERT INTO ledger_entry_lines (entry_id, account_id, debit_amount, credit_amount) VALUES
            (entry_id, ar_id,         9500, 0), (entry_id, rev_damage_id, 0, 9500);
    END IF; entry_id := NULL;

    INSERT INTO ledger_entries (entry_number, external_reference, entry_date, description)
        VALUES ('JE-2026-J012', 'SEED-JAN-27-RENTAL', '2026-01-27 09:00:00+08', 'Daily rental collections - Jan 27')
        ON CONFLICT (external_reference) DO NOTHING RETURNING id INTO entry_id;
    IF entry_id IS NOT NULL THEN
        INSERT INTO ledger_entry_lines (entry_id, account_id, debit_amount, credit_amount) VALUES
            (entry_id, cash_id,       23000, 0), (entry_id, rev_rental_id, 0, 23000);
    END IF; entry_id := NULL;

    INSERT INTO ledger_entries (entry_number, external_reference, entry_date, description)
        VALUES ('JE-2026-J013', 'SEED-JAN-29-RENTAL', '2026-01-29 08:30:00+08', 'Daily rental collections - Jan 29')
        ON CONFLICT (external_reference) DO NOTHING RETURNING id INTO entry_id;
    IF entry_id IS NOT NULL THEN
        INSERT INTO ledger_entry_lines (entry_id, account_id, debit_amount, credit_amount) VALUES
            (entry_id, gcash_id,      31000, 0), (entry_id, rev_rental_id, 0, 31000);
    END IF; entry_id := NULL;

    INSERT INTO ledger_entries (entry_number, external_reference, entry_date, description)
        VALUES ('JE-2026-J014', 'SEED-JAN-31-RENTAL', '2026-01-31 09:00:00+08', 'Monthly close rental - Jan 31')
        ON CONFLICT (external_reference) DO NOTHING RETURNING id INTO entry_id;
    IF entry_id IS NOT NULL THEN
        INSERT INTO ledger_entry_lines (entry_id, account_id, debit_amount, credit_amount) VALUES
            (entry_id, bank_id,       18000, 0), (entry_id, rev_rental_id, 0, 18000);
    END IF; entry_id := NULL;

    -- ── FEBRUARY 2026 ───────────────────────────────────────────────────────

    INSERT INTO ledger_entries (entry_number, external_reference, entry_date, description)
        VALUES ('JE-2026-F001', 'SEED-FEB-02-RENTAL', '2026-02-02 08:00:00+08', 'Daily rental collections - Feb 2')
        ON CONFLICT (external_reference) DO NOTHING RETURNING id INTO entry_id;
    IF entry_id IS NOT NULL THEN
        INSERT INTO ledger_entry_lines (entry_id, account_id, debit_amount, credit_amount) VALUES
            (entry_id, cash_id,       20000, 0), (entry_id, rev_rental_id, 0, 20000);
    END IF; entry_id := NULL;

    INSERT INTO ledger_entries (entry_number, external_reference, entry_date, description)
        VALUES ('JE-2026-F002', 'SEED-FEB-04-LATE', '2026-02-04 14:30:00+08', 'Late return fees - Feb 4')
        ON CONFLICT (external_reference) DO NOTHING RETURNING id INTO entry_id;
    IF entry_id IS NOT NULL THEN
        INSERT INTO ledger_entry_lines (entry_id, account_id, debit_amount, credit_amount) VALUES
            (entry_id, cash_id,    2500, 0), (entry_id, rev_late_id, 0, 2500);
    END IF; entry_id := NULL;

    INSERT INTO ledger_entries (entry_number, external_reference, entry_date, description)
        VALUES ('JE-2026-F003', 'SEED-FEB-06-RENTAL', '2026-02-06 09:00:00+08', 'Daily rental collections - Feb 6')
        ON CONFLICT (external_reference) DO NOTHING RETURNING id INTO entry_id;
    IF entry_id IS NOT NULL THEN
        INSERT INTO ledger_entry_lines (entry_id, account_id, debit_amount, credit_amount) VALUES
            (entry_id, bank_id,       24500, 0), (entry_id, rev_rental_id, 0, 24500);
    END IF; entry_id := NULL;

    INSERT INTO ledger_entries (entry_number, external_reference, entry_date, description)
        VALUES ('JE-2026-F004', 'SEED-FEB-08-DAMAGE', '2026-02-08 11:00:00+08', 'Vehicle damage fees - Feb 8')
        ON CONFLICT (external_reference) DO NOTHING RETURNING id INTO entry_id;
    IF entry_id IS NOT NULL THEN
        INSERT INTO ledger_entry_lines (entry_id, account_id, debit_amount, credit_amount) VALUES
            (entry_id, ar_id,         11000, 0), (entry_id, rev_damage_id, 0, 11000);
    END IF; entry_id := NULL;

    INSERT INTO ledger_entries (entry_number, external_reference, entry_date, description)
        VALUES ('JE-2026-F005', 'SEED-FEB-10-RENTAL', '2026-02-10 09:00:00+08', 'Daily rental collections - Feb 10')
        ON CONFLICT (external_reference) DO NOTHING RETURNING id INTO entry_id;
    IF entry_id IS NOT NULL THEN
        INSERT INTO ledger_entry_lines (entry_id, account_id, debit_amount, credit_amount) VALUES
            (entry_id, gcash_id,      26000, 0), (entry_id, rev_rental_id, 0, 26000);
    END IF; entry_id := NULL;

    INSERT INTO ledger_entries (entry_number, external_reference, entry_date, description)
        VALUES ('JE-2026-F006', 'SEED-FEB-12-RENTAL', '2026-02-12 08:30:00+08', 'Daily rental collections - Feb 12')
        ON CONFLICT (external_reference) DO NOTHING RETURNING id INTO entry_id;
    IF entry_id IS NOT NULL THEN
        INSERT INTO ledger_entry_lines (entry_id, account_id, debit_amount, credit_amount) VALUES
            (entry_id, bank_id,       22000, 0), (entry_id, rev_rental_id, 0, 22000);
    END IF; entry_id := NULL;

    INSERT INTO ledger_entries (entry_number, external_reference, entry_date, description)
        VALUES ('JE-2026-F007', 'SEED-FEB-14-RENTAL', '2026-02-14 09:00:00+08', 'Weekend rental collections - Feb 14')
        ON CONFLICT (external_reference) DO NOTHING RETURNING id INTO entry_id;
    IF entry_id IS NOT NULL THEN
        INSERT INTO ledger_entry_lines (entry_id, account_id, debit_amount, credit_amount) VALUES
            (entry_id, gcash_id,      35000, 0), (entry_id, rev_rental_id, 0, 35000);
    END IF; entry_id := NULL;

    INSERT INTO ledger_entries (entry_number, external_reference, entry_date, description)
        VALUES ('JE-2026-F008', 'SEED-FEB-17-LATE', '2026-02-17 15:00:00+08', 'Late return fees - Feb 17')
        ON CONFLICT (external_reference) DO NOTHING RETURNING id INTO entry_id;
    IF entry_id IS NOT NULL THEN
        INSERT INTO ledger_entry_lines (entry_id, account_id, debit_amount, credit_amount) VALUES
            (entry_id, cash_id,    3000, 0), (entry_id, rev_late_id, 0, 3000);
    END IF; entry_id := NULL;

    INSERT INTO ledger_entries (entry_number, external_reference, entry_date, description)
        VALUES ('JE-2026-F009', 'SEED-FEB-19-RENTAL', '2026-02-19 09:00:00+08', 'Daily rental collections - Feb 19')
        ON CONFLICT (external_reference) DO NOTHING RETURNING id INTO entry_id;
    IF entry_id IS NOT NULL THEN
        INSERT INTO ledger_entry_lines (entry_id, account_id, debit_amount, credit_amount) VALUES
            (entry_id, bank_id,       27500, 0), (entry_id, rev_rental_id, 0, 27500);
    END IF; entry_id := NULL;

    INSERT INTO ledger_entries (entry_number, external_reference, entry_date, description)
        VALUES ('JE-2026-F010', 'SEED-FEB-21-RENTAL', '2026-02-21 08:00:00+08', 'Weekend rental collections - Feb 21')
        ON CONFLICT (external_reference) DO NOTHING RETURNING id INTO entry_id;
    IF entry_id IS NOT NULL THEN
        INSERT INTO ledger_entry_lines (entry_id, account_id, debit_amount, credit_amount) VALUES
            (entry_id, cash_id,       33000, 0), (entry_id, rev_rental_id, 0, 33000);
    END IF; entry_id := NULL;

    INSERT INTO ledger_entries (entry_number, external_reference, entry_date, description)
        VALUES ('JE-2026-F011', 'SEED-FEB-23-DAMAGE', '2026-02-23 12:00:00+08', 'Vehicle damage fees - Feb 23')
        ON CONFLICT (external_reference) DO NOTHING RETURNING id INTO entry_id;
    IF entry_id IS NOT NULL THEN
        INSERT INTO ledger_entry_lines (entry_id, account_id, debit_amount, credit_amount) VALUES
            (entry_id, ar_id,         8000, 0), (entry_id, rev_damage_id, 0, 8000);
    END IF; entry_id := NULL;

    INSERT INTO ledger_entries (entry_number, external_reference, entry_date, description)
        VALUES ('JE-2026-F012', 'SEED-FEB-25-RENTAL', '2026-02-25 09:30:00+08', 'Daily rental collections - Feb 25')
        ON CONFLICT (external_reference) DO NOTHING RETURNING id INTO entry_id;
    IF entry_id IS NOT NULL THEN
        INSERT INTO ledger_entry_lines (entry_id, account_id, debit_amount, credit_amount) VALUES
            (entry_id, gcash_id,      29000, 0), (entry_id, rev_rental_id, 0, 29000);
    END IF; entry_id := NULL;

    INSERT INTO ledger_entries (entry_number, external_reference, entry_date, description)
        VALUES ('JE-2026-F013', 'SEED-FEB-27-RENTAL', '2026-02-27 08:00:00+08', 'Daily rental collections - Feb 27')
        ON CONFLICT (external_reference) DO NOTHING RETURNING id INTO entry_id;
    IF entry_id IS NOT NULL THEN
        INSERT INTO ledger_entry_lines (entry_id, account_id, debit_amount, credit_amount) VALUES
            (entry_id, bank_id,       19500, 0), (entry_id, rev_rental_id, 0, 19500);
    END IF; entry_id := NULL;

    INSERT INTO ledger_entries (entry_number, external_reference, entry_date, description)
        VALUES ('JE-2026-F014', 'SEED-FEB-28-LATE', '2026-02-28 16:00:00+08', 'Late return fees - Feb 28')
        ON CONFLICT (external_reference) DO NOTHING RETURNING id INTO entry_id;
    IF entry_id IS NOT NULL THEN
        INSERT INTO ledger_entry_lines (entry_id, account_id, debit_amount, credit_amount) VALUES
            (entry_id, cash_id,    1800, 0), (entry_id, rev_late_id, 0, 1800);
    END IF; entry_id := NULL;

END $$;

-- Summary of seeded revenue:
-- January 2026: ₱16,000 + ₱14,500 + ₱2,000 + ₱19,500 + ₱7,000 + ₱25,000 + ₱17,000
--             + ₱1,500 + ₱28,500 + ₱21,000 + ₱9,500 + ₱23,000 + ₱31,000 + ₱18,000
--             = ₱233,500
-- February 2026: ₱20,000 + ₱2,500 + ₱24,500 + ₱11,000 + ₱26,000 + ₱22,000 + ₱35,000
--              + ₱3,000 + ₱27,500 + ₱33,000 + ₱8,000 + ₱29,000 + ₱19,500 + ₱1,800
--              = ₱262,800
-- Cumulative YTD with V035: ₱233,500 + ₱262,800 + ₱341,500 + ₱144,000 = ₱981,800
