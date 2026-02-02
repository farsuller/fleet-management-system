# Database Constraints and Business Invariants

## Overview

This document describes the business rules enforced at the database level through constraints, triggers, and other PostgreSQL features.

## Critical Business Invariants

### 1. Double-Booking Prevention

**Business Rule**: A vehicle cannot be rented to multiple customers during overlapping time periods.

**Implementation**:
```sql
-- Exclusion constraint using PostgreSQL's GIST index
EXCLUDE USING GIST (
    vehicle_id WITH =,
    period WITH &&
) WHERE (status IN ('RESERVED', 'ACTIVE'))
```

**How It Works**:
- Uses PostgreSQL's `tstzrange` type for time periods
- GIST index enables efficient overlap detection
- Only applies to RESERVED and ACTIVE rentals
- COMPLETED and CANCELLED rentals don't block future bookings

**Testing**:
```sql
-- This should succeed
INSERT INTO rental_periods (rental_id, vehicle_id, period, status)
VALUES (
    gen_random_uuid(),
    'vehicle-uuid-here',
    tstzrange('2024-03-01 10:00:00+00', '2024-03-05 10:00:00+00'),
    'RESERVED'
);

-- This should fail with exclusion constraint violation
INSERT INTO rental_periods (rental_id, vehicle_id, period, status)
VALUES (
    gen_random_uuid(),
    'vehicle-uuid-here',  -- Same vehicle
    tstzrange('2024-03-03 10:00:00+00', '2024-03-07 10:00:00+00'),  -- Overlapping period
    'RESERVED'
);
```

### 2. Non-Decreasing Odometer Readings

**Business Rule**: Odometer readings can only increase, never decrease.

**Implementation**:
```sql
CREATE OR REPLACE FUNCTION validate_odometer_reading()
RETURNS TRIGGER AS $$
DECLARE
    last_reading INTEGER;
BEGIN
    SELECT reading_km INTO last_reading
    FROM odometer_readings
    WHERE vehicle_id = NEW.vehicle_id
    ORDER BY recorded_at DESC
    LIMIT 1;
    
    IF last_reading IS NOT NULL AND NEW.reading_km < last_reading THEN
        RAISE EXCEPTION 'Odometer reading cannot decrease. Last: %, New: %', 
            last_reading, NEW.reading_km;
    END IF;
    
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
```

**Why It Matters**:
- Prevents data entry errors
- Ensures accurate mileage tracking
- Critical for maintenance scheduling
- Important for vehicle valuation

### 3. Double-Entry Bookkeeping Balance

**Business Rule**: For every ledger entry, total debits must equal total credits.

**Implementation**:
```sql
CREATE OR REPLACE FUNCTION validate_ledger_entry_balance()
RETURNS TRIGGER AS $$
DECLARE
    total_debits BIGINT;
    total_credits BIGINT;
BEGIN
    SELECT 
        COALESCE(SUM(debit_amount_cents), 0),
        COALESCE(SUM(credit_amount_cents), 0)
    INTO total_debits, total_credits
    FROM ledger_entry_lines
    WHERE entry_id = NEW.entry_id;
    
    IF total_debits != total_credits THEN
        RAISE EXCEPTION 'Ledger entry % is unbalanced: debits = %, credits = %', 
            NEW.entry_id, total_debits, total_credits;
    END IF;
    
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
```

**Trigger Type**: CONSTRAINT TRIGGER (deferred)
- Allows all lines to be inserted before validation
- Validates at transaction commit time
- Ensures accounting integrity

### 4. Idempotent Ledger Posting

**Business Rule**: The same business event should not create duplicate ledger entries.

**Implementation**:
```sql
CREATE TABLE ledger_entries (
    ...
    external_reference VARCHAR(255) NOT NULL UNIQUE,
    ...
);
```

**Usage Pattern**:
```kotlin
fun postRentalActivation(rentalId: UUID) {
    val externalReference = "rental-$rentalId-activation"
    
    // This will fail if already posted
    insertLedgerEntry(
        externalReference = externalReference,
        lines = listOf(
            DebitLine(account = "1100", amount = 50000),  // Accounts Receivable
            CreditLine(account = "4000", amount = 50000)  // Rental Revenue
        )
    )
}
```

## Data Integrity Constraints

### Unique Constraints

| Table | Column(s) | Purpose |
|-------|-----------|---------|
| users | email | One account per email address |
| vehicles | plate_number | Unique vehicle identification |
| vehicles | vin | Unique vehicle identification number |
| customers | email | One customer record per email |
| customers | driver_license_number | One license per customer |
| rentals | rental_number | Unique rental identifier |
| maintenance_jobs | job_number | Unique job identifier |
| ledger_entries | external_reference | Idempotent posting |
| inbox_processed_messages | (message_id, consumer_group) | Idempotent consumption |

### Check Constraints

#### Vehicles
```sql
CHECK (year >= 1900 AND year <= 2100)
CHECK (current_odometer_km >= 0)
CHECK (passenger_capacity > 0)
CHECK (status IN ('ACTIVE', 'RENTED', 'UNDER_MAINTENANCE', 'DECOMMISSIONED'))
```

#### Rentals
```sql
CHECK (end_date > start_date)
CHECK (actual_end_date IS NULL OR actual_end_date >= actual_start_date)
CHECK (end_odometer_km IS NULL OR end_odometer_km >= start_odometer_km)
CHECK (daily_rate_cents >= 0)
CHECK (total_amount_cents >= 0)
```

#### Maintenance Jobs
```sql
CHECK (started_at IS NULL OR started_at >= scheduled_date)
CHECK (completed_at IS NULL OR completed_at >= started_at)
CHECK (labor_cost_cents >= 0)
CHECK (parts_cost_cents >= 0)
```

#### Accounting
```sql
-- Each ledger line must be either debit or credit, not both
CHECK (
    (debit_amount_cents > 0 AND credit_amount_cents = 0) OR
    (debit_amount_cents = 0 AND credit_amount_cents > 0)
)
```

### Foreign Key Constraints

All foreign keys use `ON DELETE` actions:
- `CASCADE`: Delete dependent records (e.g., rental_charges when rental deleted)
- `SET NULL`: Preserve record but clear reference (e.g., customer.user_id)
- `RESTRICT`: Prevent deletion if references exist (default)

## Optimistic Locking

**Purpose**: Prevent lost updates in concurrent scenarios.

**Implementation**:
```sql
-- Version column on critical tables
version BIGINT NOT NULL DEFAULT 0

-- Trigger to increment version
CREATE TRIGGER increment_vehicles_version BEFORE UPDATE ON vehicles
    FOR EACH ROW EXECUTE FUNCTION increment_version();
```

**Application Usage**:
```kotlin
fun updateVehicle(vehicle: Vehicle, expectedVersion: Long) {
    val updated = db.update(vehicles) {
        where { 
            (vehicles.id eq vehicle.id) and 
            (vehicles.version eq expectedVersion)
        }
        set(vehicles.status, vehicle.status)
        set(vehicles.version, expectedVersion + 1)
    }
    
    if (updated == 0) {
        throw ConcurrentModificationException("Vehicle was modified by another transaction")
    }
}
```

## Generated Columns

### Computed Totals
```sql
-- Maintenance job total cost
total_cost_cents INTEGER GENERATED ALWAYS AS (labor_cost_cents + parts_cost_cents) STORED

-- Invoice totals
total_cents INTEGER GENERATED ALWAYS AS (subtotal_cents + tax_cents) STORED
balance_cents INTEGER GENERATED ALWAYS AS (subtotal_cents + tax_cents - paid_cents) STORED
```

**Benefits**:
- Always accurate (can't get out of sync)
- Indexed for efficient queries
- Reduced application logic

## Automatic Timestamps

### Updated At Trigger
```sql
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
```

**Applied To**:
- users
- vehicles
- customers
- rentals
- maintenance_jobs
- invoices
- payments
- accounts

## Validation Rules

### Email Format
Currently enforced at application layer. Consider adding:
```sql
CHECK (email ~* '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Z|a-z]{2,}$')
```

### Phone Format
Currently flexible. Can add country-specific validation if needed.

### Currency Codes
```sql
CHECK (currency_code IN ('USD', 'EUR', 'GBP', 'JPY', ...))
```

## Soft Deletes

### Users
```sql
is_active BOOLEAN NOT NULL DEFAULT true
```

**Benefits**:
- Preserve historical data
- Maintain referential integrity
- Enable account reactivation
- Audit trail

**Queries**:
```sql
-- Active users only
SELECT * FROM users WHERE is_active = true;

-- Include inactive
SELECT * FROM users;
```

## Invariant Testing

### Test Double-Booking Prevention
```sql
BEGIN;
    -- Create two overlapping rentals
    INSERT INTO rental_periods ...;
    INSERT INTO rental_periods ...;  -- Should fail
ROLLBACK;
```

### Test Odometer Validation
```sql
BEGIN;
    INSERT INTO odometer_readings (vehicle_id, reading_km) VALUES ('...', 1000);
    INSERT INTO odometer_readings (vehicle_id, reading_km) VALUES ('...', 900);  -- Should fail
ROLLBACK;
```

### Test Ledger Balance
```sql
BEGIN;
    INSERT INTO ledger_entries ...;
    INSERT INTO ledger_entry_lines (debit_amount_cents) VALUES (1000);
    INSERT INTO ledger_entry_lines (credit_amount_cents) VALUES (500);  -- Unbalanced, should fail
ROLLBACK;
```

## Performance Impact

### Constraint Checking Overhead
- Unique constraints: Minimal (index lookup)
- Check constraints: Negligible (simple comparison)
- Exclusion constraints: Moderate (GIST index scan)
- Triggers: Low to moderate (depends on complexity)

### Optimization Tips
1. Use partial indexes for conditional constraints
2. Defer constraint checking when possible
3. Batch operations in transactions
4. Monitor slow query log for constraint-related issues

## Migration Considerations

### Adding Constraints to Existing Data
```sql
-- 1. Add constraint as NOT VALID (doesn't check existing data)
ALTER TABLE vehicles 
ADD CONSTRAINT check_year 
CHECK (year >= 1900 AND year <= 2100) NOT VALID;

-- 2. Validate constraint (checks existing data, can be done during low traffic)
ALTER TABLE vehicles VALIDATE CONSTRAINT check_year;
```

### Removing Constraints
```sql
-- Drop constraint
ALTER TABLE vehicles DROP CONSTRAINT check_year;

-- Drop trigger
DROP TRIGGER IF EXISTS validate_odometer_reading ON odometer_readings;
```

## References

- [PostgreSQL Constraints](https://www.postgresql.org/docs/current/ddl-constraints.html)
- [PostgreSQL Triggers](https://www.postgresql.org/docs/current/triggers.html)
- [GIST Indexes](https://www.postgresql.org/docs/current/gist.html)
- [Range Types](https://www.postgresql.org/docs/current/rangetypes.html)
