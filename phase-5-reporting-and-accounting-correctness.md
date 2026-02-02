# Phase 5 — Reporting and Accounting Correctness

## Status

- Overall: **Not Started**
- Implementation Date: TBD
- Verification: Pending

---

## Purpose

Make reporting and accounting **reproducible and auditable** using immutable facts (ledger postings) and derived read models/snapshots.

---

## Depends on

- Phase 2 schema v1 (ledger tables and invariants)
- Phase 3 API v1 (posting and read APIs)
- Phase 4 eventing (optional but recommended for projections)

---

## Inputs / Constraints

- Double-entry bookkeeping principles
- Immutable ledger entries (append-only)
- Idempotent posting via unique external references
- Money stored as cents + currency code
- All financial calculations in domain layer
- Audit trail for all transactions
- Reconciliation between operational and financial data

---

## Implementation Breakdown

| Item | Status | Notes / Definition |
|------|--------|-------------------|
| Define accounting rules | Not Started | Posting rules per business event (rental activation/completion, maintenance cost, payment capture) |
| Enforce idempotent postings | Not Started | Unique external reference per journal entry; safe retries |
| Read models for reports | Not Started | Materialized views or query projections; never overwrite facts |
| Report snapshot strategy | Not Started | Append-only snapshots; parameters captured; reproducible outputs |
| Reconciliation checks | Not Started | Detect drifts between rentals/payments and ledger; alerting strategy |
| Performance plan | Not Started | Indexing, query plans, caching policy (if any) |
| Financial reports | Not Started | Revenue, expenses, outstanding balances, aging reports |
| Audit trail | Not Started | Complete history of all financial transactions |

---

## Definition of Done (Phase 5)

- [ ] Financial reports are derived from immutable facts and can be regenerated
- [ ] Ledger postings are idempotent and auditable
- [ ] Optional snapshots are append-only and traceable to input parameters
- [ ] Reconciliation processes detect and alert on discrepancies
- [ ] All accounting rules documented and tested
- [ ] Performance acceptable for reporting queries
- [ ] Audit trail complete and queryable

---

## Implementation Summary

### ✅ Core Features Implemented

*This section will be populated during implementation with:*

#### 1. **Double-Entry Ledger**
**Chart of Accounts**:
- **Assets**
  - Cash (1000)
  - Accounts Receivable (1100)
  - Vehicle Fleet (1500)
- **Liabilities**
  - Accounts Payable (2000)
  - Customer Deposits (2100)
- **Revenue**
  - Rental Revenue (4000)
  - Late Fees (4100)
- **Expenses**
  - Maintenance Costs (5000)
  - Depreciation (5100)

**Posting Rules**:
```kotlin
// Rental activation: Create receivable
DebitAccount(AccountsReceivable, rentalAmount)
CreditAccount(RentalRevenue, rentalAmount)

// Payment received: Clear receivable
DebitAccount(Cash, paymentAmount)
CreditAccount(AccountsReceivable, paymentAmount)

// Maintenance cost: Record expense
DebitAccount(MaintenanceCosts, maintenanceAmount)
CreditAccount(Cash, maintenanceAmount)
```

#### 2. **Idempotent Posting**
**Implementation**:
```kotlin
fun postToLedger(
    externalReference: String,  // Unique key (e.g., "rental-123-activation")
    entries: List<JournalEntry>
) {
    // Check if already posted
    if (ledgerRepository.existsByExternalReference(externalReference)) {
        logger.info("Already posted: $externalReference")
        return
    }
    
    transaction {
        // Validate double-entry (debits = credits)
        require(entries.sumOf { it.debitAmount } == entries.sumOf { it.creditAmount })
        
        // Post all entries with same external reference
        entries.forEach { entry ->
            ledgerRepository.insert(
                entry.copy(externalReference = externalReference)
            )
        }
    }
}
```

#### 3. **Financial Reports**
**Reports Implemented**:
- **Revenue Report**: Total rental revenue by period
- **Expense Report**: Maintenance and operational costs
- **Outstanding Balances**: Accounts receivable aging
- **Cash Flow**: Cash in/out by category
- **Profit & Loss**: Revenue minus expenses
- **Balance Sheet**: Assets, liabilities, equity

#### 4. **Reconciliation**
**Reconciliation Checks**:
- Rental revenue in ledger matches completed rentals
- Payments in ledger match payment records
- Maintenance costs in ledger match maintenance jobs
- Account balances sum to zero (double-entry validation)

---

## Verification

### Accounting Tests

*This section will be populated with:*
- Ledger posting tests
- Idempotency verification
- Report accuracy validation
- Reconciliation test results
- Performance benchmarks

---

## Architecture Structure

### Accounting and Reporting Layer
```
src/main/kotlin/com/example/
├── accounting/
│   ├── domain/
│   │   ├── models/
│   │   │   ├── LedgerEntry.kt             ✅ (Phase 2)
│   │   │   ├── Account.kt                 (Phase 5)
│   │   │   ├── JournalEntry.kt            (Phase 5)
│   │   │   └── Money.kt                   (Phase 5)
│   │   ├── ports/
│   │   │   ├── LedgerRepository.kt        ✅ (Phase 2)
│   │   │   └── ReportRepository.kt        (Phase 5)
│   │   └── rules/
│   │       ├── RentalPostingRules.kt      (Phase 5)
│   │       ├── MaintenancePostingRules.kt (Phase 5)
│   │       └── PaymentPostingRules.kt     (Phase 5)
│   ├── application/
│   │   ├── usecases/
│   │   │   ├── PostToLedgerUseCase.kt     (Phase 5)
│   │   │   ├── GenerateReportUseCase.kt   (Phase 5)
│   │   │   └── ReconcileUseCase.kt        (Phase 5)
│   │   └── dto/
│   │       ├── LedgerRequest.kt           (Phase 5)
│   │       └── ReportResponse.kt          (Phase 5)
│   └── infrastructure/
│       ├── persistence/
│       │   ├── LedgerRepositoryImpl.kt    ✅ (Phase 2)
│       │   └── ReportRepositoryImpl.kt    (Phase 5)
│       ├── http/
│       │   ├── LedgerRoutes.kt            ✅ (Phase 3)
│       │   └── ReportRoutes.kt            (Phase 5)
│       └── messaging/
│           └── handlers/
│               ├── RentalEventHandler.kt  ✅ (Phase 4)
│               └── MaintenanceEventHandler.kt ✅ (Phase 4)
├── reporting/
│   ├── domain/
│   │   ├── models/
│   │   │   ├── RevenueReport.kt           (Phase 5)
│   │   │   ├── ExpenseReport.kt           (Phase 5)
│   │   │   └── BalanceSheet.kt            (Phase 5)
│   │   └── ports/
│   │       └── ReportGenerator.kt         (Phase 5)
│   ├── application/
│   │   └── usecases/
│   │       ├── GenerateRevenueReportUseCase.kt (Phase 5)
│   │       └── GenerateBalanceSheetUseCase.kt  (Phase 5)
│   └── infrastructure/
│       ├── queries/
│       │   ├── RevenueQueries.kt          (Phase 5)
│       │   └── ExpenseQueries.kt          (Phase 5)
│       └── snapshots/
│           └── ReportSnapshotRepository.kt (Phase 5)
└── shared/
    └── domain/
        └── valueobjects/
            └── Money.kt                    (Phase 5)

docs/accounting/
├── chart-of-accounts.md                   (Phase 5)
├── posting-rules.md                       (Phase 5)
├── reconciliation-procedures.md           (Phase 5)
└── report-definitions.md                  (Phase 5)
```

---

## Code Impact (Repository Artifacts)

### Files Created (Expected)

**Domain Models** (~8 files):
1. `src/main/kotlin/com/example/accounting/domain/models/Account.kt`
2. `src/main/kotlin/com/example/accounting/domain/models/JournalEntry.kt`
3. `src/main/kotlin/com/example/shared/domain/valueobjects/Money.kt`
4. `src/main/kotlin/com/example/reporting/domain/models/RevenueReport.kt`
5. `src/main/kotlin/com/example/reporting/domain/models/ExpenseReport.kt`
6. `src/main/kotlin/com/example/reporting/domain/models/BalanceSheet.kt`

**Posting Rules** (~3 files):
1. `src/main/kotlin/com/example/accounting/domain/rules/RentalPostingRules.kt`
2. `src/main/kotlin/com/example/accounting/domain/rules/MaintenancePostingRules.kt`
3. `src/main/kotlin/com/example/accounting/domain/rules/PaymentPostingRules.kt`

**Use Cases** (~6 files):
1. `src/main/kotlin/com/example/accounting/application/usecases/PostToLedgerUseCase.kt`
2. `src/main/kotlin/com/example/accounting/application/usecases/ReconcileUseCase.kt`
3. `src/main/kotlin/com/example/reporting/application/usecases/GenerateRevenueReportUseCase.kt`
4. `src/main/kotlin/com/example/reporting/application/usecases/GenerateExpenseReportUseCase.kt`
5. `src/main/kotlin/com/example/reporting/application/usecases/GenerateBalanceSheetUseCase.kt`

**Infrastructure** (~6 files):
1. `src/main/kotlin/com/example/accounting/infrastructure/persistence/ReportRepositoryImpl.kt`
2. `src/main/kotlin/com/example/accounting/infrastructure/http/ReportRoutes.kt`
3. `src/main/kotlin/com/example/reporting/infrastructure/queries/RevenueQueries.kt`
4. `src/main/kotlin/com/example/reporting/infrastructure/queries/ExpenseQueries.kt`
5. `src/main/kotlin/com/example/reporting/infrastructure/snapshots/ReportSnapshotRepository.kt`

### Files Modified
1. Event handlers - Add ledger posting logic
2. Use cases - Trigger accounting events
3. `build.gradle.kts` - Reporting dependencies

### Configuration Files
- Database views for common reports
- Scheduled jobs for reconciliation

### Documentation
- `docs/accounting/chart-of-accounts.md` - Account definitions
- `docs/accounting/posting-rules.md` - Business event → ledger mapping
- `docs/accounting/reconciliation-procedures.md` - Reconciliation process
- `docs/accounting/report-definitions.md` - Report specifications

---

## Key Achievements

*This section will be populated during implementation with:*
1. **Immutable Audit Trail** - Complete financial history
2. **Idempotent Posting** - Safe retry of accounting operations
3. **Reproducible Reports** - Reports can be regenerated from ledger
4. **Automated Reconciliation** - Detect discrepancies automatically
5. **Performance Optimized** - Fast queries for large datasets

---

## Compliance Status

### Phase 2 Requirements
| Requirement | Status | Notes |
|-------------|--------|-------|
| Ledger schema | ✅ | Tables created |
| Idempotency constraints | ✅ | Unique external reference |

### Phase 3 Requirements
| Requirement | Status | Notes |
|-------------|--------|-------|
| Ledger API | ✅ | Posting endpoints |

### Phase 4 Requirements
| Requirement | Status | Notes |
|-------------|--------|-------|
| Event handlers | ✅ | React to business events |

### Phase 5 Requirements
| Requirement | Status | Notes |
|-------------|--------|-------|
| Posting rules | Not Started | All business events |
| Idempotent posting | Not Started | Implementation |
| Read models | Not Started | Report queries |
| Snapshots | Not Started | Append-only |
| Reconciliation | Not Started | Automated checks |
| Financial reports | Not Started | All standard reports |
| Audit trail | Not Started | Complete history |

**Overall Compliance**: **0%** (Not Started)

---

## How to Run

### Post to Ledger
```bash
curl -X POST http://localhost:8080/v1/accounting/ledger/post \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -H "Idempotency-Key: rental-123-activation" \
  -H "Content-Type: application/json" \
  -d '{
    "externalReference": "rental-123-activation",
    "entries": [
      {
        "accountCode": "1100",
        "debitAmount": 50000,
        "creditAmount": 0,
        "description": "Rental receivable"
      },
      {
        "accountCode": "4000",
        "debitAmount": 0,
        "creditAmount": 50000,
        "description": "Rental revenue"
      }
    ]
  }'
```

### Generate Revenue Report
```bash
curl "http://localhost:8080/v1/reports/revenue?startDate=2024-01-01&endDate=2024-12-31" \
  -H "Authorization: Bearer $JWT_TOKEN"
```

### Run Reconciliation
```bash
curl -X POST http://localhost:8080/v1/accounting/reconcile \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "reconciliationType": "RENTAL_REVENUE",
    "period": "2024-01"
  }'
```

### Query Ledger
```bash
curl "http://localhost:8080/v1/accounting/ledger?accountCode=1100&startDate=2024-01-01" \
  -H "Authorization: Bearer $JWT_TOKEN"
```

### Expected Behavior
- Ledger postings are idempotent (duplicate posts ignored)
- All postings balance (debits = credits)
- Reports derived from ledger are accurate
- Reconciliation detects discrepancies
- Audit trail shows complete history

---

## Next Steps

### Immediate
- [ ] Define chart of accounts
- [ ] Implement posting rules for all business events
- [ ] Create idempotent posting logic
- [ ] Build report queries and read models
- [ ] Implement reconciliation checks
- [ ] Add financial report endpoints
- [ ] Create audit trail queries
- [ ] Write accounting tests

### Phase 6: Hardening
1. Add structured logging for all financial transactions
2. Implement rate limiting on accounting endpoints
3. Add performance monitoring for report queries
4. Optimize database indexes for ledger queries
5. Add alerting for reconciliation failures

### Future Phases
- **Phase 7**: Deployment with backup and disaster recovery for financial data

---

## References

### Project Documentation
- `fleet-management-plan.md` - Overall project plan
- `phase-4-eventing-kafka-integration.md` - Previous phase
- `phase-6-hardening.md` - Next phase

### Skills Documentation
- `skills/backend-development/SKILL.md` - Backend principles
- `skills/clean-code/SKILL.md` - Coding standards
- `skills/accounting/SKILL.md` - Accounting principles (if exists)

### Accounting Documentation
- `docs/accounting/chart-of-accounts.md` - Account definitions (to be created)
- `docs/accounting/posting-rules.md` - Posting rules (to be created)
- `docs/accounting/reconciliation-procedures.md` - Reconciliation (to be created)
- `docs/accounting/report-definitions.md` - Report specs (to be created)

---

## Summary

**Phase 5 Status**: **Not Started**

This phase will implement production-grade accounting and reporting using immutable ledger entries and derived read models. All financial transactions will be auditable and reproducible.

**Key Deliverables**:
- [ ] Double-entry ledger with posting rules
- [ ] Idempotent posting implementation
- [ ] Financial reports (revenue, expenses, balance sheet)
- [ ] Reconciliation processes
- [ ] Audit trail queries
- [ ] Report snapshots (append-only)
- [ ] Performance optimization for queries
- [ ] Comprehensive accounting tests

**Ready for Phase 6**: Not Yet

Once Phase 5 is complete, the system will have production-grade financial tracking ready for hardening and deployment.

---

**Implementation Date**: TBD  
**Verification**: Pending  
**Accounting Status**: Not Started  
**Compliance**: 0%  
**Ready for Next Phase**: Not Yet
