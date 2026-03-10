# Accounting Gap: Invoice–Customer Relation, Post-Rental Invoice Generation & Customer→Driver Payment

**Date:** 2026-03-10  
**Module:** `fleet-management` — Accounting, Rentals, Drivers  
**Status:** Planning

---

## 1. Current State Summary

| Area | What Exists | What is Missing |
|---|---|---|
| Invoice→Customer | `Invoice.customerId` FK (DB-level RESTRICT) | No customer info in response; no `findByCustomerId` query |
| Invoice→Rental | `Invoice.rentalId` FK (nullable, SET NULL) | No auto-generation on rental completion |
| Rental→Invoice | Nothing | No `invoiceId` on `Rental`; post-completion billing is fully manual |
| Customer→Driver Payment | Nothing | `Driver` entity has zero accounting integration |
| `AccountingService.postRentalActivation()` | Defined | Never called by any use case |
| `rental_payments` / `rental_charges` tables | Schema exists | No application logic writes to them; orphaned |

---

## 2. Gap Analysis

### 2.1 Invoice–Customer Relationship

**Problem:**  
`Invoice` holds `customerId` but the HTTP response (`InvoiceResponse`) only returns the raw UUID. There is no `findByCustomerId` method on `InvoiceRepository`, so fetching all invoices for a given customer requires a full table scan filtered in-memory. Additionally, the back-office and any invoice PDF generation has no customer display info (name, email, contact).

**Root files affected:**
- `domain/repository/InvoiceRepository.kt` — missing query
- `infrastructure/persistence/InvoiceRepositoryImpl.kt` — missing join
- `application/dto/InvoiceResponse.kt` — missing customer snapshot
- `infrastructure/http/AccountingRoutes.kt` — missing GET endpoint for customer invoices

---

### 2.2 Post-Rental Invoice Auto-Generation

**Problem:**  
`CompleteRentalUseCase` marks the rental `COMPLETED` and updates the vehicle odometer but never creates an invoice. The billing-to-close cycle is broken — a staff member must manually call `POST /v1/accounting/invoices` with the correct `rentalId`, `customerId`, and amount. This creates:
- Risk of missing invoices for completed rentals
- Data-entry errors in amounts
- No atomicity — rental can complete without financial record

**Root files affected:**
- `rentals/application/usecases/CompleteRentalUseCase.kt` — needs to call invoice issuance
- `accounts/application/usecases/IssueInvoiceUseCase.kt` — must be injectable/callable internally
- `accounts/domain/model/Accounting.kt` — `Invoice.rentalId` stays nullable but should be set to the rental
- Optionally `rentals/domain/model/Rental.kt` — add `invoiceId` for bidirectional lookup

---

### 2.3 Driver–Payment Integration (Customer Pays Driver)

**Problem:**  
In a fleet-management context, a common scenario is:
- A customer pays a **driver** directly (cash on delivery, field payment)
- The driver then **remits** that collected payment to the company
- The back-office records the remittance and reconciles against the outstanding invoice

Currently `Driver` is completely absent from the financial layer. There is no `driverId` on `Payment`, no driver remittance concept, and no way to track whether a driver has collected and forwarded payment or still holds an outstanding collection.

**Root files affected:**
- `accounts/domain/model/Accounting.kt` — `Payment` needs optional `driverId`
- `drivers/domain/model/Driver.kt` — no changes needed (lookup only)
- `accounts/domain/repository/PaymentRepository.kt` — missing `findByDriverId`
- `infrastructure/persistence/AccountingTables.kt` — `PaymentsTable` needs `driver_id` column
- New use case: `RecordDriverRemittanceUseCase`
- New DTO: `DriverRemittanceRequest` / `DriverRemittanceResponse`
- `accounts/infrastructure/http/AccountingRoutes.kt` — new route group `/v1/accounting/payments/driver/{id}`

---

## 3. Implementation Plan

---

### Phase A — Invoice–Customer Enrichment

#### A1. Add `findByCustomerId` to `InvoiceRepository`

**File:** `src/main/kotlin/com/solodev/fleet/modules/accounts/domain/repository/InvoiceRepository.kt`

```kotlin
// Add to interface:
suspend fun findByCustomerId(customerId: UUID): List<Invoice>
suspend fun findByRentalId(rentalId: UUID): Invoice?
```

#### A2. Implement the new queries in `InvoiceRepositoryImpl`

**File:** `src/main/kotlin/com/solodev/fleet/modules/accounts/infrastructure/persistence/InvoiceRepositoryImpl.kt`

```kotlin
override suspend fun findByCustomerId(customerId: UUID): List<Invoice> =
    dbQuery {
        InvoicesTable
            .selectAll()
            .where { InvoicesTable.customerId eq customerId }
            .map { it.toInvoice() }
    }

override suspend fun findByRentalId(rentalId: UUID): Invoice? =
    dbQuery {
        InvoicesTable
            .selectAll()
            .where { InvoicesTable.rentalId eq rentalId }
            .singleOrNull()
            ?.toInvoice()
    }
```

#### A3. Enrich `InvoiceResponse` with customer snapshot

**File:** `src/main/kotlin/com/solodev/fleet/modules/accounts/application/dto/InvoiceResponse.kt`

Add a lightweight `CustomerSummary` embedded object. This avoids a full customer entity join on every query while giving the front-end enough context to render the invoice.

```kotlin
data class CustomerSummary(
    val id: String,
    val fullName: String,
    val email: String,
    val phoneNumber: String?
)

data class InvoiceResponse(
    val id: String,
    val invoiceNumber: String,
    val customer: CustomerSummary,         // <-- replace raw customerId string
    val rentalId: String?,
    val status: String,
    val subtotal: Int,
    val tax: Int,
    val totalAmount: Int,
    val paidAmount: Int,
    val balance: Int,
    val issueDate: String,
    val dueDate: String,
    val paidDate: String?,
    val notes: String?,
    val lineItems: List<InvoiceLineItemResponse>
)
```

**Required change in `IssueInvoiceUseCase` / mapper:** Join the `CustomersTable` when building `InvoiceResponse` so `CustomerSummary` is populated. Accept `CustomerRepository` as a dependency in `IssueInvoiceUseCase` or create a dedicated `InvoiceQueryService` that performs the join.

#### A4. New HTTP route — invoices by customer

**File:** `src/main/kotlin/com/solodev/fleet/modules/accounts/infrastructure/http/AccountingRoutes.kt`

```
GET /v1/accounting/invoices/customer/{customerId}
```

- Returns paginated list of invoices for the given customer
- Calls `invoiceRepository.findByCustomerId(UUID.fromString(customerId))`
- Responds with `List<InvoiceResponse>`

#### A5. Migration — no schema change needed

`invoices.customer_id` FK already exists at the DB level. This phase is purely application-layer enrichment.

---

### Phase B — Auto-Invoice on Rental Completion

#### B1. Extend `CompleteRentalUseCase` to issue invoice atomically

**File:** `src/main/kotlin/com/solodev/fleet/modules/rentals/application/usecases/CompleteRentalUseCase.kt`

**Strategy:** Inject `IssueInvoiceUseCase` (or a minimal `InvoiceRepository` + `AccountingService`) into `CompleteRentalUseCase` and call it inside the same transaction after the rental is marked `COMPLETED`.

```kotlin
class CompleteRentalUseCase(
    private val rentalRepository: RentalRepository,
    private val vehicleRepository: VehicleRepository,
    private val issueInvoiceUseCase: IssueInvoiceUseCase,   // NEW
    private val invoiceRepository: InvoiceRepository         // NEW — to skip if already exists
) {
    suspend fun execute(rentalId: UUID): Rental {
        // 1. Load + complete rental (existing logic)
        val rental = ...
        val completedRental = rental.complete(...)
        rentalRepository.save(completedRental)
        vehicleRepository.updateOdometer(...)

        // 2. Guard: skip if invoice already issued for this rental
        val existing = invoiceRepository.findByRentalId(rentalId)
        if (existing == null) {
            val invoiceRequest = InvoiceRequest(
                customerId = completedRental.customerId.value.toString(),
                rentalId   = rentalId.toString(),
                dueDate    = Instant.now().plus(30, ChronoUnit.DAYS).toString(),
                lineItems  = listOf(
                    InvoiceLineItemRequest(
                        description = "Vehicle Rental — ${rental.vehicleId.value}",
                        quantity    = completedRental.durationDays(),
                        unitPrice   = completedRental.dailyRateAmount
                    )
                ),
                notes = "Auto-generated on rental completion"
            )
            issueInvoiceUseCase.execute(invoiceRequest)
        }

        return completedRental
    }
}
```

**Key rules:**
- The invoice is issued in the `ISSUED` status immediately (rental is done, billing begins)
- `dueDate` defaults to **30 days** from completion date (configurable)
- Idempotency: if the rental is somehow completed twice, the `findByRentalId` guard prevents duplicate invoices
- The GL posting (`DR: 1100 Accounts Receivable / CR: 4000 Rental Revenue`) happens inside `IssueInvoiceUseCase` as it does today

#### B2. Add `durationDays()` helper to `Rental` domain model

**File:** `src/main/kotlin/com/solodev/fleet/modules/rentals/domain/model/Rental.kt`

```kotlin
fun durationDays(): Int {
    val start = startDate ?: return 1
    val end   = endDate   ?: Instant.now()
    return (Duration.between(start, end).toDays().toInt()).coerceAtLeast(1)
}
```

#### B3. Add `invoiceId` to `Rental` for bidirectional lookup (optional but recommended)

**File:** `src/main/kotlin/com/solodev/fleet/modules/rentals/domain/model/Rental.kt`

```kotlin
val invoiceId: UUID? = null   // set after auto-invoice issuance
```

**Migration:**

```sql
-- Vxxx__add_invoice_id_to_rentals.sql
ALTER TABLE rentals ADD COLUMN invoice_id UUID REFERENCES invoices(id) ON DELETE SET NULL;
CREATE INDEX idx_rentals_invoice_id ON rentals(invoice_id);
```

This enables `GET /v1/rentals/{id}` to return the linked invoice ID without a reverse query.

#### B4. Wire DI / plugin injection

**File:** `src/main/kotlin/com/solodev/fleet/Application.kt` (or wherever use cases are wired)

Update the `CompleteRentalUseCase` construction to pass `IssueInvoiceUseCase` and `InvoiceRepository`.

---

### Phase C — Customer → Driver Payment Flow

#### C1. Conceptual model

In the fleet scenario:
1. Customer owes money on an invoice (rental completed → invoice auto-generated in Phase B)
2. Customer **pays the driver** in cash or mobile wallet when receiving/returning the vehicle in the field
3. The driver **remits** the collected amount to the back-office (daily, weekly, or per-trip)
4. The back-office **records the remittance** which settles the original customer invoice

This means two separate financial events:
- **Driver Collection**: driver collects from customer (not yet in the ledger — it's a field event)
- **Driver Remittance**: driver hands collected funds to company → this clears the invoice and updates the GL

#### C2. Domain model changes

**File:** `src/main/kotlin/com/solodev/fleet/modules/accounts/domain/model/Accounting.kt`

Add `driverId` to `Payment`:

```kotlin
data class Payment(
    val id: UUID,
    val paymentNumber: String,
    val customerId: CustomerId,
    val invoiceId: UUID?,
    val driverId: UUID?,                     // NEW — nullable; set when driver collected payment
    val amount: Int,
    val paymentMethod: String,
    val transactionReference: String?,
    val status: PaymentStatus,
    val paymentDate: Instant,
    val collectionType: PaymentCollectionType = PaymentCollectionType.DIRECT  // NEW
)

enum class PaymentCollectionType {
    DIRECT,           // customer paid company directly (online, bank)
    DRIVER_COLLECTED  // driver collected on behalf of company
}
```

Add a new `DriverRemittance` aggregate:

```kotlin
data class DriverRemittance(
    val id: UUID,
    val remittanceNumber: String,
    val driverId: UUID,
    val remittanceDate: Instant,
    val totalAmount: Int,
    val status: RemittanceStatus,
    val paymentIds: List<UUID>,   // payments cleared by this remittance
    val notes: String?
)

enum class RemittanceStatus { PENDING, SUBMITTED, VERIFIED, DISCREPANCY }
```

#### C3. New use cases

##### `RecordDriverCollectionUseCase`

Triggered when a back-office staff records that a driver collected payment from a customer in the field.

```kotlin
// Input
data class DriverCollectionRequest(
    val driverId: String,
    val customerId: String,
    val invoiceId: String,
    val amount: Int,
    val paymentMethod: String,        // CASH, GCASH, etc.
    val transactionReference: String?,
    val collectedAt: String           // ISO-8601
)

// Behaviour
// 1. Validate invoice exists, belongs to customerId, is ISSUED or OVERDUE
// 2. Validate amount <= invoice.balance
// 3. Create Payment { collectionType = DRIVER_COLLECTED, driverId = ..., status = PENDING }
// 4. Do NOT post GL yet (funds not yet with company)
// 5. Return PaymentResponse with status PENDING
```

Payment stays `PENDING` until remittance is verified.

##### `RecordDriverRemittanceUseCase`

Triggered when the driver physically hands over collected cash/proof-of-transfer to back-office.

```kotlin
// Input
data class DriverRemittanceRequest(
    val driverId: String,
    val paymentIds: List<String>,     // PENDING payments to clear
    val remittanceDate: String,
    val notes: String?
)

// Behaviour
// 1. Load all Payment records by paymentIds; validate all belong to driverId, all PENDING
// 2. Sum amounts
// 3. Create DriverRemittance record (status = SUBMITTED)
// 4. For each Payment:
//    a. Mark status = COMPLETED
//    b. Post GL: DR: 1000 Cash (or 1020 GCash etc.) / CR: 1100 Accounts Receivable
//    c. Update linked Invoice.paidAmount += payment.amount; recalculate status
// 5. Return DriverRemittanceResponse with totals + updated invoices
```

**GL Journal Entry for each remitted payment:**
```
DR 1000 Cash / 1020 GCash / ...   amount
  CR 1100 Accounts Receivable     amount
```
(Same as the existing `PayInvoiceUseCase` flow — reuse `AccountingService.postPaymentCapture()`)

##### `GetDriverOutstandingCollectionsUseCase`

```kotlin
// Returns all PENDING payments collected by a given driver that have not been remitted yet
suspend fun execute(driverId: UUID): List<PaymentResponse>
```

#### C4. Repository changes

**File:** `src/main/kotlin/com/solodev/fleet/modules/accounts/domain/repository/PaymentRepository.kt`

```kotlin
// Add:
suspend fun findByDriverId(driverId: UUID): List<Payment>
suspend fun findPendingByDriverId(driverId: UUID): List<Payment>
suspend fun findByIds(ids: List<UUID>): List<Payment>
```

New `DriverRemittanceRepository`:

```kotlin
interface DriverRemittanceRepository {
    suspend fun save(remittance: DriverRemittance): DriverRemittance
    suspend fun findById(id: UUID): DriverRemittance?
    suspend fun findByDriverId(driverId: UUID): List<DriverRemittance>
    suspend fun findAll(): List<DriverRemittance>
}
```

#### C5. Schema migration

```sql
-- Vxxx__add_driver_payment_fields.sql

-- 1. Add driver_id and collection_type to payments
ALTER TABLE payments
    ADD COLUMN driver_id UUID REFERENCES drivers(id) ON DELETE SET NULL,
    ADD COLUMN collection_type TEXT NOT NULL DEFAULT 'DIRECT'
        CHECK (collection_type IN ('DIRECT', 'DRIVER_COLLECTED'));

CREATE INDEX idx_payments_driver_id ON payments(driver_id);

-- 2. Create driver_remittances table
CREATE TABLE driver_remittances (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    remittance_number TEXT NOT NULL UNIQUE,
    driver_id         UUID NOT NULL REFERENCES drivers(id) ON DELETE RESTRICT,
    remittance_date   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    total_amount      INT NOT NULL CHECK (total_amount > 0),
    status            TEXT NOT NULL DEFAULT 'PENDING'
                          CHECK (status IN ('PENDING', 'SUBMITTED', 'VERIFIED', 'DISCREPANCY')),
    notes             TEXT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_driver_remittances_driver_id ON driver_remittances(driver_id);
CREATE INDEX idx_driver_remittances_status    ON driver_remittances(status);

-- 3. Linking table: remittance → payments (many-to-many, but in practice 1 remittance : many payments)
CREATE TABLE driver_remittance_payments (
    remittance_id UUID NOT NULL REFERENCES driver_remittances(id) ON DELETE CASCADE,
    payment_id    UUID NOT NULL REFERENCES payments(id) ON DELETE RESTRICT,
    PRIMARY KEY (remittance_id, payment_id)
);
```

#### C6. New HTTP routes

Extend `AccountingRoutes.kt`:

```
# Driver collection recording
POST /v1/accounting/payments/driver-collection
  Body: DriverCollectionRequest
  Returns: PaymentResponse (status=PENDING)

# View outstanding (uncollected) driver collections
GET  /v1/accounting/payments/driver/{driverId}/pending
  Returns: List<PaymentResponse>

# Driver remittance submission
POST /v1/accounting/remittances
  Body: DriverRemittanceRequest
  Returns: DriverRemittanceResponse

# View all remittances for a driver
GET  /v1/accounting/remittances/driver/{driverId}
  Returns: List<DriverRemittanceResponse>

# View single remittance detail
GET  /v1/accounting/remittances/{remittanceId}
  Returns: DriverRemittanceResponse
```

---

## 4. Fix: `balance` Column Mismatch

**File:** `src/main/kotlin/com/solodev/fleet/modules/accounts/infrastructure/persistence/AccountingTables.kt`

The `invoices.balance` column is `GENERATED ALWAYS AS (subtotal + tax - paid_amount) STORED` in PostgreSQL. The Kotlin `InvoicesTable` must NOT attempt to write this column.

```kotlin
// BEFORE (incorrect — tries to write a generated column):
val balance = integer("balance")

// AFTER (correct — read the generated value, never write it):
val balance = integer("balance")
    .databaseGenerated()   // Exposed 0.50+ — marks column as DB-generated, excludes from INSERT/UPDATE
```

If using an older version of Exposed that lacks `databaseGenerated()`, remove `balance` from `InvoicesTable` entirely and compute it in the Kotlin mapper:

```kotlin
val balance: Int get() = subtotal + tax - paidAmount
```

---

## 5. Suppress Orphaned `rental_payments` / `rental_charges`

These tables currently exist in SQL and in `RentalsTables.kt` but have no application logic. Two options:

| Option | Action |
|---|---|
| **Keep for future use** | Add a `// TODO` comment in `RentalsTables.kt` explaining these are reserved for per-trip itemized charge tracking; no migration needed |
| **Remove** | Drop the tables via migration; remove `RentalPaymentsTable` / `RentalChargesTable` from `RentalsTables.kt` |

**Recommendation:** Keep them but document their purpose. The `rental_charges` table is a natural place to store itemized rental extras (fuel overage, damage, insurance) that can then flow into line items in the auto-generated invoice (Phase B).

---

## 6. Implementation Order

| Phase | Deliverable | Effort | Priority |
|---|---|---|---|
| **A1–A2** | `findByCustomerId` / `findByRentalId` repository methods | XS | High |
| **A3–A4** | Enriched `InvoiceResponse` + new GET route by customer | S | High |
| **Fix balance column** | Mark `balance` as DB-generated in Exposed | XS | High (correctness) |
| **B1–B3** | Auto-invoice on rental completion | M | Critical |
| **B4** | DI wiring for `CompleteRentalUseCase` | XS | Critical |
| **C2–C3** | `Payment` domain model + new use cases | M | Medium |
| **C4** | Repository additions + `DriverRemittanceRepository` | S | Medium |
| **C5** | Database migration for driver payment fields | S | Medium |
| **C6** | New HTTP routes for driver collection/remittance | S | Medium |

---

## 7. Testing Checklist

| Test Case | Coverage Target |
|---|---|
| Rental completes → invoice auto-generated with correct amount | `CompleteRentalUseCase` integration test |
| Completing the same rental twice → only one invoice created | `CompleteRentalUseCase` idempotency test |
| `GET /v1/accounting/invoices/customer/{id}` → filters correctly | Controller integration test |
| Invoice response includes customer name, email | `IssueInvoiceUseCase` unit test |
| Driver collection → payment created in PENDING state, GL not posted | `RecordDriverCollectionUseCase` unit test |
| Driver remittance → PENDING payments move to COMPLETED, GL posted, invoice settled | `RecordDriverRemittanceUseCase` unit test |
| Overpayment on remittance → rejected | `RecordDriverRemittanceUseCase` validation test |
| `balance` column reads DB value, no write conflict | `InvoiceRepositoryImpl` integration test |

---

## 8. Open Questions

1. **Due date policy**: Should the 30-day default for auto-generated invoices be a configuration value or a per-customer / per-rental-type setting?
2. **Driver collection notification**: Should the system notify the driver (push/SMS) when a collection is recorded against them?
3. **Partial remittance**: Can a driver remit partial amounts, or is each driver-collection payment fully remitted at once?
4. **Discrepancy handling**: When a driver remits an amount that differs from what was recorded (e.g., ₱50 short), should the system auto-create an adjustment entry or flag for manual review?
5. **`rental_charges` future use**: Should the auto-generated invoice pull line items from `rental_charges` if any exist, or always generate a single flat-rate line item?
