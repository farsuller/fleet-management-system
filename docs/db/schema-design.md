# Database Schema Design

## Overview

The Fleet Management System uses PostgreSQL as its source of truth with a well-structured relational schema that enforces business rules at the database level.

## Design Principles

1. **UUID Primary Keys**: All tables use UUID primary keys for global uniqueness
2. **Audit Fields**: All tables include `created_at` and `updated_at` timestamps
3. **Optimistic Locking**: Critical tables include a `version` column for concurrency control
4. **Money Storage**: All monetary values stored as whole units (PHP) as integers (Long)
5. **Constraint Enforcement**: Business rules enforced via CHECK constraints and triggers
6. **Immutable Facts**: Historical data (odometer readings, ledger entries) is append-only

## Schema Modules

### 1. Users and Authentication (`V001`)

**Tables**:
- `users`: Core user information with authentication
- `roles`: System roles (ADMIN, FLEET_MANAGER, RENTAL_AGENT, etc.)
- `user_roles`: Many-to-many relationship between users and roles
- `staff_profiles`: Additional information for staff members

**Key Features**:
- Email uniqueness enforced
- Password stored as hash
- Soft delete via `is_active` flag
- Auto-updating `updated_at` trigger

### 2. Vehicles (`V002`)

**Tables**:
- `vehicles`: Core vehicle information
- `odometer_readings`: Historical odometer tracking

**Key Features**:
- Plate number uniqueness
- VIN uniqueness (when provided)
- Status enum: ACTIVE, RENTED, UNDER_MAINTENANCE, DECOMMISSIONED
- Odometer readings are non-decreasing (enforced by trigger)
- Optimistic locking via `version` column

**Business Rules Enforced**:
```sql
-- Odometer cannot decrease
CREATE TRIGGER check_odometer_reading_before_insert
    BEFORE INSERT ON odometer_readings
    FOR EACH ROW
    EXECUTE FUNCTION validate_odometer_reading();

-- Version increments on update
CREATE TRIGGER increment_vehicles_version BEFORE UPDATE ON vehicles
    FOR EACH ROW EXECUTE FUNCTION increment_version();
```

### 3. Rentals (`V003`)

**Tables**:
- `customers`: Customer information
- `rentals`: Core rental information
- `rental_periods`: Tracks active rental periods for double-booking prevention
- `rental_charges`: Additional charges (fuel, damage, late fees)
- `rental_payments`: Payment tracking

**Key Features**:
- **Double-Booking Prevention**: Exclusion constraint prevents overlapping rentals
  ```sql
  EXCLUDE USING GIST (
      vehicle_id WITH =,
      period WITH &&
  ) WHERE (status IN ('RESERVED', 'ACTIVE'))
  ```
- Rental status: RESERVED, ACTIVE, COMPLETED, CANCELLED
- Automatic sync of `rental_periods` table via trigger
- Driver license validation

**Business Rules Enforced**:
- End date must be after start date
- Actual end date must be after actual start date
- End odometer must be >= start odometer

### 4. Maintenance (`V004`)

**Tables**:
- `maintenance_jobs`: Track maintenance work
- `maintenance_parts`: Parts used in jobs
- `maintenance_schedules`: Recurring maintenance requirements

**Key Features**:
- Job types: ROUTINE, REPAIR, INSPECTION, RECALL, EMERGENCY
- Priority levels: LOW, NORMAL, HIGH, URGENT
- Cost tracking (labor + parts)
- Automatic schedule updates after job completion

**Business Rules Enforced**:
- Started date must be >= scheduled date
- Completed date must be >= started date
- Automatic next service calculation based on mileage/time intervals

### 5. Accounting (`V005`)

**Tables**:
- `accounts`: Chart of accounts
- `ledger_entries`: Journal entries (header)
- `ledger_entry_lines`: Individual debit/credit lines
- `invoices`: Customer invoices
- `invoice_line_items`: Invoice details
- `payments`: Payment tracking

**Key Features**:
- **Double-Entry Bookkeeping**: Debits must equal credits (enforced by trigger)
- **Idempotent Posting**: Unique `external_reference` prevents duplicate entries
- **Account Types**: ASSET, LIABILITY, EQUITY, REVENUE, EXPENSE
- **Normal Balance**: Reports handle sign-flipping (Revenue/Liabilities/Equity = Credit - Debit)
- **PHP Currency**: Stored as whole units (Long) for precision and readability

**Business Rules Enforced**:
```sql
-- Validate ledger balance
CREATE CONSTRAINT TRIGGER validate_ledger_balance
    AFTER INSERT OR UPDATE ON ledger_entry_lines
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW
    EXECUTE FUNCTION validate_ledger_entry_balance();
```

**Default Chart of Accounts**:
- **Assets**: Cash (1000), Accounts Receivable (1100), Vehicle Fleet (1500)
- **Liabilities**: Accounts Payable (2000), Customer Deposits (2100)
- **Equity**: Owner Equity (3000), Retained Earnings (3100)
- **Revenue**: Rental Revenue (4000), Late Fees (4100), Damage Fees (4200)
- **Expenses**: Maintenance (5000), Depreciation (5100), Fuel (5200)

### 6. Integration Tables (`V006`)

**Tables**:
- `outbox_events`: Transactional outbox for reliable event publishing
- `inbox_processed_messages`: Idempotent event consumption tracking
- `dlq_messages`: Dead letter queue for failed messages
- `idempotency_keys`: API request idempotency tracking

**Key Features**:
- **Outbox Pattern**: Events written in same transaction as business logic
- **Inbox Pattern**: Prevents duplicate event processing
- **DLQ**: Manual review and replay of failed messages
- **Idempotency Keys**: 24-hour expiration (configurable)

**Cleanup Functions**:
```sql
-- Clean up old outbox events (7 days)
SELECT cleanup_old_outbox_events();

-- Clean up old inbox messages (30 days)
SELECT cleanup_old_inbox_messages();

-- Clean up expired idempotency keys
SELECT cleanup_expired_idempotency_keys();
```

## Indexes

All tables have appropriate indexes for:
- Primary keys (automatic)
- Foreign keys
- Unique constraints
- Common query patterns (status, dates, etc.)

## Triggers

### Auto-Update Triggers
- `update_updated_at_column()`: Updates `updated_at` on all mutable tables
- `increment_version()`: Increments version for optimistic locking

### Business Logic Triggers
- `validate_odometer_reading()`: Ensures odometer readings don't decrease
- `sync_rental_period()`: Maintains rental_periods table for double-booking prevention
- `update_maintenance_schedule_after_job()`: Updates next service dates
- `validate_ledger_entry_balance()`: Ensures debits = credits

## Migration Strategy

### Naming Convention
```
V{version}__{description}.sql
```

Example: `V001__create_users_schema.sql`

### Rollback Strategy
Flyway doesn't support automatic rollback. For production:
1. Test migrations in staging first
2. Create manual rollback scripts if needed
3. Use database backups before major migrations

### Adding New Migrations
1. Create new file with next version number
2. Test locally with H2 (tests) and PostgreSQL (dev)
3. Commit to version control
4. Deploy via CI/CD pipeline

## Performance Considerations

### Connection Pooling
- HikariCP configured with max pool size
- Transaction isolation: REPEATABLE READ

### Query Optimization
- Indexes on all foreign keys
- Composite indexes for common query patterns
- EXPLAIN ANALYZE for slow queries

### Partitioning (Future)
Consider partitioning for:
- `odometer_readings` by date
- `ledger_entry_lines` by date
- `outbox_events` by date

## Security

### Access Control
- Application uses dedicated database user
- Principle of least privilege
- No direct database access in production

### Sensitive Data
- Passwords stored as hashes (bcrypt)
- PII encrypted at application layer (future)
- Audit logging for sensitive operations

## Backup and Recovery

### Backup Strategy
- Daily full backups
- Point-in-time recovery enabled
- Backup retention: 30 days

### Disaster Recovery
- RTO: 4 hours
- RPO: 1 hour
- Regular restore testing

## Monitoring

### Metrics to Track
- Connection pool utilization
- Query performance (slow query log)
- Table sizes and growth
- Index usage
- Lock contention

### Alerts
- Connection pool exhaustion
- Long-running queries (> 30s)
- Failed migrations
- Replication lag (if applicable)

## References

- [PostgreSQL Documentation](https://www.postgresql.org/docs/)
- [Flyway Documentation](https://flywaydb.org/documentation/)
- [Exposed ORM](https://github.com/JetBrains/Exposed)
