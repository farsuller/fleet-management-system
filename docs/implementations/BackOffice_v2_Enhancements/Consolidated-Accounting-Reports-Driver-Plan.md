# Consolidated Master Plan: BackOffice v2 Accounting, Reports & Driver App Integration

**Date:** 2026-04-12  
**Module:** `Fleet Management BackOffice` (Web) + `fleet-management` (Backend)  
**Target:** BackOffice v2 Audit & Enhancement + Android Driver App Support  

---

## 1. Executive Summary

This plan consolidates the frontend UI/UX overhaul of the Accounting and Reports modules with the backend infrastructure required to support the upcoming **Android Driver App**. It ensures that financial data collected in the field by drivers flows seamlessly into the BackOffice for management review, reporting, and ledger reconciliation.

---

## 2. Android Driver App Support (Field Operations)

> [!IMPORTANT]
> The following infrastructure is built specifically to enable the **Android Driver App**. These endpoints and workflows allow drivers to act as mobile payment collection agents.

### 2.1 Field Collection Mechanism
*   **Backend Support**: `POST /v1/accounting/payments/driver-collection`
*   **Purpose**: Allows drivers to record payments received from customers during vehicle delivery or return.
*   **Driver App Interaction**: The app will send the invoice ID, collected amount, and payment method (Cash, GCash, etc.).
*   **Status**: These transactions are marked as `PENDING` (collected by driver, not yet remitted to office).

### 2.2 Driver Remittance Workflow
*   **Backend Support**: `POST /v1/accounting/remittances`
*   **Purpose**: Allows drivers to settle their collections. The driver selects a group of pending payments and submits them as a "Remittance" when handing over funds to the office.
*   **BackOffice Verification**: Staff in the portal verify the remittance, which then updates the invoice balance and posts to the General Ledger.

---

## 3. Backend Implementation (Consolidated)

### 3.1 Data Enrichment & API Contracts
*   **Invoice Response Enrichment**: Backend will enrich `InvoiceResponse` with a `rentalSummary` object containing `rentalNumber`, `vehiclePlate`, `vehicleMake`, and `vehicleModel`.
*   **Invoice Categorization**: Add `category` field (`RENTAL`, `MAINTENANCE`, `CUSTOMER_PAYMENT`) to separate revenue streams.
*   **Query Extensions**:
    *   `GET /v1/accounting/invoices?category=...`
    *   `GET /v1/accounting/payments?invoiceId=...` (to show all payments against a specific bill).

### 3.2 SQL Migrations
*   Add `category` to `invoices` table.
*   Add `driver_id` and `collection_type` to `payments` table.
*   Create `driver_remittances` and `driver_remittance_payments` linking tables.

---

## 4. BackOffice Portal (Frontend) Enhancements

### 4.1 Design & Aesthetics (Maintenance-Style)
*   **KPI Section**: Real-time summary of Total Revenue, Outstanding Balances, and Paid Count at the top of the Accounting screen.
*   **FilterChips**: Direct, high-visibility chips for Status and Category filtering.
*   **Adaptive Detail View**:
    *   **Desktop**: Rich `DetailPanel` sliding from the right.
    *   **Mobile/Tablet**: `ModalBottomSheet` sliding from the bottom.
    *   **Content**: Displays Customer Details, Rental/Vehicle Context, and a history of payments (including Driver Collections).

### 4.2 New Reports Screen
A dedicated business intelligence hub:
*   **Financial Summary**: High-level trends and category breakdowns.
*   **Revenue Charts**: Visualizations using the **KMP Canvas API**.
*   **Fiscal Documents**: Balance Sheet and Revenue Reports for export.
*   **Chart of Accounts**: Moved here to decouple reference data from daily transactions.

---

## 5. Implementation Roadmap

### Phase 1: Driver App Backend Foundation (P0)
*   Finalize `driver-collection` and `remittance` persistence.
*   Implement `findByInvoiceId` and `findByCategory` queries.

### Phase 2: Data Enrichment & Categorization (P1)
*   Update `InvoiceDto` and backend mappers to include `rentalSummary`.
*   Run backfill migration for existing invoice categories.

### Phase 3: Adaptive Detail View & Navigation (P1)
*   Implement the `DetailPanel` / `BottomSheet` logic in the BackOffice portal.
*   Upgrade `InvoicesTab` and `PaymentsTab` to the new design standard.

### Phase 4: Payment Methods CRUD & Settings (P1)
*   Implement `SettingsPaymentScreen` to manage system-wide payment options.
*   Add logic to toggle `active/inactive` status for GCash, Maya, and Banks.
*   Ensure these statuses are respected in the `InvoicesTab` and field collection flows.

### Phase 5: Reports Screen & Visualization (P2)
*   Create `ReportsScreen` stub and implement the first 3 tabs (Summary, Revenue, Balance Sheet).
*   Add KMP Canvas bar charts for revenue trends.

---

## 6. Android Apps Integration (Future Initiative)

> [!NOTE]
> While the current focus is BackOffice and Backend, the following integration points are planned for the Android Driver and Customer apps.

### 6.1 Field Collection & Maintenance Mode
*   **Dynamic UI**: The apps will fetch payment methods dynamically. If a method is marked as "Inactive" (e.g., bank under maintenance), it will be automatically hidden in the app.
*   **Centralized Control**: Administrators can disable specific payment channels globally from the BackOffice portal.

### 6.2 Implementation Delta
*   Update Android apps to consume the `isActive` filter on `/v1/accounting/payment-methods`.
*   Implement real-time updates (or fetch-on-open) for payment method availability.

---

## 7. Open Decisions & Confirmations

> [!NOTE]
> The following design decisions are confirmed based on user feedback:
> *   **Efficiency first**: Best pagination approach will be used for large invoice lists.
> *   **Backend enrichment**: The backend will provide the `rentalSummary`.
> *   **Canvas Visualization**: We will use KMP Canvas for bar charts.
> *   **All-Access Reports**: Reports page visible to all back-office staff.
> *   **Deferred Maintenance Costs**: The Cost tab in Reports remains hidden until Phase 5.

**One final confirmation: Is there any additional data (e.g., driver location, photo of receipt) the Android Driver App needs to send during a collection?**
