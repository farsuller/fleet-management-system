-- V035: Seed two months of revenue ledger entries (March & April 2026)
-- Generates realistic rental + maintenance + late-fee revenue for the Reports screen.
-- All amounts in whole PHP pesos (system uses integers = PHP, not centavos).
-- Each entry is a double-entry: DR Cash/Bank, CR Revenue account.
-- Uses ON CONFLICT on external_reference to stay idempotent.

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
    e_num          INT := 1;
BEGIN
    -- Resolve account IDs from codes
    SELECT id INTO cash_id       FROM accounts WHERE account_code = '1000';
    SELECT id INTO bank_id       FROM accounts WHERE account_code = '1010';
    SELECT id INTO gcash_id      FROM accounts WHERE account_code = '1020';
    SELECT id INTO rev_rental_id FROM accounts WHERE account_code = '4000';
    SELECT id INTO rev_late_id   FROM accounts WHERE account_code = '4100';
    SELECT id INTO rev_damage_id FROM accounts WHERE account_code = '4200';
    SELECT id INTO ar_id         FROM accounts WHERE account_code = '1100';

    -- Helper: insert one balanced entry (DR asset, CR revenue)
    -- Called inline per date below

    -- ── MARCH 2026 ─────────────────────────────────────────────────────────
    -- Week 1 (Mar 1–7)
    INSERT INTO ledger_entries (entry_number, external_reference, entry_date, description)
        VALUES ('JE-2026-0001', 'SEED-MAR-01-RENTAL', '2026-03-01 08:00:00+08', 'Daily rental collections - Mar 1')
        ON CONFLICT (external_reference) DO NOTHING
        RETURNING id INTO entry_id;
    IF entry_id IS NOT NULL THEN
        INSERT INTO ledger_entry_lines (entry_id, account_id, debit_amount, credit_amount) VALUES
            (entry_id, cash_id,       18500, 0),
            (entry_id, rev_rental_id, 0, 18500);
    END IF; entry_id := NULL;

    INSERT INTO ledger_entries (entry_number, external_reference, entry_date, description)
        VALUES ('JE-2026-0002', 'SEED-MAR-03-RENTAL', '2026-03-03 09:00:00+08', 'Daily rental collections - Mar 3')
        ON CONFLICT (external_reference) DO NOTHING
        RETURNING id INTO entry_id;
    IF entry_id IS NOT NULL THEN
        INSERT INTO ledger_entry_lines (entry_id, account_id, debit_amount, credit_amount) VALUES
            (entry_id, bank_id,       22000, 0),
            (entry_id, rev_rental_id, 0, 22000);
    END IF; entry_id := NULL;

    INSERT INTO ledger_entries (entry_number, external_reference, entry_date, description)
        VALUES ('JE-2026-0003', 'SEED-MAR-05-RENTAL', '2026-03-05 10:00:00+08', 'Daily rental collections - Mar 5')
        ON CONFLICT (external_reference) DO NOTHING
        RETURNING id INTO entry_id;
    IF entry_id IS NOT NULL THEN
        INSERT INTO ledger_entry_lines (entry_id, account_id, debit_amount, credit_amount) VALUES
            (entry_id, gcash_id,      15000, 0),
            (entry_id, rev_rental_id, 0, 15000);
    END IF; entry_id := NULL;

    INSERT INTO ledger_entries (entry_number, external_reference, entry_date, description)
        VALUES ('JE-2026-0004', 'SEED-MAR-07-LATE', '2026-03-07 14:00:00+08', 'Late return fees - Mar 7')
        ON CONFLICT (external_reference) DO NOTHING
        RETURNING id INTO entry_id;
    IF entry_id IS NOT NULL THEN
        INSERT INTO ledger_entry_lines (entry_id, account_id, debit_amount, credit_amount) VALUES
            (entry_id, cash_id,    3500, 0),
            (entry_id, rev_late_id, 0, 3500);
    END IF; entry_id := NULL;

    -- Week 2 (Mar 8–14)
    INSERT INTO ledger_entries (entry_number, external_reference, entry_date, description)
        VALUES ('JE-2026-0005', 'SEED-MAR-08-RENTAL', '2026-03-08 08:30:00+08', 'Daily rental collections - Mar 8')
        ON CONFLICT (external_reference) DO NOTHING
        RETURNING id INTO entry_id;
    IF entry_id IS NOT NULL THEN
        INSERT INTO ledger_entry_lines (entry_id, account_id, debit_amount, credit_amount) VALUES
            (entry_id, bank_id,       28500, 0),
            (entry_id, rev_rental_id, 0, 28500);
    END IF; entry_id := NULL;

    INSERT INTO ledger_entries (entry_number, external_reference, entry_date, description)
        VALUES ('JE-2026-0006', 'SEED-MAR-10-RENTAL', '2026-03-10 09:00:00+08', 'Daily rental collections - Mar 10')
        ON CONFLICT (external_reference) DO NOTHING
        RETURNING id INTO entry_id;
    IF entry_id IS NOT NULL THEN
        INSERT INTO ledger_entry_lines (entry_id, account_id, debit_amount, credit_amount) VALUES
            (entry_id, cash_id,       19000, 0),
            (entry_id, rev_rental_id, 0, 19000);
    END IF; entry_id := NULL;

    INSERT INTO ledger_entries (entry_number, external_reference, entry_date, description)
        VALUES ('JE-2026-0007', 'SEED-MAR-12-DAMAGE', '2026-03-12 11:00:00+08', 'Vehicle damage fees - Mar 12')
        ON CONFLICT (external_reference) DO NOTHING
        RETURNING id INTO entry_id;
    IF entry_id IS NOT NULL THEN
        INSERT INTO ledger_entry_lines (entry_id, account_id, debit_amount, credit_amount) VALUES
            (entry_id, ar_id,          8500, 0),
            (entry_id, rev_damage_id,  0, 8500);
    END IF; entry_id := NULL;

    INSERT INTO ledger_entries (entry_number, external_reference, entry_date, description)
        VALUES ('JE-2026-0008', 'SEED-MAR-14-RENTAL', '2026-03-14 08:00:00+08', 'Weekend rental collections - Mar 14')
        ON CONFLICT (external_reference) DO NOTHING
        RETURNING id INTO entry_id;
    IF entry_id IS NOT NULL THEN
        INSERT INTO ledger_entry_lines (entry_id, account_id, debit_amount, credit_amount) VALUES
            (entry_id, gcash_id,      34000, 0),
            (entry_id, rev_rental_id, 0, 34000);
    END IF; entry_id := NULL;

    -- Week 3 (Mar 15–21)
    INSERT INTO ledger_entries (entry_number, external_reference, entry_date, description)
        VALUES ('JE-2026-0009', 'SEED-MAR-15-RENTAL', '2026-03-15 09:00:00+08', 'Daily rental collections - Mar 15')
        ON CONFLICT (external_reference) DO NOTHING
        RETURNING id INTO entry_id;
    IF entry_id IS NOT NULL THEN
        INSERT INTO ledger_entry_lines (entry_id, account_id, debit_amount, credit_amount) VALUES
            (entry_id, bank_id,       24000, 0),
            (entry_id, rev_rental_id, 0, 24000);
    END IF; entry_id := NULL;

    INSERT INTO ledger_entries (entry_number, external_reference, entry_date, description)
        VALUES ('JE-2026-0010', 'SEED-MAR-17-RENTAL', '2026-03-17 10:00:00+08', 'Daily rental collections - Mar 17')
        ON CONFLICT (external_reference) DO NOTHING
        RETURNING id INTO entry_id;
    IF entry_id IS NOT NULL THEN
        INSERT INTO ledger_entry_lines (entry_id, account_id, debit_amount, credit_amount) VALUES
            (entry_id, cash_id,       21000, 0),
            (entry_id, rev_rental_id, 0, 21000);
    END IF; entry_id := NULL;

    INSERT INTO ledger_entries (entry_number, external_reference, entry_date, description)
        VALUES ('JE-2026-0011', 'SEED-MAR-19-LATE', '2026-03-19 15:00:00+08', 'Late return fees - Mar 19')
        ON CONFLICT (external_reference) DO NOTHING
        RETURNING id INTO entry_id;
    IF entry_id IS NOT NULL THEN
        INSERT INTO ledger_entry_lines (entry_id, account_id, debit_amount, credit_amount) VALUES
            (entry_id, cash_id,    2500, 0),
            (entry_id, rev_late_id, 0, 2500);
    END IF; entry_id := NULL;

    INSERT INTO ledger_entries (entry_number, external_reference, entry_date, description)
        VALUES ('JE-2026-0012', 'SEED-MAR-21-RENTAL', '2026-03-21 08:00:00+08', 'Weekend rental collections - Mar 21')
        ON CONFLICT (external_reference) DO NOTHING
        RETURNING id INTO entry_id;
    IF entry_id IS NOT NULL THEN
        INSERT INTO ledger_entry_lines (entry_id, account_id, debit_amount, credit_amount) VALUES
            (entry_id, gcash_id,      31500, 0),
            (entry_id, rev_rental_id, 0, 31500);
    END IF; entry_id := NULL;

    -- Week 4 (Mar 22–28)
    INSERT INTO ledger_entries (entry_number, external_reference, entry_date, description)
        VALUES ('JE-2026-0013', 'SEED-MAR-22-RENTAL', '2026-03-22 09:00:00+08', 'Daily rental collections - Mar 22')
        ON CONFLICT (external_reference) DO NOTHING
        RETURNING id INTO entry_id;
    IF entry_id IS NOT NULL THEN
        INSERT INTO ledger_entry_lines (entry_id, account_id, debit_amount, credit_amount) VALUES
            (entry_id, bank_id,       26000, 0),
            (entry_id, rev_rental_id, 0, 26000);
    END IF; entry_id := NULL;

    INSERT INTO ledger_entries (entry_number, external_reference, entry_date, description)
        VALUES ('JE-2026-0014', 'SEED-MAR-24-DAMAGE', '2026-03-24 12:00:00+08', 'Vehicle damage fees - Mar 24')
        ON CONFLICT (external_reference) DO NOTHING
        RETURNING id INTO entry_id;
    IF entry_id IS NOT NULL THEN
        INSERT INTO ledger_entry_lines (entry_id, account_id, debit_amount, credit_amount) VALUES
            (entry_id, ar_id,          12000, 0),
            (entry_id, rev_damage_id,  0, 12000);
    END IF; entry_id := NULL;

    INSERT INTO ledger_entries (entry_number, external_reference, entry_date, description)
        VALUES ('JE-2026-0015', 'SEED-MAR-26-RENTAL', '2026-03-26 09:30:00+08', 'Daily rental collections - Mar 26')
        ON CONFLICT (external_reference) DO NOTHING
        RETURNING id INTO entry_id;
    IF entry_id IS NOT NULL THEN
        INSERT INTO ledger_entry_lines (entry_id, account_id, debit_amount, credit_amount) VALUES
            (entry_id, cash_id,       17500, 0),
            (entry_id, rev_rental_id, 0, 17500);
    END IF; entry_id := NULL;

    INSERT INTO ledger_entries (entry_number, external_reference, entry_date, description)
        VALUES ('JE-2026-0016', 'SEED-MAR-28-RENTAL', '2026-03-28 08:00:00+08', 'Weekend rental collections - Mar 28')
        ON CONFLICT (external_reference) DO NOTHING
        RETURNING id INTO entry_id;
    IF entry_id IS NOT NULL THEN
        INSERT INTO ledger_entry_lines (entry_id, account_id, debit_amount, credit_amount) VALUES
            (entry_id, gcash_id,      38000, 0),
            (entry_id, rev_rental_id, 0, 38000);
    END IF; entry_id := NULL;

    -- Mar 29–31
    INSERT INTO ledger_entries (entry_number, external_reference, entry_date, description)
        VALUES ('JE-2026-0017', 'SEED-MAR-30-RENTAL', '2026-03-30 09:00:00+08', 'Daily rental collections - Mar 30')
        ON CONFLICT (external_reference) DO NOTHING
        RETURNING id INTO entry_id;
    IF entry_id IS NOT NULL THEN
        INSERT INTO ledger_entry_lines (entry_id, account_id, debit_amount, credit_amount) VALUES
            (entry_id, bank_id,       20000, 0),
            (entry_id, rev_rental_id, 0, 20000);
    END IF; entry_id := NULL;

    -- ── APRIL 2026 ──────────────────────────────────────────────────────────
    INSERT INTO ledger_entries (entry_number, external_reference, entry_date, description)
        VALUES ('JE-2026-0018', 'SEED-APR-01-RENTAL', '2026-04-01 08:00:00+08', 'Daily rental collections - Apr 1')
        ON CONFLICT (external_reference) DO NOTHING
        RETURNING id INTO entry_id;
    IF entry_id IS NOT NULL THEN
        INSERT INTO ledger_entry_lines (entry_id, account_id, debit_amount, credit_amount) VALUES
            (entry_id, cash_id,       23500, 0),
            (entry_id, rev_rental_id, 0, 23500);
    END IF; entry_id := NULL;

    INSERT INTO ledger_entries (entry_number, external_reference, entry_date, description)
        VALUES ('JE-2026-0019', 'SEED-APR-02-LATE', '2026-04-02 14:30:00+08', 'Late return fees - Apr 2')
        ON CONFLICT (external_reference) DO NOTHING
        RETURNING id INTO entry_id;
    IF entry_id IS NOT NULL THEN
        INSERT INTO ledger_entry_lines (entry_id, account_id, debit_amount, credit_amount) VALUES
            (entry_id, cash_id,    4000, 0),
            (entry_id, rev_late_id, 0, 4000);
    END IF; entry_id := NULL;

    INSERT INTO ledger_entries (entry_number, external_reference, entry_date, description)
        VALUES ('JE-2026-0020', 'SEED-APR-04-RENTAL', '2026-04-04 09:00:00+08', 'Daily rental collections - Apr 4')
        ON CONFLICT (external_reference) DO NOTHING
        RETURNING id INTO entry_id;
    IF entry_id IS NOT NULL THEN
        INSERT INTO ledger_entry_lines (entry_id, account_id, debit_amount, credit_amount) VALUES
            (entry_id, gcash_id,      27000, 0),
            (entry_id, rev_rental_id, 0, 27000);
    END IF; entry_id := NULL;

    INSERT INTO ledger_entries (entry_number, external_reference, entry_date, description)
        VALUES ('JE-2026-0021', 'SEED-APR-05-DAMAGE', '2026-04-05 11:00:00+08', 'Vehicle damage fees - Apr 5')
        ON CONFLICT (external_reference) DO NOTHING
        RETURNING id INTO entry_id;
    IF entry_id IS NOT NULL THEN
        INSERT INTO ledger_entry_lines (entry_id, account_id, debit_amount, credit_amount) VALUES
            (entry_id, ar_id,          9500, 0),
            (entry_id, rev_damage_id,  0, 9500);
    END IF; entry_id := NULL;

    INSERT INTO ledger_entries (entry_number, external_reference, entry_date, description)
        VALUES ('JE-2026-0022', 'SEED-APR-07-RENTAL', '2026-04-07 08:30:00+08', 'Daily rental collections - Apr 7')
        ON CONFLICT (external_reference) DO NOTHING
        RETURNING id INTO entry_id;
    IF entry_id IS NOT NULL THEN
        INSERT INTO ledger_entry_lines (entry_id, account_id, debit_amount, credit_amount) VALUES
            (entry_id, bank_id,       32000, 0),
            (entry_id, rev_rental_id, 0, 32000);
    END IF; entry_id := NULL;

    INSERT INTO ledger_entries (entry_number, external_reference, entry_date, description)
        VALUES ('JE-2026-0023', 'SEED-APR-08-RENTAL', '2026-04-08 09:00:00+08', 'Daily rental collections - Apr 8')
        ON CONFLICT (external_reference) DO NOTHING
        RETURNING id INTO entry_id;
    IF entry_id IS NOT NULL THEN
        INSERT INTO ledger_entry_lines (entry_id, account_id, debit_amount, credit_amount) VALUES
            (entry_id, cash_id,       19500, 0),
            (entry_id, rev_rental_id, 0, 19500);
    END IF; entry_id := NULL;

    INSERT INTO ledger_entries (entry_number, external_reference, entry_date, description)
        VALUES ('JE-2026-0024', 'SEED-APR-10-LATE', '2026-04-10 16:00:00+08', 'Late return fees - Apr 10')
        ON CONFLICT (external_reference) DO NOTHING
        RETURNING id INTO entry_id;
    IF entry_id IS NOT NULL THEN
        INSERT INTO ledger_entry_lines (entry_id, account_id, debit_amount, credit_amount) VALUES
            (entry_id, cash_id,    3000, 0),
            (entry_id, rev_late_id, 0, 3000);
    END IF; entry_id := NULL;

    INSERT INTO ledger_entries (entry_number, external_reference, entry_date, description)
        VALUES ('JE-2026-0025', 'SEED-APR-12-RENTAL', '2026-04-12 08:00:00+08', 'Daily rental collections - Apr 12')
        ON CONFLICT (external_reference) DO NOTHING
        RETURNING id INTO entry_id;
    IF entry_id IS NOT NULL THEN
        INSERT INTO ledger_entry_lines (entry_id, account_id, debit_amount, credit_amount) VALUES
            (entry_id, gcash_id,      25500, 0),
            (entry_id, rev_rental_id, 0, 25500);
    END IF; entry_id := NULL;

END $$;

-- Summary of seeded revenue:
-- March 2026: ₱18,500 + ₱22,000 + ₱15,000 + ₱3,500 + ₱28,500 + ₱19,000 + ₱8,500 + ₱34,000
--           + ₱24,000 + ₱21,000 + ₱2,500 + ₱31,500 + ₱26,000 + ₱12,000 + ₱17,500 + ₱38,000 + ₱20,000
--           = ₱341,500
-- April 2026: ₱23,500 + ₱4,000 + ₱27,000 + ₱9,500 + ₱32,000 + ₱19,500 + ₱3,000 + ₱25,500
--           = ₱144,000
-- Total: ₱485,500
