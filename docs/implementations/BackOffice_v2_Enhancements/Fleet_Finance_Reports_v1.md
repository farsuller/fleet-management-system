# Financial Reporting Requirements: Fleet & Logistics (2026)

> **Status Legend:** ✅ Implemented | ⚠️ Partial | 🔲 Planned
>
> **Scope:** All "Buses & Trucks" references converted to generic **Vehicle** (`VehicleType`: SEDAN, SUV, VAN, TRUCK, BUS, MOTORCYCLE, AMBULANCE, OTHER).

---

## 1. Executive Financial Dashboard

*High-level summary for the Finance Officer to monitor liquidity and profitability.*

### A. Key Performance Indicators (KPIs)

| KPI | Status | Backend | Frontend |
|---|---|---|---|
| **Total Revenue MTD/YTD** | ✅ | `GET /v1/accounting/reports/revenue-kpis` → `RevenueKpisResponse` (dailySum, weeklySum, monthlySum, yearlySum, dailyAvg) | `ReportsScreen → Revenue tab` — 4 `PeriodKpiCard` composables |
| **Revenue Time Series** | ✅ | `GET /v1/accounting/reports/revenue-timeseries?groupBy=daily\|weekly\|monthly` | `ReportsScreen` — `AreaChart` (Charty 3.0) with Daily/Weekly/Monthly/Custom filters |
| **Total Revenue by Account** | ✅ | `GET /v1/accounting/reports/revenue?from=&to=` → `RevenueReportResponse` | `ReportsScreen` — total KPI card + items |
| **Balance Sheet (Assets/Liabilities/Equity)** | ✅ | `GET /v1/accounting/reports/balance-sheet?asOf=` → `BalanceSheetResponse` | `ReportsScreen → Balance Sheet tab` — 3 KPI cards + pie chart + liability breakdown |
| **Net Profit Margin** `(Net Income / Revenue) * 100` | 🔲 | Needs expense aggregation across accounts 5000–5400; no net income calc | No screen |
| **Operating Cash Flow** (liquid assets vs. 30-day liabilities) | ⚠️ | Cash account `1000` balance readable via `/v1/accounting/accounts/1000/balance`; no 30-day liability projection | No dedicated KPI |
| **Burn Rate** (daily avg fuel + maintenance spend) | 🔲 | Accounts `5000` (Maintenance) + `5200` (Fuel) exist in CoA; `MaintenanceJob.totalCost` tracks per-job cost; but no daily-avg aggregation endpoint | No screen |

### B. Visual Analytics (Charts)

| Chart | Status | Notes |
|---|---|---|
| **Revenue Trend (Area Chart)** | ✅ | `AreaChart` in `ReportsScreen`, data from `/revenue-timeseries` |
| **AR Aging Donut** | ⚠️ Partial | AR Aging tab implemented with 5 KPI buckets + table; donut/pie chart variant 🔲 Planned — add `PieChart` to `ArAgingTab` using bucket totals |
| **Asset Allocation Donut** | ✅ | Balance Sheet tab shows asset allocation `PieChart` |
| **Revenue vs. Expense Waterfall** | 🔲 | Requires per-category expense data aggregated against revenue; Charty has no waterfall — custom `Canvas` composable needed |
| **Expense Stacked Bar (Monthly)** | 🔲 | Accounts 5000–5400 exist; need `/reports/expense-breakdown?groupBy=monthly` endpoint + new chart |
| **Vehicle ROI Heatmap** | 🔲 | Requires per-vehicle revenue (invoice→rental→vehicle join) and cost data; no endpoint yet |

---

## 2. Vehicle Asset Reports

*Detailed per-vehicle financial reporting for all fleet types (Sedan, SUV, Van, Truck, Bus, Motorcycle, Ambulance).*

> **VehicleType enum** (backend): `SEDAN | SUV | VAN | TRUCK | BUS | MOTORCYCLE | AMBULANCE | OTHER`
> All unit economics reports filter/group by `vehicleType` or individual `vehicleId`.

### A. Unit Economics

| Feature | Status | Notes |
|---|---|---|
| **Revenue per Vehicle** | ⚠️ Partial | `invoices.rental_id` → `rentals.vehicle_id` join is possible; no aggregated endpoint yet |
| **Cost per Vehicle** (Fuel + Maintenance + Driver Commission + Tax) | 🔲 | `MaintenanceJob.totalCost` tracked per `vehicleId`; no `fuel_logs` table; no commission model |
| **Net Profit per Vehicle** `Revenue − (Fuel + Maintenance + Commission + Tax)` | 🔲 | Depends on fuel log data from driver app |
| **Vehicles by `VehicleType`** | ✅ | `GET /v1/vehicles` returns full list with `vehicleType`; filter by type in frontend |
| **Efficiency: Revenue per KM** | 🔲 | `Vehicle.mileageKm` tracked; `dailyRateAmount` on vehicle; no per-trip km-revenue calc |
| **Idle Time Cost** (lost revenue + fuel during delay) | 🔲 | `VehicleState.AVAILABLE` tracked; no idle duration/cost calc |

**Implementation Path:**
- Add `fuel_logs` table: `(id, vehicle_id FK, driver_id FK, liters INT, amount_php INT, odometer_km INT, logged_at TIMESTAMP)`
- New endpoint: `GET /v1/reports/unit-economics?vehicleId=&vehicleType=&from=&to=`
- Response: `{ plateNumber, vehicleType, totalRevenue, maintenanceCost, fuelCost, commissionCost, taxAmount, netProfit, revenuePerKm }`

### B. Maintenance & Lifecycle Finance

| Feature | Status | Existing Assets | Gap |
|---|---|---|---|
| **Maintenance Cost per Vehicle** | ✅ | `MaintenanceJob` has `laborCost + partsCost = totalCost`, linked to `vehicleId` | UI shows per-job cost; no aggregate per-vehicle total yet |
| **Maintenance Cost Summary** | ⚠️ | `GET /v1/maintenance` returns all jobs with costs | No `/reports/maintenance-summary` endpoint grouping total cost by vehicle |
| **Depreciation Tracker** | ⚠️ | CoA: `5100 Depreciation Expense` (EXPENSE) + `1600 Accumulated Depreciation` (ASSET) seeded | No monthly straight-line calc; no GL posting on schedule |
| **Sinking Fund** (planned overhaul/replacement accrual) | 🔲 | No `sinking_fund_accruals` table | New table + monthly GL posting needed |
| **Parts Inventory Valuation** | 🔲 | No `parts_inventory` table | Parts tracked only as `partsCost` per job; no stock model |

**Depreciation Implementation Path:**
- Add scheduled job (Ktor `cron` or separate service): monthly `Dr 5100 Cr 1600` posting per vehicle
- Depreciation rate stored on `Vehicle` model (new field: `depreciationRateMonthly INT`)
- New endpoint: `GET /v1/reports/depreciation?asOf=` → per-vehicle book value

---

## 3. Philippine Compliance & Tax Module

*Localized for BIR, SEC, and local government mandates (2026 rates).*

### A. Tax Ledger & Forms

| Feature | Status | Notes |
|---|---|---|
| **VAT on Invoices (12%)** | ⚠️ | `invoices.tax INT` column exists; `CreateInvoiceRequest.tax` accepted | No server-side auto-calc; no BIR invoice type flag; no EOPT `invoice_type` |
| **VAT Input/Output Summary** | 🔲 | No endpoint aggregating VAT-able transactions by period for BIR filing |
| **BIR Form 2307 (EWT Certificate)** | 🔲 | No `ewt_transactions` table; no vendor TIN; no ATC code |
| **Withholding Tax Monitor (1%/2%/5%)** | 🔲 | No EWT rate table; no real-time EWT liability tracking |
| **BIR Series Number / OR Number** | 🔲 | `invoice_number` is internal only; no `bir_series` field |

**VAT Implementation Path (Priority):**
1. Migration: add `invoice_type VARCHAR(20) DEFAULT 'VAT_INVOICE'` to `invoices`; add `tin VARCHAR(20)` to `customers`
2. `IssueInvoiceUseCase`: auto-compute `tax = subtotal * 0.12` when `category = RENTAL`
3. New endpoint: `GET /v1/accounting/reports/vat-summary?period=YYYY-MM` → total output VAT for BIR submission

**EWT Implementation Path:**
1. New tables: `ewt_rates (payee_type, rate, bir_atc_code)` + `ewt_transactions (vendor_id, payment_id, gross_amount, ewt_amount, atc_code, period)`
2. Rates: Professional/individual 5% (WC158), Professional/corp 2% (WC158), Rentals 5% (WC010), Suppliers 1% (WC120)
3. Endpoints: `POST /accounting/ewt`, `GET /reports/ewt-2307?vendorId=&period=`

### B. Local Benefit & Subsidy Tracking

| Feature | Status | Notes |
|---|---|---|
| **Statutory Benefit Monitor (SSS / PhilHealth / Pag-IBIG)** | 🔲 | No `employees` table; no contribution tables; no remittance tracking |
| **Government Subsidy Ledger** (DOTr/DSWD fuel subsidies per vehicle) | 🔲 | No `subsidies` table; would map to offset against `5200 Fuel` expense |
| **Local Permit & Franchise Reserve** (LTO / LTFRB / Mayor's Permit) | 🔲 | No `scheduled_outflows` table; no permit renewal calendar per vehicle |

**Benefit Tracking Path:**
- New tables: `employees (driver_id FK, sss_no, philhealth_no, pagibig_no, monthly_salary)` + `gov_contributions (employee_id, period, type, employee_share, employer_share, status)`
- GL accounts needed: `2200–2220` (SSS/PhilHealth/Pag-IBIG Payable), `5500–5520` (corresponding Expenses)
- New endpoint: `GET /payroll/contributions?period=&type=SSS|PHILHEALTH|PAGIBIG`

**Permit Reserve Path:**
- New table: `scheduled_outflows (vehicle_id FK nullable, label, category, amount_php, due_date, is_recurring, recurrence_months)`
- Categories: `LTO_RENEWAL | LTFRB_FRANCHISE | MAYORS_PERMIT | INSURANCE | OTHER`
- Feeds into **Cash Flow Forecast** (Enhancement G in `Enhancement-Reports.md`)

---

## 4. UI/UX Design Reference

### A. Report Layout — Profit & Loss Style *(ref: light-theme screenshot)*

```
┌────────────┐ ┌────────────┐ ┌────────────┐ ┌────────────┐
│ Total      │ │ Total      │ │ Gross      │ │ Net Profit │
│ Revenue    │ │ Expenses   │ │ Profit     │ │ Margin     │
│ ₱52,340    │ │ ₱21,920    │ │ ₱30,420    │ │ 58%        │
└────────────┘ └────────────┘ └────────────┘ └────────────┘
┌────────────────────────────┐ ┌──────────────┐ ┌──────────────┐
│  Profit vs Expenses Chart  │ │ Income Table │ │ Expense Table│
│  Stacked bar — by month    │ │  by account  │ │  by category │
│  Green=profit Orange=exp   │ │  subtotals   │ │  subtotals   │
└────────────────────────────┘ └──────────────┘ └──────────────┘
┌────────────────────────────────────────────────────────────────┐
│  Invoices & Bills table — Company | InvoiceID | Customer |     │
│  Service | Amount | Date | Status (Paid/Pending/Overdue)       │
└────────────────────────────────────────────────────────────────┘
```

**Apply to:** Future `ProfitLossTab` — top 4 KPI cards + split layout (chart left, income/expense tables right) + invoices table below.

### B. Bento Dashboard — Dark Mode *(ref: dark-theme screenshots)*

| Element | Description | Implementation Notes |
|---|---|---|
| **Glassmorphism cards** | Semi-transparent dark cards with subtle border | `Surface(color=Color(0xFF1A1A2E), border=BorderStroke(1.dp, Color.White.copy(alpha=0.1f)))` |
| **Analytics toggle** | `Expenses | Income` chip toggle on area chart | Add `selectedSeries` state to `RevenueTab`; filter `AreaChart` dataset |
| **Total Spending KPI + mini bar** | Large value KPI with week-delta badge | Reuse `PeriodKpiCard` pattern; add `deltaAmount` + `deltaPercent` badges |
| **Recent Transactions list** | Company logo avatar + name + bank + time ago | Map to `payments` / `invoices` last 10 entries; avatar via first-letter `Box` |
| **Income / Expenses side-by-side** | Two KPI tiles with `↑↓` trend arrows | `Row { KpiTile(income) ; KpiTile(expenses) }` |
| **Bento grid layout** | `LazyVerticalGrid(columns = GridCells.Fixed(3))` | Replace current `Column + Row` in `ReportsScreen` with `LazyVerticalGrid` spanning cells |
| **Dark / Light mode toggle** | ✅ Circular reveal animation | Already implemented — `ThemeRevealOverlay`, `CubicBezierEasing(0.22,1,0.36,1)` 650ms |

### C. Payment Method Cards — "My Accounts" *(ref: encircled in dark screenshot)*

Visual model:
```
┌────────────────────────────────┐  ┌────────────────────────────────┐
│  **** 8892           [MC logo] │  │  **** 7712            [VISA]   │
└────────────────────────────────┘  └────────────────────────────────┘
┌────────────────────────────────┐  ┌──────────────┐
│  **** 2241            [VISA]   │  │     + Add    │
│  ₱16,800.00        04/25  ←active│  └──────────────┘
└────────────────────────────────┘
```

**Map to fleet domain:** Customer billing methods — each customer can store 1–N payment methods (card/e-wallet/bank) displayed as clickable tiles.

---

## 5. Payment Method CRUD

*Customer-level payment method management — displayed as "My Accounts" card tiles.*

### A. Backend

> ⚠️ **`payment_methods` table already exists (V012)** — it is the GL mapping table: `GCASH → 1020 GCash Wallet`, `MAYA → 1030 Maya Wallet`, `CASH → 1000`, `BANK_TRANSFER → 1010`, `CREDIT_CARD → 1040`. Do NOT replace or rename it.

**New table** (Flyway migration `Vxxx__add_customer_payment_methods.sql`):
```sql
CREATE TABLE customer_payment_methods (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id         UUID NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
    payment_method_code VARCHAR(20) NOT NULL REFERENCES payment_methods(code),
    -- FK into existing V012 GL mapping: CASH | BANK_TRANSFER | GCASH | MAYA | CREDIT_CARD | DEBIT_CARD
    label               VARCHAR(100),  -- e.g. "GCash 0917-xxx", "BDO Savings"
    last4               VARCHAR(4),    -- nullable for CASH / BANK_TRANSFER
    expiry              VARCHAR(7),    -- MM/YYYY, nullable
    is_default          BOOLEAN NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMP NOT NULL DEFAULT now()
);
```

**GL interconnect:** `customer_payment_methods.payment_method_code → payment_methods.target_account_code → ledger DR account`
- Customer saves "GCash 0917" → `payment_method_code = 'GCASH'`
- At payment time → resolved to `target_account_code = '1020'`
- Ledger: `DR 1020 GCash Wallet / CR 1100 Accounts Receivable`

**Endpoints:**
| Method | Path | Description |
|---|---|---|
| `GET` | `/v1/customers/{id}/payment-methods` | List saved methods for customer |
| `POST` | `/v1/customers/{id}/payment-methods` | Add new (body: `paymentMethodCode`, `label`, `last4`, `expiry`) |
| `PUT` | `/v1/customers/{id}/payment-methods/{pmId}` | Update label / expiry / set default |
| `DELETE` | `/v1/customers/{id}/payment-methods/{pmId}` | Remove |
| `GET` | `/v1/payment-methods` | *(existing)* List all active GL-mapped methods (source for dropdown) |

**Domain files to create:**
- `CustomerPaymentMethod.kt` — data class (`id`, `customerId`, `paymentMethodCode`, `label`, `last4`, `expiry`, `isDefault`)
- `CustomerPaymentMethodRepository.kt` — interface + `ExposedCustomerPaymentMethodRepository`
- `CustomerPaymentMethodUseCases.kt` — `GetCustomerPaymentMethods`, `AddCustomerPaymentMethod`, `UpdateCustomerPaymentMethod`, `DeleteCustomerPaymentMethod`
- Route block in `CustomerRoutes.kt` (nested under `/v1/customers/{id}`)

**Validation rules:**
- `paymentMethodCode` must exist in `payment_methods.code` (FK — DB enforced)
- `last4` must be exactly 4 digits if provided
- Only one `is_default = true` per customer — toggle others to false on write

### B. Frontend (KMP Compose)

**New composable: `PaymentMethodCard`**
```kotlin
// Card tile showing: brand icon (GCASH/MAYA/VISA/MC/CASH) + label + last4 + default badge
@Composable
fun PaymentMethodCard(method: CustomerPaymentMethodDto, isSelected: Boolean, onClick: () -> Unit)
// Active (default): gradient Surface — fleetColors.primary gradient
// Inactive: Surface with BorderStroke(1.dp, fleetColors.outline)
// Brand colour: GCash=blue 0xFF007AFF, Maya=purple 0xFF6C2BD9, Cash=green
```

**New composable: `PaymentMethodsSection`**
```kotlin
// LazyRow of PaymentMethodCard tiles + "+ Add" tile at end
// Right-click / long-press → context menu: Set Default, Delete
// "+ Add" → AddPaymentMethodDialog (ModalBottomSheet)
//   Step 1: pick method type from GET /v1/payment-methods (active GL methods)
//   Step 2: enter label + last4/expiry if card/ewallet
```

**Integration point:** Add to `CustomerDetailScreen` below customer info. Also feeds `FlowsTab` step 2 dropdown (saved methods for the selected customer, grouped by `paymentMethodCode`).

**State:**
- `CustomerPaymentMethodViewModel` — `UiState<List<CustomerPaymentMethodDto>>`
- Uses `GetCustomerPaymentMethodsUseCase`, `AddCustomerPaymentMethodUseCase`, `DeleteCustomerPaymentMethodUseCase`

### C. DTO
```kotlin
@Serializable
data class CustomerPaymentMethodDto(
    val id: String,
    val customerId: String,
    val paymentMethodCode: String,   // "GCASH" | "MAYA" | "CASH" | "BANK_TRANSFER" | "CREDIT_CARD"
    val paymentMethodLabel: String,  // display_name from payment_methods table e.g. "GCash"
    val targetAccountCode: String,   // CoA account code e.g. "1020" — for GL posting display
    val label: String? = null,       // customer-specific e.g. "GCash 0917-xxx"
    val last4: String? = null,
    val expiry: String? = null,
    val isDefault: Boolean
)
```

---

## 6. Implementation Priority (Updated)

| Priority | Feature | Status | Effort |
|---|---|---|---|
| 1 | AR Aging Report | ✅ Done | — |
| 2 | VAT auto-calc + `invoice_type` flag | ⚠️→✅ | Low: 1 migration + `IssueInvoiceUseCase` change |
| 3 | Maintenance cost summary per vehicle | ⚠️→✅ | Low: 1 new query in existing `MaintenanceRepository` |
| 4 | AR Aging Donut chart | ⚠️→✅ | Low: add `PieChart` to existing `ArAgingTab` |
| 5 | **Payment Method CRUD** | 🔲 | Medium: 1 table, 4 endpoints, `PaymentMethodCard` composable |
| 6 | **Profit & Loss tab** (P&L layout ref) | 🔲 | Medium: expense aggregation endpoint + 4-KPI + split chart/table layout |
| 7 | **Bento grid + Glassmorphism** Reports layout | 🔲 | Medium: `LazyVerticalGrid` + dark card styling |
| 8 | EWT Generator + Form 2307 | 🔲 | Medium: 2 tables, 2 endpoints, 1 new tab |
| 9 | Expense Stacked Bar chart | 🔲 | Medium: 1 new endpoint + custom chart composable |
| 10 | SSS/PhilHealth/Pag-IBIG Monitor | 🔲 | High: employees table, contribution engine |
| 11 | Vehicle Unit Economics | 🔲 | High: fuel_logs table, driver app data |
| 12 | LTO/LTFRB Permit Reserve | 🔲 | Medium: scheduled_outflows table |
| 13 | Vehicle ROI Heatmap | 🔲 | High: unit economics prerequisite |

