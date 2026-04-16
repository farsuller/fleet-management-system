# Finance & Accounting Module — BackOffice v2 Enhancements

> **Scope:** Localized for BIR compliance, Philippine e-wallets (GCash, Maya), and local tax laws (VAT, EWT, SSS/PhilHealth/Pag-IBIG).
>
> **Status Legend:**
> - ✅ **Implemented** — code exists in backend + frontend, endpoint live
> - ⚠️ **Partial** — scaffolding exists (DB column, method enum, etc.) but feature logic is incomplete
> - 🔲 **Planned** — described in this document, no code yet

---

## 1. Architecture Foundation — What Is Already Built

The full double-entry accounting system is live. The eight enhancement features in Section 3 are built *on top of* this foundation.

### 1.1 Database Schema (Backend — `fleet-management`)

All tables created by Flyway migrations. Base path: `src/main/resources/db/migration/`

| Table | Migration | Purpose |
|---|---|---|
| `accounts` | `V005` | Chart of Accounts master — `account_code` (UNIQUE), `account_type` (ASSET \| LIABILITY \| EQUITY \| REVENUE \| EXPENSE) |
| `ledger_entries` | `V005` | Immutable double-entry journal header — `entry_number` (UNIQUE), `external_reference` (idempotency key) |
| `ledger_entry_lines` | `V005` | GL debit/credit lines — FK → `ledger_entries`, FK → `accounts`; `debit_amount` / `credit_amount` (PHP centavos not used, whole integers) |
| `invoices` | `V005` | Customer invoices — `status` (DRAFT \| ISSUED \| PAID \| OVERDUE \| CANCELLED), `category` (RENTAL \| MAINTENANCE \| DIRECT), `subtotal`, `tax`, `total_amount` (generated), `balance` (generated) |
| `invoice_line_items` | `V005` | Line-level invoice detail — FK → `invoices` |
| `payments` | `V005` | Payment records — `collection_type` (DIRECT \| DRIVER_COLLECTED), `category` column added in `V033` |
| `payment_methods` | `V012` | Dynamic GL mappings — `code` (CASH, GCASH, MAYA, CREDIT_CARD, …), `target_account_code` → `accounts.account_code`, `status` column added in `V034` |
| `driver_remittances` | `V005` | Driver batch hand-over header — `status` (PENDING \| SUBMITTED \| VERIFIED \| DISCREPANCY) |
| `driver_remittance_payments` | `V005` | Junction table linking remittances ↔ payments |

### 1.2 Chart of Accounts (Seeded — `V011`)

| Range | Type | Key Accounts |
|---|---|---|
| 1000–1049 | ASSET — Cash & Equivalents | 1000 Cash, 1010 Bank / BPI, 1020 GCash Wallet, 1030 Maya Wallet, 1040 Credit Card Clearing |
| 1100 | ASSET — Receivable | 1100 Accounts Receivable |
| 1500–1600 | ASSET — Fixed | 1500 Fleet (Vehicles), 1600 Accumulated Depreciation |
| 2000–2100 | LIABILITY | 2000 Accounts Payable, 2100 Customer Deposits |
| 3000–3100 | EQUITY | 3000 Owner Equity, 3100 Retained Earnings |
| 4000–4200 | REVENUE | 4000 Rental Revenue, 4100 Late Fees, 4200 Damage Fees |
| 5000–5400 | EXPENSE | 5000 Maintenance, 5100 Depreciation, 5200 Fuel, 5300 Insurance, 5400 Salaries |

### 1.3 GL Posting Rules (Live)

| Trigger | Debit | Credit | Use Case |
|---|---|---|---|
| Invoice issued | AR 1100 | Revenue 4000 | `IssueInvoiceUseCase` |
| Payment captured | Asset (method-mapped) | AR 1100 | `PayInvoiceUseCase` — method → `payment_methods.target_account_code` |
| Driver remittance posted | Asset (method-mapped) | AR 1100 (per payment in batch) | `RecordDriverRemittanceUseCase` |

### 1.4 Domain Services (Backend)

| Class | File | Responsibility |
|---|---|---|
| `AccountingService` | `application/AccountingService.kt` | GL posting on rental activation and payment capture |
| `ReconciliationService` | `application/ReconciliationService.kt` | Validates invoice balances == ledger totals; validates accounting equation (Assets = Liabilities + Equity) |
| `GenerateFinancialReportsUseCase` | `application/usecases/` | Revenue report by account, Revenue KPIs (daily/weekly/monthly/yearly), Revenue time-series for charting |

---

## 2. API & Frontend Reference

### 2.1 REST API Endpoints (`/v1/accounting`)

| Method | Path | Request Params | Response | Status |
|---|---|---|---|---|
| `GET` | `/invoices` | `?category=RENTAL\|MAINTENANCE\|DIRECT` | `List<InvoiceResponse>` | ✅ |
| `GET` | `/invoices/{id}` | — | `InvoiceResponse` | ✅ |
| `POST` | `/invoices` | `InvoiceRequest` (customerId, rentalId, subtotal, tax, category, dueDate) | `InvoiceResponse` | ✅ |
| `GET` | `/payments` | — | `List<PaymentResponse>` | ✅ |
| `POST` | `/payments/{invoiceId}/pay` | `PaymentRequest` (amount, paymentMethod, notes) | `PaymentReceiptResponse` | ✅ |
| `POST` | `/payments/driver-collection` | `DriverCollectionRequest` | `PaymentResponse` | ✅ |
| `GET` | `/accounts` | — | `List<AccountResponse>` with balances | ✅ |
| `POST` | `/accounts` | `AccountRequest` | `AccountResponse` | ✅ |
| `GET` | `/reports/balance-sheet` | `?asOf=ISO-8601` | `BalanceSheetResponse` (Assets/Liabilities/Equity, `isBalanced` flag) | ✅ |
| `GET` | `/reports/revenue` | `?from=&to=` (ISO-8601) | `RevenueReportResponse` (total + items by account) | ✅ |
| `GET` | `/reports/revenue-kpis` | — | `RevenueKpisResponse` (dailyAvg, weeklySum, monthlySum, yearlySum) | ✅ |
| `GET` | `/reports/revenue-timeseries` | `?groupBy=daily\|weekly\|monthly&from=&to=` | Array of `{date, amount}` | ✅ |
| `GET` | `/payment-methods` | — | `List<PaymentMethodResponse>` | ✅ |
| `POST` | `/payment-methods` | `PaymentMethodRequest` | `PaymentMethodResponse` | ✅ |
| `PUT` | `/payment-methods/{id}` | `PaymentMethodRequest` | `PaymentMethodResponse` | ✅ |
| `GET` | `/remittances` | — | `List<DriverRemittanceResponse>` | ✅ |
| `POST` | `/remittances` | `DriverRemittanceRequest` (driverId, paymentIds[], remittanceDate, notes) | `DriverRemittanceResponse` | ✅ |

### 2.2 Frontend Screens (BackOffice — `composeApp/src/`)

| Screen / File | Tabs / Sections | ViewModel | Key DTOs Used |
|---|---|---|---|
| `AccountingScreen.kt` | Invoices, Payments, Flows, Driver Payments, Remittances | `AccountingOverviewViewModel` | `InvoiceDto`, `PaymentDto`, `DriverRemittanceDto` |
| `InvoicesTab.kt` | Create Invoice, Pay Invoice, Filter by Category, Paginated Table | `InvoicesViewModel` | `InvoiceDto`, `PaymentMethodDto`, `CreateInvoiceRequest`, `PayInvoiceRequest` |
| `PaymentsTab.kt` | Date range filter, collectionType filter | `PaymentsViewModel` | `PaymentDto` |
| `DriverPaymentsTab.kt` | Driver list + pending collections | `DriverPaymentsViewModel` | `DriverCollectionRequest` |
| `RemittancesTab.kt` | Driver list, payment checkboxes, submit remittance | `RemittancesViewModel` | `DriverRemittanceRequest`, `DriverRemittanceDto` |
| `FlowsTab.kt` | Step-by-step GL flow visualization | — | — |
| `ReportsScreen.kt` | Revenue (AreaChart + KPIs), Balance Sheet, Chart of Accounts | `ReportsViewModel` | `RevenueReportResponse`, `BalanceSheetResponse`, `AccountDto` |
| `SettingsPaymentScreen.kt` | Payment method CRUD, status badge | `SettingsPaymentViewModel` | `PaymentMethodDto`, `CreatePaymentMethodRequest` |

---

## 3. Enhancement Features — Implementation Status

### A. E-Wallet Reconciliation ⚠️ Partial

**Goal:** Dedicated ledger tracking for GCash, Maya, and GrabPay collections with automatic 1%–2% platform fee deduction to reflect net revenue.

**What Exists Now:**

| Component | Location | Notes |
|---|---|---|
| GCash GL account | `V011` — account code `1020` | Asset account "GCash Wallet" |
| Maya GL account | `V011` — account code `1030` | Asset account "Maya Wallet" |
| Payment method records | `V012` — `payment_methods` table | `GCASH` mapped to 1020, `MAYA` to 1030, both ACTIVE |
| Payment method selection | `InvoicesTab.kt`, `DriverPaymentsTab.kt` | User can choose GCash / Maya at payment time |
| E-wallet GL posting | `PayInvoiceUseCase` | Posts `Dr 1020/1030 Cr AR 1100` correctly |

**What Is Missing:**

| Gap | Detail |
|---|---|
| Platform fee deduction | No automatic 1%–2% fee is calculated or posted. Net vs. gross GCash/Maya amounts are identical |
| GrabPay support | No `GRABPAY` payment method seeded; no account in CoA |
| Fee expense account | No dedicated "E-Wallet Transaction Fees" expense account in CoA (would need ~5500) |
| Reconciliation report | No endpoint to compare GCash/Maya GL balances vs. expected net-of-fee amounts |
| Frontend reconciliation tab | No UI diff view for e-wallet settlements |

**Implementation Notes:**
- Add account `5500 E-Wallet Transaction Fees` (EXPENSE) in a new migration
- Add `GRABPAY` payment method + account `1035 GrabPay Wallet`
- On payment capture for e-wallet methods: post an additional `Dr 5500 Cr 1020/1030` for the fee amount
- Fee rate could be stored on `payment_methods` as a new `transaction_fee_rate DECIMAL` column

---

### B. VAT / EOPT Compliance ⚠️ Partial

**Goal:** BIR 2024 EOPT Act — replace "Official Receipts" with "VAT Invoices" for all rental services; flag VAT-registered transactions.

**What Exists Now:**

| Component | Location | Notes |
|---|---|---|
| `tax` field on invoices | `invoices` table (`V005`) | Integer column; currently accepts any value |
| `tax` in `InvoiceRequest` | `InvoiceRequest.kt`, `CreateInvoiceRequest.kt` | Passed through to DB and returned in `InvoiceDto` |
| `InvoiceCategory` enum | `Accounting.kt`, `AccountingDtos.kt` | RENTAL \| MAINTENANCE \| DIRECT — basis for tax type determination |
| Tax display | `InvoicesTab.kt` | Tax amount shown in invoice detail panel |

**What Is Missing:**

| Gap | Detail |
|---|---|
| Automatic 12% VAT calculation | No server-side VAT derivation from subtotal; caller must supply raw `tax` value |
| EOPT invoice type flag | No `invoice_type` column (OR \| VAT_INVOICE \| SUMMARY_LIST) |
| BIR series number | No `bir_series` or `or_number` field; `invoice_number` is internal only |
| VAT-registered customer flag | No `is_vat_registered` or `tin` on `customers` table |
| Summary List of Sales (SLS) report | No endpoint aggregating VAT-able transactions by period |
| 2307 (EWT Certificate) form | Covered separately under Enhancement C |
| Frontend VAT invoice view | Invoice detail does not display a BIR-compliant invoice format |

**Implementation Notes:**
- Add `invoice_type VARCHAR(20) DEFAULT 'VAT_INVOICE'` to `invoices` in a new migration
- Add `tin VARCHAR(20)`, `is_vat_registered BOOLEAN DEFAULT FALSE` to `customers`
- Add server-side 12% VAT computation in `IssueInvoiceUseCase` when `category=RENTAL`
- New endpoint: `GET /reports/vat-summary?period=2026-Q1` → summary list of sales for BIR filing

---

### C. Withholding Tax (EWT) Generator 🔲 Planned

**Goal:** Automatic EWT calculation at 1%, 2%, or 5% based on vendor/payee type; generate BIR Form 2307 (Certificate of Creditable Withholding Tax).

**What Exists Now:** Nothing. No EWT table, no rate logic, no 2307 generation.

**Implementation Plan:**

| Component | Description |
|---|---|
| New DB table `ewt_rates` | `payee_type VARCHAR`, `rate DECIMAL(5,4)`, `bir_atc_code VARCHAR` (e.g., WC158 = 2% professional fees) |
| New DB table `ewt_transactions` | `id`, `vendor_id`, `payment_id`, `gross_amount INT`, `ewt_rate DECIMAL`, `ewt_amount INT`, `atc_code VARCHAR`, `period VARCHAR(7)` (YYYY-MM), `form_2307_generated BOOLEAN` |
| `vendors` table (new) | `id`, `name`, `tin`, `payee_type` (INDIVIDUAL \| CORPORATION \| PROFESSIONAL), `address` — separate from `customers` |
| New endpoint `POST /accounting/ewt` | Record EWT deduction on vendor payment |
| New endpoint `GET /reports/ewt-2307?vendorId=&period=` | Generate Form 2307 data (aggregated by ATC code per quarter) |
| New endpoint `GET /reports/ewt-summary?period=` | Summary of all EWT withheld — for BIR alphalist (SAWT) |
| Frontend: new `EWTTab` in `AccountingScreen` | Vendor list, EWT transactions, 2307 generation button |

**EWT Rate Reference (BIR):**

| Payee Type | Rate | ATC |
|---|---|---|
| Professional fees (individual) | 5% | WC158 |
| Professional fees (corporation) | 2% | WC158 |
| Rentals (real property) | 5% | WC010 |
| Income payments to suppliers | 1% | WC120 |

---

### D. Government Benefit Tracking 🔲 Planned

**Goal:** Dashboard for SSS, PhilHealth, and Pag-IBIG employer contribution tracking; alerts for overpayments or delinquency.

**What Exists Now:** Nothing. No employee/payroll tables beyond `drivers`.

**Implementation Plan:**

| Component | Description |
|---|---|
| New DB table `employees` | `id`, `driver_id FK`, `sss_number`, `philhealth_number`, `pagibig_number`, `monthly_salary INT`, `employment_status` |
| New DB table `gov_contributions` | `id`, `employee_id FK`, `period VARCHAR(7)` (YYYY-MM), `type` (SSS \| PHILHEALTH \| PAGIBIG), `employee_share INT`, `employer_share INT`, `total INT`, `status` (PENDING \| REMITTED), `reference_number VARCHAR` |
| Contribution computation | SSS table-based, PhilHealth 5% (split 50/50), Pag-IBIG 2% (split) — rates effective 2025 |
| New endpoint `GET /payroll/contributions?period=&type=` | List contributions by period and benefit type |
| New endpoint `POST /payroll/contributions/mark-remitted` | Mark batch as remitted with reference number |
| New endpoint `GET /payroll/contributions/alerts` | Returns employees with PENDING contributions past due date |
| GL accounts needed | 2200 SSS Payable, 2210 PhilHealth Payable, 2220 Pag-IBIG Payable (LIABILITY); 5500 SSS Expense, 5510 PhilHealth Expense, 5520 Pag-IBIG Expense (EXPENSE) |
| Frontend: new section in Settings or Reports | Contribution table by month, delinquency alert banner, remittance confirmation |

---

### E. Unit Economics — Profit per Plate 🔲 Planned

**Goal:** Per-vehicle revenue report: `Revenue − (Fuel + Tolls + Maintenance + Driver Commission + Tax)`.

**What Exists Now (Partial Foundation):**

| Component | Location | Notes |
|---|---|---|
| `rentals` table | Existing module | Links vehicle + customer + revenue |
| `invoices` with `rental_id` | `invoices` table | Revenue traceable to rental (and therefore vehicle) |
| `MaintenanceExpense` (inferred) | Maintenance module | Maintenance costs exist but not linked to CoA account 5000 per vehicle |
| `payments` with `category` | `V033` migration | RENTAL \| MAINTENANCE \| DIRECT categories |
| GL account 5200 Fuel | `V011` | Fuel expense exists in CoA but no per-vehicle fuel log |

**What Is Missing:**

| Gap | Detail |
|---|---|
| Per-vehicle fuel log | No `fuel_logs` table (`vehicle_id`, `liters`, `amount`, `date`, `odometer`) |
| Per-vehicle toll log | No `toll_logs` table (see Enhancement H) |
| Driver commission model | No `commission_rates` or per-trip commission records |
| Unit economics report endpoint | No `GET /reports/unit-economics?vehicleId=&from=&to=` |
| Frontend | No per-vehicle P&L breakdown screen |

**Implementation Notes:**
- Depends on Enhancement H (Tollway data) and fuel log tables
- Revenue per vehicle: join `invoices` → `rentals` → `vehicles`
- Costs per vehicle: aggregate `fuel_logs` + `toll_logs` + `maintenance_expenses` + `driver_commissions` where `vehicle_id` matches
- New endpoint: `GET /reports/unit-economics?vehicleId=&from=&to=` → `{ plateNumber, totalRevenue, fuelCost, tollCost, maintenanceCost, commissionCost, taxAmount, netProfit }`

---

### F. Aging Accounts Receivable (AR) ✅ Implemented

**Goal:** Categorize open invoice balances by age: 0–30 days, 31–60 days, 61–90 days, 90+ days. Critical for corporate accounts and 3PL partners.

**What Is Implemented:**

| Component | Location | Notes |
|---|---|---|
| `GenerateArAgingUseCase` | `accounts/application/usecases/` | Queries ISSUED/OVERDUE invoices with `balance > 0`, buckets by days past due |
| `ArAgingResponse` / `ArAgingRow` DTOs | `accounts/application/dto/ArAgingResponse.kt` | 4 buckets + totals |
| `GET /v1/accounting/reports/ar-aging` | `AccountingRoutes.kt` | Query param `?asOf=YYYY-MM-DD` |
| Frontend `ArAgingDto` | `AccountingDtos.kt` | Matches backend response |
| `GetArAgingUseCase` | `AccountingUseCases.kt` | Thin use case over repository |
| `ReportsViewModel.arAging` | `ReportsViewModel.kt` | `UiState<ArAgingDto>`; auto-loads with `asOfDate` |
| `ArAgingTab` composable | `ReportsScreen.kt` | 5 KPI cards + sortable table with colour-coded overdue buckets |

**Bucket Logic:**
- `daysOverdue = max(0, daysBetween(invoice.dueDate, asOf))`
- 0–30: current (not yet overdue) + 1–30 days past due
- 31–60 / 61–90 / 91+: escalating overdue — colour-coded orange → red → purple in UI

---

### G. Cash Flow Forecast (90-Day) 🔲 Planned

**Goal:** 90-day cash projection to anticipate 13th Month Pay obligations, annual insurance premiums, and LTO bulk renewals.

**What Exists Now (Partial Foundation):**

| Component | Location | Notes |
|---|---|---|
| Revenue time-series | `GET /reports/revenue-timeseries` | Historical revenue available with daily/weekly/monthly grouping |
| `invoices.due_date` | `invoices` table | Scheduled inflows from open invoices |
| Maintenance module | Existing module | Scheduled maintenance costs (approximate outflows) |

**What Is Missing:**

| Gap | Detail |
|---|---|
| Recurring obligations table | No `scheduled_outflows` or `recurring_expenses` table (`type`, `amount`, `due_date`, `label`) |
| 13th Month Pay calc | No payroll table to compute 13th month liability |
| Insurance / LTO renewal calendar | No vehicle-level renewal date tracking |
| Projection algorithm | No server-side 90-day forecast logic |
| Confidence intervals | Not implemented |
| Frontend cash flow screen | No projection chart in `ReportsScreen` |

**Implementation Notes:**
- New table `scheduled_outflows`: `id`, `label`, `category` (PAYROLL \| INSURANCE \| LTO \| MAINTENANCE \| OTHER), `amount INT`, `due_date DATE`, `is_recurring BOOL`, `recurrence_months INT`
- Forecast algorithm: `known_inflows (open invoices by due_date) + projected_inflows (avg daily revenue × days) − scheduled_outflows`
- New endpoint: `GET /reports/cash-flow-forecast?from=&to=` → daily net cash position for 90 days
- Frontend: AreaChart in `ReportsScreen` — positive area (inflows) vs. negative area (outflows) overlay

---

### H. Tollway Expenditure Report 🔲 Planned

**Goal:** Compare NLEX vs. SLEX vs. Skyway costs per route to optimize dispatch decisions.

**What Exists Now:** Nothing. No toll or route data.

**Implementation Plan:**

| Component | Description |
|---|---|
| New DB table `toll_logs` | `id`, `vehicle_id FK`, `driver_id FK`, `trip_id FK (nullable)`, `expressway` (NLEX \| SLEX \| SKYWAY \| CAVITEX \| MCX), `toll_amount INT`, `entry_point VARCHAR`, `exit_point VARCHAR`, `transaction_date TIMESTAMP`, `rfid_reference VARCHAR` |
| GL account needed | Account 5600 Toll Fees (EXPENSE) — new account in CoA |
| New endpoint `POST /vehicles/{id}/toll-logs` | Record toll transaction (from driver app or RFID import) |
| New endpoint `GET /reports/toll-expenditure?from=&to=&expressway=` | Aggregated toll costs by expressway, route, vehicle |
| New endpoint `GET /reports/toll-by-route` | Top routes by cost, avg toll per trip |
| Driver app integration | Driver records toll via mobile app; falls under Android driver app scope |
| Frontend: Tollway tab in Reports | Bar chart by expressway, sortable table by vehicle/driver |

**Implementation Dependencies:**
- Depends on Android driver app or RFID feed for data input
- Enhancement E (Unit Economics) references toll data

---

## 4. Backend → Frontend Integration Map

| Enhancement | DB Tables | API Endpoint | Frontend Location | Status |
|---|---|---|---|---|
| Invoice & Payment Core | `invoices`, `invoice_line_items`, `payments`, `ledger_entries`, `ledger_entry_lines` | `GET/POST /invoices`, `POST /payments/{id}/pay` | `InvoicesTab`, `PaymentsTab` | ✅ |
| Payment Methods (GL Mapping) | `payment_methods`, `accounts` | `GET/POST/PUT /payment-methods` | `SettingsPaymentScreen` | ✅ |
| Driver Collection & Remittance | `payments` (DRIVER_COLLECTED), `driver_remittances`, `driver_remittance_payments` | `POST /payments/driver-collection`, `POST /remittances` | `DriverPaymentsTab`, `RemittancesTab` | ✅ |
| Financial Reports (Revenue) | `ledger_entry_lines`, `accounts` | `GET /reports/revenue`, `/revenue-kpis`, `/revenue-timeseries` | `ReportsScreen → Revenue tab` | ✅ |
| Balance Sheet | `accounts`, `ledger_entry_lines` | `GET /reports/balance-sheet` | `ReportsScreen → Balance Sheet tab` | ✅ |
| Chart of Accounts | `accounts` | `GET/POST /accounts` | `ReportsScreen → Chart of Accounts tab` | ✅ |
| E-Wallet Reconciliation | `payment_methods` (partial), `accounts` 1020/1030 | No dedicated endpoint | No dedicated tab | ⚠️ |
| VAT / EOPT | `invoices.tax` (partial) | No BIR-specific endpoint | Invoice detail (tax shown only) | ⚠️ |
| EWT Generator | — | — | — | 🔲 |
| Gov Benefit Tracking | — | — | — | 🔲 |
| Unit Economics | `invoices`, `rentals` (partial) | — | — | 🔲 |
| AR Aging | `invoices` (reuses existing) | `GET /v1/accounting/reports/ar-aging?asOf=` | `ReportsScreen → AR Aging tab` | ✅ |
| Cash Flow Forecast | `invoices` (partial) | — | — | 🔲 |
| Tollway Expenditure | — | — | — | 🔲 |

---

## 5. Implementation Roadmap

Ordered by implementation complexity and data availability (features that reuse existing DB data first).

| Priority | Enhancement | Complexity | Dependency | Effort Estimate |
|---|---|---|---|---|
| 1 | **F. AR Aging Report** | Low | None — reuses `invoices` table | ✅ Implemented |
| 2 | **A. E-Wallet Reconciliation** | Low–Medium | None — accounts 1020/1030 already exist | 1 migration (fee rate column), fee posting logic, 1 new report endpoint |
| 3 | **B. VAT / EOPT Compliance** | Medium | Requires `customers.tin` migration | 1–2 migrations, server-side VAT calc, invoice format UI update |
| 4 | **C. EWT Generator** | Medium | Vendors table (new), `ewt_rates` table | 2 migrations, 2 new endpoints, 1 new UI tab |
| 5 | **E. Unit Economics** | Medium–High | Depends on fuel log tables; partially depends on H (tolls) | 2 migrations, 1 complex JOIN query endpoint, 1 new UI screen |
| 6 | **G. Cash Flow Forecast** | High | `scheduled_outflows` table; better with payroll module | 1 migration, forecast algorithm, 1 new chart screen |
| 7 | **D. Gov Benefit Tracking** | High | `employees` table, contribution rate tables | 2-3 migrations, contribution engine, 1 new section |
| 8 | **H. Tollway Expenditure** | High | Android driver app or RFID feed for data input | 1 migration, 2 endpoints, 1 chart tab; blocked on data source |

---

## 6. Known Constraints

| Constraint | Detail |
|---|---|
| PHP currency only | All amounts stored as whole integers (no centavos / decimal). Tax and fee calculations must round to nearest peso |
| No payroll module | Enhancements C, D, and G partially depend on payroll/employee data that does not exist yet |
| Android driver app required | Enhancement H (tolls) and E (fuel) data entry depends on the driver mobile app integration |
| BIR TIN required | Enhancements B and C require TIN on customers/vendors — needs frontend collection forms and validation |
| Auth/RBAC | All new endpoints must follow existing JWT + RBAC patterns; BIR reports should be ADMIN-only role |