# BackOffice v2 Enhancement: Accounting Page & Reports Page

**Date:** 2026-04-12  
**Module:** `Fleet Management BackOffice` (composeApp/webMain) + `fleet-management` backend  
**Status:** In Progress — see §1.1 / §3 / §6 for per-item status  
**Author:** Engineering Review

---

## 1. Codebase Review Summary

### 1.1 What Currently Exists

#### Backend (`fleet-management`)

| Area | Implementation Status |
|---|---|
| `Invoice` domain model | ✅ Full CRUD, `rentalId` nullable FK, `customerId` FK |
| `Payment` domain model | ✅ With `collectionType` (DIRECT/DRIVER_COLLECTED), `driverId`, `status` |
| `DriverRemittance` aggregate | ✅ Full lifecycle: PENDING → SUBMITTED → VERIFIED/DISCREPANCY |
| `Account` / Chart of Accounts | ✅ With `accountType`, balance via ledger query |
| `payment_methods` GL mapping | ✅ **V012 + V034** — `code/display_name/target_account_code`; `status` column (ACTIVE/INACTIVE/DEPRECATED) added in V034 — use `WHERE status='ACTIVE'`, **NOT** `is_active` (deprecated boolean) |
| `invoice_line_items` | ✅ **V005** — `description, quantity, unit_price, total_amount` per invoice; FK `invoice_id`; ⚠️ **NOT yet shown in `InvoiceDetailBottomSheet`** |
| `maintenance_parts` | ✅ **V004** — `part_number, part_name, quantity, unit_cost, total_cost` per job; FK `job_id`; ⚠️ **Foundation for Parts Inventory Valuation — not yet exposed in UI** |
| `maintenance_schedules` | ✅ **V004** — recurring schedule per vehicle (`interval_type`, `next_service_date`, `next_service_odometer_km`); ⚠️ **Not yet used in Reports or KPI cards** |
| `driver_remittances` + `driver_remittance_payments` | ✅ **V024** — full remittance lifecycle PENDING→SUBMITTED→VERIFIED/DISCREPANCY |
| `/v1/accounting/invoices` | ✅ List all, GET by customer, POST create, POST pay |
| `/v1/accounting/payments` | ✅ List all, by customer, by driver (pending+all) |
| `/v1/accounting/remittances` | ✅ POST submit, GET by driver, GET single |
| `/v1/accounting/accounts` | ✅ List, balance lookup |
| `/v1/reports/revenue` | ✅ Revenue report with date range |
| `/v1/reports/balance-sheet` | ✅ Balance sheet as-of date |
| `/v1/reconciliation` | ✅ Invoice integrity + accounting equation checks |
| `GenerateFinancialReportsUseCase` | ✅ `revenueReport()` and `balanceSheet()` |
| Invoice `invoiceType` / category | ✅ **IMPLEMENTED** – `InvoiceCategory` enum (RENTAL/MAINTENANCE/DIRECT/UNKNOWN) on `Invoice`; `?category=` filter on `GET /invoices`; ✅ **V031 DB migration applied** (`invoices.category` column live) |
| `payments.category` column | ✅ **V033 applied** — mirrors `InvoiceCategory` on `payments` table; `idx_payments_category`; ⚠️ **NOT YET exposed in `PaymentsTab` FilterChip** |
| Maintenance cost → Ledger posting | ❌ Not yet implemented (planned in Phase 5 of Maintenance plan) |
| Backend transaction filter by type | ⚠️ PARTIAL – `?category=` on `GET /invoices` ✅; `?invoiceId=` on `GET /payments` ❌ not yet added to route |

#### BackOffice Frontend (`Fleet Management BackOffice`)

| Area | Implementation Status |
|---|---|
| `AccountingScreen` | ✅ KPI header (Total Revenue, Accounts Receivable + more), 6 tabs — still includes Chart of Accounts tab |
| `InvoicesTab` | ✅ PaginatedTable + `AdaptiveDetailPanel` (slide-in) + FilterChip row (All/Rental/Maintenance/Direct) |
| `PaymentsTab` | ⚠️ PARTIAL – row-click opens `PaymentDetailPanelView`; FilterChip for `collectionType` not yet added |
| `FlowsTab` | ✅ Guided stepper (not enhanced yet — payment method still free-text) |
| `DriverPaymentsTab` | ✅ Driver selector sidebar + collection form + history rows |
| `RemittancesTab` | ✅ Checklist select + submit remittance form + history |
| `AccountsTab` | ✅ Chart of Accounts grouped by type with collapsible sections (still in Accounting AND in Reports) |
| `ReportsScreen` | ✅ **IMPLEMENTED** – 3-tab layout: Revenue, Balance Sheet, Chart of Accounts (`PrimaryTabRow`) |
| Transaction type filter (Rental/Maintenance/Direct) | ✅ **IMPLEMENTED** – FilterChip row in `InvoicesTab`; category state + `filterByCategory()` in `InvoicesViewModel` |
| Bottom Sheet on table row click | ⚠️ PARTIAL – `InvoicesTab` now uses `AdaptiveDetailPanel` (right-side panel); full `InvoiceDetailBottomSheet.kt` not yet created |
| Full transaction detail in bottom sheet | ⚠️ PARTIAL – `InvoiceDetailState` (invoice + payments) in `InvoicesViewModel`; `InvoiceDetailBottomSheet.kt` file not yet created |
| Design consistency (Maintenance-style) | ✅ PARTIAL – KPI cards + FilterChips added to `AccountingScreen`/`InvoicesTab`; `PaymentsTab`/`FlowsTab` still not overhauled |
| `ReportsScreen` content (revenue + balance sheet consumer) | ✅ **IMPLEMENTED** – Revenue tab, Balance Sheet tab, Chart of Accounts tab all fully built |

### 1.2 Key Gaps Identified

1. ✅ **RESOLVED** — `InvoiceCategory` enum (RENTAL/MAINTENANCE/DIRECT/UNKNOWN) added to backend domain model + frontend DTOs. FilterChip row in `InvoicesTab` enables server-side filtering via `?category=` query param. ✅ **V031 DB migration already applied.** ⚠️ Note: enum value is `DIRECT` (not `CUSTOMER_PAYMENT`) — verify frontend `SerialName` matches.
2. ✅ **PARTIALLY RESOLVED** — `AccountingScreen` now has KPI header cards; `InvoicesTab` has FilterChip row. `PaymentsTab`/`FlowsTab` still need overhaul.
3. ⚠️ **PARTIALLY RESOLVED** — `InvoicesTab` now uses `AdaptiveDetailPanel` (right-side slide-in) which is better than a dialog; full `InvoiceDetailBottomSheet.kt` not yet created.
4. ⚠️ **PARTIALLY RESOLVED** — Chart of Accounts now available in `ReportsScreen` as tab 3 (delegates to `AccountsTab()`). The ACCOUNTS tab still also exists in `AccountingScreen` — removing it from there is pending.
5. ✅ **RESOLVED** — `ReportsScreen.kt` fully implemented with Revenue, Balance Sheet, and Chart of Accounts tabs consuming `/v1/reports/*` endpoints.
6. ⚠️ **PARTIALLY RESOLVED** — `InvoicesViewModel` now has `InvoiceDetailState` (invoice + linked payments); `loadInvoice(id)` async loader added; but `InvoiceDetailBottomSheet.kt` with the full rich UI layout is not yet created. The `?invoiceId=` backend route is also still missing.
7. ❌ **NOT YET** — `FlowsTab` payment method is still free-text. Enhancement deferred to P8.

---

## 2. Enhancement Plan

---

### 2.1 Transaction Type Classification (Backend)

**Goal:** Allow the front-end to filter transactions by category: Rental invoice, Maintenance payment invoice, Direct customer payment.

#### 2.1.1 Add `InvoiceCategory` enum to domain model

**File:** `src/main/kotlin/com/solodev/fleet/modules/accounts/domain/model/Accounting.kt`

```kotlin
enum class InvoiceCategory {
    RENTAL,            // linked to a completed rental
    MAINTENANCE,       // maintenance cost invoice (Phase 5 of Maintenance plan)
    CUSTOMER_PAYMENT,  // ad-hoc / direct payment invoice
    UNKNOWN
}
```

Add to `Invoice`:

```kotlin
data class Invoice(
    ...
    val category: InvoiceCategory = InvoiceCategory.UNKNOWN,
)
```

#### 2.1.2 Schema migration

**New file:** `src/main/resources/db/migration/Vxxx__add_invoice_category.sql`

```sql
ALTER TABLE invoices
    ADD COLUMN category TEXT NOT NULL DEFAULT 'UNKNOWN'
        CHECK (category IN ('RENTAL', 'MAINTENANCE', 'CUSTOMER_PAYMENT', 'UNKNOWN'));

CREATE INDEX idx_invoices_category ON invoices(category);

-- Backfill: any invoice with a rental_id gets RENTAL
UPDATE invoices SET category = 'RENTAL' WHERE rental_id IS NOT NULL;
```

#### 2.1.3 Expose `category` in `InvoiceResponse` and add query parameter filter

**File:** `src/main/kotlin/com/solodev/fleet/modules/accounts/infrastructure/http/AccountingRoutes.kt`

Extend `GET /v1/accounting/invoices` to accept `?category=RENTAL|MAINTENANCE|CUSTOMER_PAYMENT`:

```kotlin
get("/invoices") {
    val categoryFilter = call.parameters["category"]
        ?.let { runCatching { InvoiceCategory.valueOf(it) }.getOrNull() }
    val invoices = if (categoryFilter != null)
        invoiceRepository.findByCategory(categoryFilter)
    else
        invoiceRepository.findAll()
    // ... existing mapping
}
```

Add `findByCategory` to `InvoiceRepository` interface and `InvoiceRepositoryImpl`.

**Frontend DTO update:**

**File:** `composeApp/src/commonMain/kotlin/org/solodev/fleet/mngt/api/dto/accounting/AccountingDtos.kt`

```kotlin
@Serializable
enum class InvoiceCategory {
    @SerialName("RENTAL") RENTAL,
    @SerialName("MAINTENANCE") MAINTENANCE,
    @SerialName("CUSTOMER_PAYMENT") CUSTOMER_PAYMENT,
    @SerialName("UNKNOWN") UNKNOWN,
}

// Add to InvoiceDto:
val category: InvoiceCategory? = null,
```

---

### 2.2 Accounting Page — UI Design Overhaul

**Goal:** Bring the Accounting page to the same design quality as `MaintenanceListScreen` — KPI cards, FilterChip-based filtering, rich table rows, and a bottom sheet detail panel instead of a dialog.

#### 2.2.1 AccountingScreen header & KPI section

**File:** `composeApp/src/webMain/kotlin/org/solodev/fleet/mngt/features/accounting/AccountingScreen.kt`

Replace the plain `Text("Accounting")` header with:

```
┌──────────────────────────────────────────────────────────────────┐
│ Accounting & Finance                                             │
│ Monitor all invoices, payments, collections, and remittances.    │
│                                              [+ Create Invoice]  │
├──────────────────────────────────────────────────────────────────┤
│  KpiCard: Total Revenue   KpiCard: Outstanding    KpiCard: Paid  │
│  (sum of paid invoices)   (sum of balances >0)   (count PAID)   │
└──────────────────────────────────────────────────────────────────┘
```

- Reuse `KpiCard` / `KpiSegment` / `KpiLegendItem` from `components/common/KpiCard.kt` (already used in Maintenance).
- KPI data is derived client-side from the loaded invoice list (no extra API call required initially).

#### 2.2.2 InvoicesTab — FilterChip row for transaction type

**File:** `composeApp/src/webMain/kotlin/org/solodev/fleet/mngt/features/accounting/InvoicesTab.kt`

Replace the raw header section above the table with:

```
Header: "Invoices"  subtitle: "All customer billing records."
──────────────────────────────────────────────────────────
[All] [Rental] [Maintenance] [Direct Payment]   [Status: All ▾]  [+ Create Invoice]
```

- `FilterChip` row for `InvoiceCategory` — uses the new `category` field.
- Secondary dropdown for `InvoiceStatus` (All / Issued / Paid / Overdue / Cancelled).
- Filtering is server-side for category (query param), client-side for status (fast local filter on loaded data).

**Revised `InvoicesTab` structure:**

```kotlin
@Composable
fun InvoicesTab(router: AppRouter) {
    var categoryFilter by remember { mutableStateOf<InvoiceCategory?>(null) }
    var statusFilter by remember { mutableStateOf<InvoiceStatus?>(null) }
    var selectedInvoice by remember { mutableStateOf<InvoiceDto?>(null) }
    
    // FilterChip row
    InvoiceCategoryFilterRow(selected = categoryFilter, onSelect = { categoryFilter = it })
    InvoiceStatusFilterRow(selected = statusFilter, onSelect = { statusFilter = it })
    
    // PaginatedTable (unchanged)
    
    // Bottom Sheet (replaces Dialog)
    if (selectedInvoice != null) {
        InvoiceDetailBottomSheet(
            invoice = selectedInvoice!!,
            onDismiss = { selectedInvoice = null }
        )
    }
}
```

#### 2.2.3 Replace `InvoiceDetailDialog` with `InvoiceDetailBottomSheet`

This is the key UX enhancement. When a user clicks a row in the invoice table, a `ModalBottomSheet` slides up from the bottom showing a rich, scrollable panel.

**New file:** `composeApp/src/webMain/kotlin/org/solodev/fleet/mngt/features/accounting/InvoiceDetailBottomSheet.kt`

**Layout of the bottom sheet:**

```
┌────────────────────────────────────────────────────────┐ ← drag handle
│  Invoice #INV-1770... · PAID ▓▓ badge                  │
│  ─────────────────────────────────────────────────────  │
│  CUSTOMER DETAILS                                        │
│  👤 John Doe        📧 john@example.com  📞 +63...       │
│  ─────────────────────────────────────────────────────  │
│  INVOICE DETAILS                                         │
│  Subtotal   Tax    Total   Paid    Balance   Due Date    │
│  ─────────────────────────────────────────────────────  │
│  LINKED RENTAL (if rentalId present)                     │  ← async load
│  Rental # · Vehicle plate · Make/Model                   │
│  From [date] → To [date]                                 │
│  ─────────────────────────────────────────────────────  │
│  DRIVER PAYMENTS ON THIS BOOKING                         │  ← from vm
│  Payment # · Amount · Method · Date · Status             │
│  ─────────────────────────────────────────────────────  │
│  ───────── RECORD PAYMENT ─────────────────────────────  │  ← if unpaid
│  [Payment Method ▾]    [Amount (PHP)]   [Confirm]        │
└────────────────────────────────────────────────────────┘
```

**Data sources for the bottom sheet:**

| Section | Data Source | DB Table |
|---|---|---|
| Invoice fields | Already in `InvoiceDto` (populated from backend) | `invoices` (V005) |
| **Invoice line items** | `InvoiceDto.lineItems` or separate `GET /v1/accounting/invoices/{id}/lines` | `invoice_line_items` (V005) — already FK to `invoice_id` |
| Customer details | `InvoiceDto.customer` (CustomerSummaryDto — already embedded) | `customers` |
| Rental details | `GET /v1/rentals/{rentalId}` — lazy-loaded when `rentalId != null` | `rentals` (V003+V025) |
| Vehicles used | Extracted from rental response (`vehicleId`, `vehiclePlate`, `vehicleMake`, `vehicleModel`) | `vehicles` |
| Driver payments on booking | `GET /v1/accounting/payments?invoiceId={id}` — needs new backend query param | `payments` (V005+V024+V033) |
| Payment methods | `GET /v1/payment-methods?status=ACTIVE` — from V012/V034 table | `payment_methods` — filter `status='ACTIVE'` |

**New backend endpoint needed:**

```
GET /v1/accounting/payments?invoiceId={id}
```

In `PaymentRepository`:
```kotlin
suspend fun findByInvoiceId(invoiceId: UUID): List<Payment>
```

In `AccountingRoutes.kt`:
```kotlin
get("/payments") {
    val invoiceId = call.parameters["invoiceId"]
    val payments = if (invoiceId != null)
        paymentRepository.findByInvoiceId(UUID.fromString(invoiceId))
    else
        paymentRepository.findAll()
    call.respond(ApiResponse.success(payments.map { PaymentResponse.fromDomain(it) }, call.requestId))
}
```

#### 2.2.4 PaymentsTab enhancement

**File:** `composeApp/src/webMain/kotlin/org/solodev/fleet/mngt/features/accounting/PaymentsTab.kt`

Current state: uses raw `OutlinedTextField` for date and method filtering.  
Enhancement:
- Replace text fields with `FilterChip` rows for `collectionType` (All / Direct / Driver Collected) — `payments.collection_type` column (V024).
- Add `FilterChip` row for `category` (All / Rental / Maintenance / Direct) — **`payments.category` column already exists in V033** (`idx_payments_category`). Add `?category=` query param to `GET /v1/accounting/payments` (same pattern as invoices).
- Add a `StatusBadge` for payment status on each row.
- Add `onRowClick` handler — clicking a payment row shows a lightweight bottom sheet with the linked invoice details.
- Use the same design header pattern (title + subtitle + KPI summary bar).

---

### 2.3 Reports Page — New Screen

**Goal:** Create a rich `ReportsScreen` that consumes the existing backend `/v1/reports/*` endpoints, moves the Chart of Accounts there, and provides business-oriented visualizations.

**New file:** `composeApp/src/webMain/kotlin/org/solodev/fleet/mngt/features/reports/ReportsScreen.kt`

#### 2.3.1 Reports Page Tab Structure

```kotlin
private enum class ReportsTab(val label: String) {
    FINANCIAL_SUMMARY("Financial Summary"),
    REVENUE("Revenue"),
    BALANCE_SHEET("Balance Sheet"),
    CHART_OF_ACCOUNTS("Chart of Accounts"),
    MAINTENANCE_COSTS("Maintenance Costs"),  // future – ties into Phase 5
    RECONCILIATION("Reconciliation"),
}
```

#### 2.3.2 Financial Summary Tab

This is the business-intelligence landing tab. It consumes the existing `InvoicesViewModel` and `PaymentsViewModel` data locally:

```
┌─────────────────────────────────────────────────────────┐
│ Financial Summary                                        │
│ ─────────────────────────────────────────────────────── │
│  KpiCard                KpiCard              KpiCard     │
│  Total Revenue          Total Outstanding    Paid Today  │
│  ₱ 2,400,000           ₱ 320,000           ₱ 48,000    │
│                                                          │
│  KpiCard                KpiCard                          │
│  Active Invoices        Overdue Invoices                 │
│  (count ISSUED)        (count OVERDUE)                   │
│                                                          │
│ ─────────────────────────────────────────────────────── │
│  Revenue Breakdown (by category)                         │
│  ■ Rental  ■ Maintenance  ■ Direct                      │
│  [Stacked bar or donut-style KpiSegment chart]           │
│                                                          │
│ ─────────────────────────────────────────────────────── │
│  Recent Transactions Table (last 10 invoices)            │
│  Invoice # | Customer | Category | Amount | Status       │
└─────────────────────────────────────────────────────────┘
```

**Implementation:** Derived entirely from the already-loaded invoice list — no extra API call beyond what `InvoicesViewModel` already fetches.

#### 2.3.3 Revenue Report Tab

Consumes `GET /v1/reports/revenue?startDate=&endDate=`.

```
Date Range Picker: [From YYYY-MM-DD] [To YYYY-MM-DD]  [Generate Report]

┌──────────────────────────────────────────────────────────┐
│ Revenue Report: 2026-01-01 to 2026-03-31                 │
│──────────────────────────────────────────────────────────│
│ Total Invoiced: ₱ 4,200,000                              │
│ Total Collected: ₱ 3,800,000                             │
│ Outstanding: ₱ 400,000                                   │
│ Collection Rate: 90.5%                                    │
│──────────────────────────────────────────────────────────│
│ Revenue by Category                                       │
│ ┌───────────────────────────────────────────────────┐   │
│ │  RENTAL      ₱ 3,600,000  ████████████████████░░ │   │
│ │  MAINTENANCE  ₱  300,000   ██░░░░░░░░░░░░░░░░░░░ │   │
│ │  DIRECT       ₱  300,000   ██░░░░░░░░░░░░░░░░░░░ │   │
│ └───────────────────────────────────────────────────┘   │
└──────────────────────────────────────────────────────────┘
```

#### 2.3.4 Balance Sheet Tab

Consumes `GET /v1/reports/balance-sheet?asOf=`.

```
As-Of Date: [Today ▾]  [Run Balance Sheet]

┌──────────────────────────────────────────────────────────┐
│  ASSETS                             LIABILITIES          │
│  Cash & Equivalents   ₱ 1,200,000  Accounts Payable ₱.. │
│  Accounts Receivable  ₱   320,000  Notes Payable    ₱.. │
│  Vehicle Assets       ₱ 8,000,000  Total Liabilities ₱..│
│  Total Assets:        ₱ 9,520,000                        │
│                                     EQUITY               │
│                                     Retained Earnings ₱..│
│                                     Total Equity     ₱..│
│                                                          │
│  Assets = Liabilities + Equity: ✅ BALANCED              │
└──────────────────────────────────────────────────────────┘
```

#### 2.3.5 Chart of Accounts Tab (moved from Accounting)

Move `AccountsTab` content here verbatim. Remove the "Chart of Accounts" tab from `AccountingScreen`.

**Rationale:** Chart of accounts is a reference/reporting view, not a transactional workflow. It belongs in Reports.

**Accounting tab enum update:**

```kotlin
// BEFORE
private enum class AccountingTab(val label: String) {
    INVOICES, PAYMENTS, FLOWS, DRIVER_PAYMENTS, REMITTANCES, ACCOUNTS
}

// AFTER
private enum class AccountingTab(val label: String) {
    INVOICES, PAYMENTS, FLOWS, DRIVER_PAYMENTS, REMITTANCES
}
```

#### 2.3.6 Reconciliation Tab

Consumes `GET /v1/reconciliation/invoices` and `GET /v1/reconciliation/integrity`:

```
┌─────────────────────────────────────────────────────┐
│ Accounting Integrity Check                           │
│ Assets = Liabilities + Equity: ✅ Balanced          │
│─────────────────────────────────────────────────────│
│ Invoice Mismatches (if any)                          │
│ Invoice # | Customer | Expected Balance | Actual   │
│ [empty state: "All invoices are reconciled ✅"]      │
└─────────────────────────────────────────────────────┘
```

---

### 2.4 Invoice Payment Flow Enhancement

**Goal:** Upgrade the `FlowsTab` payment flow and the new `InvoiceDetailBottomSheet` payment section for design consistency, better UX, and real payment method selection.

#### 2.4.1 Payment method dropdown (not free-text)

Currently `FlowsTab` step 2 uses a plain `OutlinedTextField` for "Payment Method".

**Enhancement:**

```kotlin
// Replace OutlinedTextField with ExposedDropdownMenuBox
// Showing: Cash, GCash, Bank Transfer, Credit Card — from paymentMethodRepository
ExposedDropdownMenuBox(expanded = pmExpanded, onExpandedChange = { pmExpanded = it }) {
    OutlinedTextField(
        value = selectedMethod?.name ?: "Select payment method",
        readOnly = true,
        label = { Text("Payment Method") },
        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(pmExpanded) },
        modifier = Modifier.menuAnchor(...).fillMaxWidth()
    )
    ExposedDropdownMenu(expanded = pmExpanded, onDismissRequest = { pmExpanded = false }) {
        paymentMethods.forEach { method ->
            DropdownMenuItem(text = { Text(method.name ?: "") }, onClick = { ... })
        }
    }
}
```

This is the same pattern already in `InvoicesTab.InvoiceDetailDialog` — unify both to the same component.

#### 2.4.2 Payment confirmation receipt card

After a successful payment (step 3 in `FlowsTab`), instead of a plain text "Payment Recorded!", show:

```
┌─────────────────────────────────────────────────────┐
│  ✅ Payment Recorded Successfully                    │
│  ─────────────────────────────────────────────────  │
│  Invoice:  INV-1770...  |  Customer: John Doe       │
│  Amount:   ₱ 16,800     |  Method: GCash            │
│  Paid At:  2026-04-12   |  Balance: ₱ 0 (PAID ✅)  │
│  ─────────────────────────────────────────────────  │
│  [Pay Another Invoice]  [View in Invoices Tab]       │
└─────────────────────────────────────────────────────┘
```

#### 2.4.3 Bottom sheet payment section (InvoiceDetailBottomSheet)

In the new bottom sheet, the "Record Payment" section should:
- Show currently applied payments as a list (driver collections + direct payments already recorded) before prompting for a new payment.
- Clearly show the remaining balance.
- Use the payment method dropdown (not free-text).
- Disable the Confirm button while amount exceeds balance.

---

### 2.5 Maintenance Page — Design & Reporting Review

The `MaintenanceListScreen` is already the most design-mature page in the back office. The following is a targeted review, not a rebuild.

#### 2.5.1 What's working well

- KPI cards with segments and legend (`KpiCard`, `KpiSegment`, `KpiLegendItem`) ✅
- `FilterChip` rows for status and priority ✅
- `PaginatedTable` with 9 columns ✅
- Slide-in `MaintenanceDetailPanel` (animated horizontal slide) ✅
- `AddEditMaintenanceBottomSheet` for create/edit ✅
- "What This Page Is For" purpose explanation section ✅

#### 2.5.2 Improvements for Maintenance Page

1. **No cost KPI card** — Add a "Total Maintenance Cost (Period)" KPI card reporting `labor_cost + parts_cost = total_cost` across visible jobs (`maintenance_jobs.total_cost` — generated column in V004).
2. **Type filter missing in UI** — `typeFilter` state exists in `MaintenanceViewModel` but is not exposed as a `FilterChip` row. Add maintenance type chips: All / Preventive / Corrective / Inspection / Emergency. *(Note: V004 enum is `ROUTINE | REPAIR | INSPECTION | RECALL | EMERGENCY` — align `FilterChip` labels to actual DB values.)*
3. **Row click opens DetailPanel but no bottom sheet** — The panel is a right-side drawer. On narrow viewports this creates layout issues. Consider making the `DetailPanel` also a `ModalBottomSheet` on the web (consistent with the new accounting approach).
4. **`maintenance_parts` table unutilized in UI** — V004 created `maintenance_parts` (`part_number, part_name, quantity, unit_cost, total_cost`, FK `job_id`). Add a "Parts Used" expandable section in `MaintenanceDetailPanel` reading from this table. **This is also the foundation for Parts Inventory Valuation** (Fleet_Finance §2B).
5. **`maintenance_schedules` table unutilized in UI** — V004 created `maintenance_schedules` (`next_service_date, next_service_odometer_km, is_active`). Add an "Upcoming Service" KPI card / alert badge to `MaintenanceListScreen` for vehicles with `next_service_date <= now() + 7 days`.
6. **No maintenance-to-revenue ratio** — Phase 5 of the Maintenance enhancement plan calls for ledger posting on completion (`DR 5000 Maintenance Costs / CR 1100 AR`). Once live, the Reports `MaintenanceCosts` tab can show `total_cost` vs rental revenue per vehicle.

---

### 2.6 New `InvoicesViewModel` State for Bottom Sheet

**File:** `composeApp/src/commonMain/kotlin/org/solodev/fleet/mngt/features/accounting/InvoicesViewModel.kt`

Add state for a detailed invoice view that loads linked rental and driver payments:

```kotlin
private val _selectedInvoiceDetail = MutableStateFlow<InvoiceDetailState?>(null)
val selectedInvoiceDetail: StateFlow<InvoiceDetailState?> = _selectedInvoiceDetail.asStateFlow()

data class InvoiceDetailState(
    val invoice: InvoiceDto,
    val rental: RentalDto? = null,         // null if no rentalId or load failed
    val linkedPayments: List<PaymentDto> = emptyList(),
    val isLoadingDetail: Boolean = false,
)

fun selectInvoice(invoice: InvoiceDto) {
    _selectedInvoiceDetail.value = InvoiceDetailState(invoice, isLoadingDetail = true)
    viewModelScope.launch {
        val rental = if (invoice.rentalId != null) {
            runCatching { rentalRepository.getRental(invoice.rentalId) }.getOrNull()
        } else null
        val payments = runCatching {
            accountingRepository.getPaymentsByInvoiceId(invoice.id ?: "")
        }.getOrDefault(emptyList())
        _selectedInvoiceDetail.value = InvoiceDetailState(
            invoice = invoice,
            rental = rental,
            linkedPayments = payments,
            isLoadingDetail = false
        )
    }
}

fun clearSelectedInvoice() {
    _selectedInvoiceDetail.value = null
}
```

---

### 2.7 New Backend Endpoint: Payments by Invoice ID

**File:** `src/main/kotlin/com/solodev/fleet/modules/accounts/domain/repository/PaymentRepository.kt`

```kotlin
suspend fun findByInvoiceId(invoiceId: UUID): List<Payment>
```

**File:** `src/main/kotlin/com/solodev/fleet/modules/accounts/infrastructure/persistence/PaymentRepositoryImpl.kt`

```kotlin
override suspend fun findByInvoiceId(invoiceId: UUID): List<Payment> = dbQuery {
    PaymentsTable
        .selectAll()
        .where { PaymentsTable.invoiceId eq invoiceId }
        .map { it.toPayment() }
}
```

**File:** `AccountingRoutes.kt` — extend existing `GET /v1/accounting/payments` to accept `?invoiceId=`:

```kotlin
get("/payments") {
    val invoiceIdParam = call.parameters["invoiceId"]
    val payments = when {
        invoiceIdParam != null -> paymentRepository.findByInvoiceId(UUID.fromString(invoiceIdParam))
        else -> paymentRepository.findAll()
    }
    call.respond(ApiResponse.success(payments.map { PaymentResponse.fromDomain(it) }, call.requestId))
}
```

---

### 2.8 Payment Method CRUD

*Source: Fleet_Finance_Reports_v1.md §5. No conflict — new domain, no overlap with existing accounting models.*

**Goal:** Customer-level payment method management. Displayed as card tiles in `CustomerDetailScreen` / `FlowsTab` dropdown. Eliminates free-text payment method input (gap from P8/§2.4.1).

#### 2.8.1 Backend

> ⚠️ **Existing table `payment_methods` (V012) must NOT be renamed or replaced.** It is the GL mapping table:
> `CASH → 1000`, `BANK_TRANSFER → 1010`, `GCASH → 1020`, `MAYA → 1030`, `CREDIT_CARD → 1040`.
> All ledger DR postings resolve `target_account_code` from this table. It is a system-level reference.

**New table** `customer_payment_methods` (Flyway `Vxxx__add_customer_payment_methods.sql`):
```sql
CREATE TABLE customer_payment_methods (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id         UUID NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
    payment_method_code VARCHAR(20) NOT NULL REFERENCES payment_methods(code),
    -- FK → V012 payment_methods(code): CASH | BANK_TRANSFER | GCASH | MAYA | CREDIT_CARD | DEBIT_CARD
    label               VARCHAR(100),  -- e.g. "GCash 0917-xxx-xxxx", "BDO Savings"
    last4               VARCHAR(4),    -- card/account last 4 digits (nullable for CASH)
    expiry              VARCHAR(7),    -- MM/YYYY, nullable
    is_default          BOOLEAN NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_cpm_customer ON customer_payment_methods(customer_id);
```

**GL flow when payment is recorded using a saved customer method:**
```
customer_payment_methods.payment_method_code
    → payment_methods.target_account_code  (e.g. 'GCASH' → '1020')
    → GL posting: DR accounts WHERE account_code = '1020' (GCash Wallet)
                  CR 1100 Accounts Receivable
```
No change to existing `RecordPaymentUseCase` — it already resolves DR account from `payment_methods.target_account_code`. The new customer method just pre-fills `paymentMethodCode` in the payment form.

**New domain files:**
- `CustomerPaymentMethod.kt` — `data class CustomerPaymentMethod(id, customerId, paymentMethodCode, label, last4, expiry, isDefault)`
- `CustomerPaymentMethodRepository.kt` — interface + `ExposedCustomerPaymentMethodRepository`
- `CustomerPaymentMethodUseCases.kt` — `GetCustomerPaymentMethods`, `AddCustomerPaymentMethod`, `UpdateCustomerPaymentMethod`, `DeleteCustomerPaymentMethod`

**Endpoints** (nested under `/v1/customers/{id}`):

| Method | Path | Description |
|---|---|---|
| `GET` | `/v1/customers/{id}/payment-methods` | List saved methods for customer |
| `POST` | `/v1/customers/{id}/payment-methods` | Add new (body: `paymentMethodCode`, `label`, `last4`, `expiry`) |
| `PUT` | `/v1/customers/{id}/payment-methods/{pmId}` | Update label / expiry / `isDefault` |
| `DELETE` | `/v1/customers/{id}/payment-methods/{pmId}` | Remove |
| `GET` | `/v1/payment-methods` | *(existing V012 endpoint)* List all active GL-mapped methods |

**Validation:** `paymentMethodCode` must exist in `payment_methods` table (FK enforced); `last4` = 4 digits if provided; only one `is_default=true` per customer.

#### 2.8.2 Frontend

**New composable `PaymentMethodCard`:**
```kotlin
@Composable
fun PaymentMethodCard(method: PaymentMethodDto, isSelected: Boolean, onClick: () -> Unit)
// Active (default): gradient Surface — fleetColors.primary gradient
// Inactive: Surface border=BorderStroke(1.dp, fleetColors.outline)
// Shows: masked last4, type label (VISA/MC/GCash/Maya), expiry if card
```

**New composable `PaymentMethodsSection`:**
```kotlin
// LazyRow of PaymentMethodCard tiles + "+ Add" tile
// Right-click / long-press → context menu: Set Default, Delete
// "+ Add" → AddPaymentMethodDialog (ModalBottomSheet)
```

**Integration — two-level dropdown in `FlowsTab` step 2:**
1. Top: `ExposedDropdownMenuBox` populated from `GET /v1/payment-methods?status=ACTIVE` (V012+V034 — filter `status='ACTIVE'`, NOT `is_active`) — user picks method type (GCash / Maya / Cash / etc.)
2. If customer has saved methods for that type: secondary `ExposedDropdownMenuBox` shows saved `customer_payment_methods` for that code (e.g. "GCash 0917-xxx", "GCash 0918-xxx")
3. Selecting a saved method pre-fills `label` + `last4`; GL account resolved server-side via `payment_methods.target_account_code`

Resolves P8 gap (§2.4.1).

**DTOs:**
```kotlin
@Serializable
data class CustomerPaymentMethodDto(
    val id: String,
    val customerId: String,
    val paymentMethodCode: String,   // matches payment_methods.code ("GCASH", "MAYA", etc.)
    val paymentMethodLabel: String,  // display_name from payment_methods ("GCash", "Maya")
    val targetAccountCode: String,   // from payment_methods.target_account_code ("1020")
    val label: String? = null,       // customer-specific label e.g. "GCash 0917"
    val last4: String? = null,
    val expiry: String? = null,
    val isDefault: Boolean
)
```

**State:** `CustomerPaymentMethodViewModel(UiState<List<CustomerPaymentMethodDto>>)` + `GetCustomerPaymentMethodsUseCase`, `AddCustomerPaymentMethodUseCase`, `DeleteCustomerPaymentMethodUseCase`.

---

### 2.9 Profit & Loss Tab

*Source: Fleet_Finance_Reports_v1.md §4A layout ref. Extends existing `ReportsScreen` (no conflict — new tab, existing enum).*

**Goal:** Add `PROFIT_LOSS` to `ReportsTab` enum. Requires new backend expense aggregation endpoint.

#### 2.9.1 Layout

```
┌────────────┐ ┌────────────┐ ┌────────────┐ ┌────────────┐
│ Total      │ │ Total      │ │ Gross      │ │ Net Profit │
│ Revenue    │ │ Expenses   │ │ Profit     │ │ Margin     │
│ ₱ X        │ │ ₱ X        │ │ ₱ X        │ │  X%        │
└────────────┘ └────────────┘ └────────────┘ └────────────┘
┌──────────────────────────┐ ┌─────────────┐ ┌──────────────┐
│  P&L BarChart (monthly)  │ │ Income by   │ │ Expense by   │
│  Green=profit Amb=expense│ │ account     │ │ category     │
└──────────────────────────┘ └─────────────┘ └──────────────┘
┌──────────────────────────────────────────────────────────────┐
│  Invoices & Bills table — last 20, sortable by status/date   │
└──────────────────────────────────────────────────────────────┘
```

#### 2.9.2 Backend

New endpoint: `GET /v1/accounting/reports/profit-loss?from=&to=`

Response:
```kotlin
data class ProfitLossResponse(
    val from: LocalDate, val to: LocalDate,
    val totalRevenue: Int,
    val totalExpenses: Int,
    val grossProfit: Int,           // totalRevenue - totalExpenses
    val netProfitMarginPct: Double, // (grossProfit / totalRevenue) * 100
    val expenseByCategory: List<ExpenseCategoryRow>, // account name → total
    val revenueByCategory: List<RevenueCategoryRow>, // InvoiceCategory → total
    val monthlyBars: List<MonthlyPnlBar>             // for BarChart
)
```

Expense aggregation: sum `ledger_entries` WHERE `account_type = EXPENSE` AND `entry_date BETWEEN from AND to`, grouped by `account_id`.

#### 2.9.3 Frontend

- Add `PROFIT_LOSS` to `ReportsTab` enum in `ReportsScreen.kt`
- New composable `ProfitLossTab` — 4 KPI cards + `BarChart` (KMP Canvas) left + income/expense tables right + invoices table below
- Reuse dark glassmorphism card style when `fleetThemeState.isDark` (see §4 design ref)

---

## 3. File Change Summary

### Backend (`fleet-management`)

| File | Change Type | Description | Status |
|---|---|---|---|
| `Accounting.kt` | MODIFY | Add `InvoiceCategory` enum and `category` field to `Invoice` | ✅ DONE |
| `InvoiceResponse.kt` | MODIFY | Add `category` field | ✅ DONE |
| `InvoiceRequest.kt` | MODIFY | Add optional `category` field (default `UNKNOWN`) | ✅ DONE |
| `AccountingTables.kt` | MODIFY | Add `category` varchar column to `InvoicesTable` | ✅ DONE — V031 applied |
| `V031__add_invoice_category.sql` | ~~NEW~~ | DB migration for `category` column | ✅ DONE — file exists, applied |
| `AccountingTables.kt` (payments) | MODIFY | Verify `payments.category` column mapped in `PaymentsTable` Exposed object | ⚠️ CHECK — V033 column live; Kotlin mapping may be absent |
| `InvoiceRepositoryImpl.kt` | MODIFY | Implement `findByCategory` with WHERE clause | ✅ DONE |
| `PaymentRepository.kt` | MODIFY | Add `findByInvoiceId(invoiceId: UUID)` | ✅ DONE |
| `AccountingRoutes.kt` | MODIFY | Add `?category` filter to `GET /invoices`, add `?invoiceId` filter to `GET /payments`, add `?category` filter to `GET /payments` | ⚠️ PARTIAL – invoice `?category` ✅; `?invoiceId` on `/payments` ❌; payment `?category` ❌ |
| `Vxxx__add_invoice_category.sql` | — | (see V031 above — already done) | ✅ |
| `CustomerPaymentMethod.kt` | NEW | Domain model: `CustomerPaymentMethod` data class (FK to `payment_methods.code`) | 🔲 PLANNED |
| `CustomerPaymentMethodRepository.kt` | NEW | Interface + `ExposedCustomerPaymentMethodRepository` | 🔲 PLANNED |
| `CustomerPaymentMethodUseCases.kt` | NEW | Get/Add/Update/Delete use cases | 🔲 PLANNED |
| `CustomerRoutes.kt` | MODIFY | Add `/payment-methods` nested routes | 🔲 PLANNED |
| `Vxxx__add_customer_payment_methods.sql` | NEW | `customer_payment_methods` table migration (FK to existing `payment_methods.code`) | 🔲 PLANNED |
| `AccountingRoutes.kt` (P&L) | MODIFY | Add `GET /v1/accounting/reports/profit-loss` | 🔲 PLANNED |
| `ProfitLossResponse.kt` | NEW | Response DTO with revenue/expense/margin | 🔲 PLANNED |

### Frontend (`Fleet Management BackOffice`)

| File | Change Type | Description | Status |
|---|---|---|---|
| `AccountingDtos.kt` | MODIFY | Add `InvoiceCategory` enum, `category` field to `InvoiceDto` | ✅ DONE |
| `AccountingScreen.kt` | MODIFY | Remove `ACCOUNTS` tab, add KPI section to header, premium header layout | ⚠️ PARTIAL – KPI cards ✅; ACCOUNTS tab still present ❌ |
| `InvoicesTab.kt` | MODIFY | Add FilterChip row for category + `AdaptiveDetailPanel` row click | ✅ DONE (panel, not bottom sheet) |
| `InvoiceDetailBottomSheet.kt` | NEW | Rich bottom sheet: customer, invoice, rental, vehicles, driver payments, pay action | ❌ NOT YET |
| `InvoicesViewModel.kt` | MODIFY | Add `InvoiceDetailState`, `loadInvoice()`, `categoryFilter`, `filterByCategory()` | ✅ DONE |
| `PaymentsTab.kt` | MODIFY | Add FilterChip row for collectionType, status badge on rows, onRowClick handler | ⚠️ PARTIAL – row click + detail panel ✅; FilterChip for collectionType ❌ |
| `FlowsTab.kt` | MODIFY | Replace payment method text field with dropdown, premium receipt card on step 3 | ❌ NOT YET |
| `AccountingRepository.kt` | MODIFY | Add `getPaymentsByInvoiceId(invoiceId: String)` | ✅ DONE |
| `AccountingUseCases.kt` | MODIFY | Add `GetPaymentsByInvoiceIdUseCase` | ✅ DONE |
| `features/accounting/ReportsScreen.kt` | NEW | Reports screen with 3 tabs (Revenue / Balance Sheet / Chart of Accounts) | ✅ DONE |
| `reports/FinancialSummaryTab.kt` | NEW | KPI cards + revenue breakdown + recent transactions (separate file) | ❌ NOT YET (inline in ReportsScreen) |
| `reports/RevenueReportTab.kt` | NEW | Date range picker + bar chart (separate file) | ❌ NOT YET (inline in ReportsScreen) |
| `reports/BalanceSheetTab.kt` | NEW | As-of date picker + balance sheet (separate file) | ❌ NOT YET (inline in ReportsScreen) |
| `reports/ChartOfAccountsTab.kt` | NEW | Moved from `AccountsTab.kt` | ❌ NOT YET (delegates to shared `AccountsTab()`) |
| `reports/ReconciliationTab.kt` | NEW | Invoice mismatch view + accounting equation integrity check | ❌ NOT YET |
| `MaintenanceListScreen.kt` | MODIFY | Add type FilterChip row, add cost KPI card | ❌ NOT YET |
| `PaymentMethodDto.kt` | NEW | `CustomerPaymentMethodDto` with `paymentMethodCode`, `targetAccountCode` fields | 🔲 PLANNED |
| `PaymentMethodCard.kt` | NEW | Card tile composable (active gradient / inactive border) | 🔲 PLANNED |
| `PaymentMethodsSection.kt` | NEW | `LazyRow` tiles + Add tile + context menu | 🔲 PLANNED |
| `CustomerPaymentMethodViewModel.kt` | NEW | `UiState<List<CustomerPaymentMethodDto>>` + CRUD use cases | 🔲 PLANNED |
| `reports/ProfitLossTab.kt` | NEW | 4 KPI cards + BarChart + income/expense tables + invoices table | 🔲 PLANNED |
| `ReportsScreen.kt` | MODIFY | Add `PROFIT_LOSS` to `ReportsTab` enum | 🔲 PLANNED |

---

## 4. UI Design System Alignment

The following design patterns should be applied across Accounting, Reports, and new Payment Method pages.

### 4.1 Established Patterns (from `MaintenanceListScreen`)

| Pattern | Source | Target Pages |
|---|---|---|
| `KpiCard` with icon + value + segments + legend | MaintenanceListScreen | AccountingScreen, ReportsScreen |
| `FilterChip` rows with explanatory subtitle | MaintenanceListScreen | InvoicesTab, PaymentsTab, ReportsScreen |
| Page header: bold title + subtitle + action button | MaintenanceListScreen | AccountingScreen, ReportsScreen |
| `ModalBottomSheet` for row detail | New pattern | InvoicesTab, PaymentsTab |
| `Surface` "What This Page Is For" info card | MaintenanceListScreen | AccountingScreen, ReportsScreen |
| `StatusBadge` / color-coded status | Shared components | PaymentsTab rows, InvoicesTab rows |
| `PaginatedTable` for all lists | All pages | All tabs (already used) |
| Animated slide-in `DetailPanel` for wide viewports | MaintenanceDetailPanel | `InvoiceDetailBottomSheet` → panel on wide, sheet on narrow |

### 4.2 New Patterns (from Fleet_Finance_Reports_v1 UI refs)

| Pattern | Applies To | Implementation |
|---|---|---|
| **P&L 4-KPI top row** (Revenue / Expenses / Gross Profit / Net Margin %) | `ProfitLossTab` | 4 `KpiCard` in `Row` — same pattern as `AccountingScreen` header |
| **Split layout** (chart left 60% + two tables right 40%) | `ProfitLossTab` | `Row { BarChart(Modifier.weight(0.6f)); Column { IncomeTable; ExpenseTable }(0.4f) }` |
| **Invoices & Bills table** below split | `ProfitLossTab` | `PaginatedTable` with Company / InvoiceID / Customer / Service / Amount / Date / Status |
| **Glassmorphism dark card** | All `ReportsScreen` tabs (dark mode only) | `Surface(color=Color(0xFF1A1A2E), border=BorderStroke(1.dp, Color.White.copy(0.1f)))` — gated on `fleetThemeState.isDark` |
| **Analytics series toggle** (`Expenses \| Income` chips) | `RevenueTab` AreaChart | `var selectedSeries by remember`; filter `AreaChart` dataset on selection |
| **Delta badge on KPI** (`+18% ↑`) | `PeriodKpiCard` | Add `deltaPercent: Float?` + `deltaAmount: Int?` params; render amber `↑` / red `↓` chip |
| **Recent Transactions list** (avatar + name + bank + time-ago) | `ReportsScreen` Financial Summary tab | Map last 10 `payments` entries; first-letter avatar `Box`; time-ago via `Duration.between(now, paidAt)` |
| **Payment Method card tiles** | `FlowsTab` (dropdown), `CustomerDetailScreen` | `LazyRow` of `PaymentMethodCard` — active=gradient, inactive=border; `+ Add` tile at end |
| **Bento grid** | `ReportsScreen` (future phase) | `LazyVerticalGrid(GridCells.Fixed(3))` replacing current `Column + Row`; cells span via `GridItemSpan` |

---

## 5. Business Value Per Enhancement

| Enhancement | Business Value |
|---|---|
| **Transaction type filter (Rental/Maintenance/Direct)** | Finance sees revenue by source instantly; aids monthly P&L separation |
| **KPI cards on Accounting screen** | Zero-effort financial health snapshot without running report |
| **InvoiceDetailBottomSheet** | Staff confirms customer + rental + vehicles + driver payments in one view — eliminates cross-tab lookups |
| **Reports page** | Management runs revenue reports and balance sheets without exporting to Excel |
| **Chart of Accounts in Reports** | CFO-level account tree + real balances, separated from transaction workflow |
| **Revenue breakdown by category** | Identifies rental dependency vs diversification; aids pricing decisions |
| **Payment method dropdown (→ CRUD)** | Consistent method list eliminates free-text errors; customer can reuse saved methods |
| **Payment confirmation receipt** | Audit trail: staff always sees what was processed before moving on |
| **Reconciliation tab** | Finance team runs integrity checks without developer; catches accounting equation violations early |
| **Payment Method CRUD** | Customer-level billing method registry; powers FlowsTab dropdown + future invoice auto-charge |
| **Profit & Loss Tab** | Management sees gross profit + net margin in one screen; replaces manual Excel P&L |
| **Bento grid + Glassmorphism** | Premium dashboard feel matching design references; increases perceived product quality |

---

## 6. Implementation Phases

> Ordered by: **finish incomplete first → then new features**. ❌/⚠️ items from original plan take priority over 🔲 new items.

| Phase | Scope | Effort | Priority | Status |
|---|---|---|---|---|
| **P1a** *(finish P1)* | Backend: V031 already applied ✅ — verify `PaymentsTable` Kotlin object maps `payments.category` (V033) | XS | 🔥 NOW | ⚠️ CHECK — V031 done; V033 Kotlin mapping unverified |
| **P1b** *(finish P1)* | Backend: `?invoiceId=` + `?category=` route params on `GET /v1/accounting/payments` | XS | 🔥 NOW | ❌ NOT YET — needed by `InvoiceDetailBottomSheet` + `PaymentsTab` FilterChip |
| **P3** | `InvoiceDetailBottomSheet.kt` — customer + invoice + rental + payments + pay action | M | High | ❌ NOT YET |
| **P6** | Remove `ACCOUNTS` tab from `AccountingScreen`; delegate fully to `ReportsScreen` | XS | Medium | ⚠️ PARTIAL — still in Accounting ❌ |
| **P7** | `PaymentsTab` FilterChip for `collectionType` (All / Direct / Driver Collected) | S | Medium | ⚠️ PARTIAL — row click ✅; FilterChip ❌ |
| **P8** | `FlowsTab`: replace payment method `OutlinedTextField` → `ExposedDropdownMenuBox`; step 3 receipt card | S | Medium | ❌ NOT YET |
| **P9** | `ReportsScreen` Reconciliation tab (`GET /v1/reconciliation/*`) | S | Low | ❌ NOT YET |
| **P10** | `MaintenanceListScreen`: type `FilterChip` row (align to V004 values: ROUTINE/REPAIR/INSPECTION/RECALL/EMERGENCY) + Total Cost KPI card + `maintenance_parts` "Parts Used" panel section + `maintenance_schedules` upcoming alert | M | Low | ❌ NOT YET |
| **P11** *(new)* | Payment Method CRUD — `payment_methods` table, 4 endpoints, `PaymentMethodCard` + `PaymentMethodsSection`, `PaymentMethodViewModel` | M | Medium | 🔲 PLANNED — resolves P8 free-text gap permanently |
| **P12** *(new)* | Profit & Loss Tab — `GET /reports/profit-loss` endpoint, `ProfitLossTab` composable, `PROFIT_LOSS` in `ReportsTab` | M | Medium | 🔲 PLANNED |
| **P13** *(new)* | `PeriodKpiCard` delta badge (`+18% ↑`) + `RevenueTab` series toggle (Expenses\|Income) | S | Low | 🔲 PLANNED |
| **P14** *(new)* | Bento grid layout in `ReportsScreen` — `LazyVerticalGrid` + glassmorphism dark cards | M | Low | 🔲 PLANNED — cosmetic, do last |

**Already done (carried forward):**

| Phase | Scope | Status |
|---|---|---|
| P1 (partial) | `InvoiceCategory` enum, repo, route filter | ✅ DONE |
| P2 | `AccountingDtos`, `InvoicesTab` FilterChip + `AdaptiveDetailPanel` | ✅ DONE |
| P4 | `AccountingScreen` KPI cards + header | ✅ DONE |
| P5 | `ReportsScreen` — Revenue + Balance Sheet + Chart of Accounts + AR Aging tabs | ✅ DONE |

---

## 7. Finance DB Schema Reference

*All migrations verified against `src/main/resources/db/migration/`. Use this as canonical truth when coding.*

### 7.1 Core Finance Tables

| Table | Migration | Key Finance Columns | Status |
|---|---|---|---|
| `accounts` | V005 + V011 | `account_code, account_name, account_type (ASSET/LIABILITY/EQUITY/REVENUE/EXPENSE), parent_account_id, is_active` | ✅ Live |
| `ledger_entries` | V005 | `entry_number, external_reference (idempotency), entry_date, description` | ✅ Live |
| `ledger_entry_lines` | V005 | `entry_id FK, account_id FK, debit_amount INT, credit_amount INT` — constraint: balanced; `validate_ledger_entry_balance()` trigger | ✅ Live |
| `invoices` | V005 + V031 | `invoice_number, customer_id, rental_id, status (DRAFT/ISSUED/PAID/OVERDUE/CANCELLED), subtotal, tax, total_amount (generated), paid_amount, balance (generated), issue_date, due_date, paid_date, **category (RENTAL/MAINTENANCE/DIRECT/UNKNOWN)**` | ✅ Live |
| `invoice_line_items` | V005 | `invoice_id FK, description, quantity, unit_price, total_amount (generated)` | ✅ Live — **not yet shown in `InvoiceDetailBottomSheet`** |
| `payments` | V005 + V024 + V033 | `payment_number, customer_id, invoice_id FK, payment_method VARCHAR, amount, status (PENDING/COMPLETED/FAILED/REFUNDED), payment_date, driver_id FK, collection_type (DIRECT/DRIVER_COLLECTED), **category (RENTAL/MAINTENANCE/DIRECT/UNKNOWN)**` | ✅ Live |
| `payment_methods` | V012 + V034 | `code (CASH/BANK_TRANSFER/BPI_TRANSFER/GCASH/MAYA/CREDIT_CARD/DEBIT_CARD), display_name, target_account_code FK→accounts, **status (ACTIVE/INACTIVE/DEPRECATED)**` | ✅ Live — use `WHERE status='ACTIVE'` |
| `driver_remittances` | V024 | `remittance_number, driver_id FK, total_amount, status (PENDING/SUBMITTED/VERIFIED/DISCREPANCY)` | ✅ Live |
| `driver_remittance_payments` | V024 | Junction: `remittance_id FK, payment_id FK` | ✅ Live |

### 7.2 Maintenance Tables (Finance-Adjacent)

| Table | Migration | Key Finance Columns | Status in Finance UI |
|---|---|---|---|
| `maintenance_jobs` | V004 | `vehicle_id FK, status, job_type (ROUTINE/REPAIR/INSPECTION/RECALL/EMERGENCY), labor_cost INT, parts_cost INT, total_cost (generated=labor+parts), odometer_km` | ⚠️ `total_cost` not in any KPI card yet |
| `maintenance_parts` | V004 | `job_id FK, part_number, part_name, quantity, unit_cost, total_cost (generated=qty×unit)` | ❌ Not shown in any UI panel — **foundation for Parts Inventory Valuation** |
| `maintenance_schedules` | V004 | `vehicle_id FK, schedule_type, interval_type, next_service_date, next_service_odometer_km, is_active` | ❌ Not used in any KPI — **foundation for overdue-service alerts** |
| `rentals.invoice_id` | V025 | FK `invoices(id)` — bidirectional invoice-rental link | ✅ Used in `InvoiceDetailBottomSheet` rental lookup |

### 7.3 Seeded Chart of Accounts

| Code | Name | Type | Used For |
|---|---|---|---|
| 1000 | Cash | ASSET | Cash payments DR |
| 1010 | Bank Account (BPI) | ASSET | Bank transfer DR |
| 1020 | GCash Wallet | ASSET | GCash DR → `payment_methods.code='GCASH'` |
| 1030 | PayMaya Wallet | ASSET | Maya DR → `payment_methods.code='MAYA'` |
| 1040 | Credit Card Clearing | ASSET | Card DR |
| 1100 | Accounts Receivable | ASSET | Invoice issuance CR |
| 1500 | Vehicle Fleet | ASSET | Asset value |
| 1600 | Accumulated Depreciation | ASSET | Monthly depreciation CR (no auto-calc yet) |
| 2000 | Accounts Payable | LIABILITY | Supplier invoices |
| 2100 | Customer Deposits | LIABILITY | Advance payments |
| 3000 | Owner Equity | EQUITY | Capital |
| 3100 | Retained Earnings | EQUITY | Accumulated profit |
| 4000 | Rental Revenue | REVENUE | Completed rental CR |
| 4100 | Late Fees | REVENUE | |
| 4200 | Damage Fees | REVENUE | |
| 5000 | Maintenance Costs | EXPENSE | Job completion DR (Phase 5) |
| 5100 | Depreciation Expense | EXPENSE | Monthly schedule DR (planned) |
| 5200 | Fuel Costs | EXPENSE | Fuel log DR (planned) |
| 5300 | Insurance | EXPENSE | |
| 5400 | Salaries | EXPENSE | |

### 7.4 GL Posting Map (What's Live vs. Planned)

| Trigger | DR | CR | Status |
|---|---|---|---|
| Invoice issued | 1100 AR | 4000 Rental Revenue | ✅ Live (V035/V036 seed entries confirm pattern) |
| Payment recorded (Cash) | 1000 Cash | 1100 AR | ✅ Live via `payment_methods.target_account_code` |
| Payment recorded (GCash) | 1020 GCash Wallet | 1100 AR | ✅ Live |
| Payment recorded (Maya) | 1030 Maya Wallet | 1100 AR | ✅ Live |
| Maintenance job completed | 5000 Maintenance Costs | 1100 AR or 1000 Cash | ❌ Phase 5 — not yet |
| Monthly depreciation | 5100 Depreciation Expense | 1600 Accumulated Depreciation | ❌ No scheduler |
| Customer deposit received | 1000 Cash | 2100 Customer Deposits | ❌ No endpoint |

---

## 8. Open Questions

1. **Invoice pagination**: The current `GET /v1/accounting/invoices` returns all invoices without pagination. Should category filtering be server-side (with pagination) or client-side (current approach) for performance? whatever seems best and most efficient.
2. **Rental detail API**: The bottom sheet needs rental data. Should `InvoicesViewModel` call the rental repository directly, or should the backend enrich the `InvoiceResponse` with a `rentalSummary` embedded object? Yes, the backend should enrich the `InvoiceResponse` with a `rentalSummary` embedded object.
3. **Revenue chart visualization**: The KMP Canvas API is available in Compose. Should we use it for a basic bar chart, or keep the `KpiSegment` inline bar pattern already established in `KpiCard`? Yes, use the KMP Canvas API for a basic bar chart.
4. **Wide viewport behavior**: On wide browser viewports, should the `InvoiceDetailBottomSheet` instead be a `DetailPanel` (right-side slide-in, like `MaintenanceDetailPanel`) for consistency with the desktop experience? Yes, use a DetailPanel for wide viewports.
5. **Reports page permission**: Should the Reports page be visible to all back-office staff or gated to ADMIN/FINANCE roles? Yes, the Reports page should be visible to all back-office staff.
6. **Maintenance cost ledger posting**: The maintenance cost tab in Reports has no data until Phase 5 of the Maintenance enhancement is implemented. Should the tab be hidden/disabled until then, or show a "Coming Soon" placeholder? Yes, the tab should be hidden/disabled until then.
