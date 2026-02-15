# Accounting Module - Sample Payloads

This document details the request and response structures for the Accounting, Account Management, and Reporting APIs. All financial amounts are in whole units (e.g., PHP) represented as integers.

---

## 1. Invoices

### 1.1 Issue New Invoice
**Endpoint**: `POST /v1/accounting/invoices`
**Context**: Creates a new financial invoice for a customer, typically following a rental completion or service activation.
**Permissions**: Finance Owner, Admin

**Request**:
```json
{
  "customerId": "550e8400-e29b-41d4-a716-446655440000",
  "rentalId": "660e8400-e29b-41d4-a716-446655441111",
  "subtotal": 2500,
  "tax": 300,
  "dueDate": "2026-03-01T10:00:00Z"
}
```

**Response (201 Created)**:
```json
{
  "status": "success",
  "data": {
    "id": "5fb69e16-c3bd-4413-8765-7ed881f6e6f3",
    "invoiceNumber": "INV-1739632400000",
    "customerId": "550e8400-e29b-41d4-a716-446655440000",
    "status": "ISSUED",
    "total": 2800,
    "balance": 2800,
    "dueDate": "2026-03-01T10:00:00Z"
  },
  "requestId": "req-1"
}
```

---

## 2. Payments

### 2.1 Pay Invoice
**Endpoint**: `POST /v1/accounting/invoices/{id}/pay`
**Context**: Records a payment against an existing invoice. If the payment covers the full balance, status updates to `PAID`.
**Permissions**: Finance Owner, Admin
**Path Parameter `{id}`**: The unique UUID of the **Invoice** record you are paying.

#### Supported Payment Methods:
*   `CASH` (Default)
*   `CREDIT_CARD`
*   `DEBIT_CARD`
*   *Regional Transfers*: `BANK_TRANSFER`, `GCASH`, `PAYMAYA`, `BPI_TRANSFER`

**Request**:
```json
{
  "amount": 2800,
  "paymentMethod": "GCASH",
  "notes": "Full payment via GCash",
  "transactionReference": "GCASH-998877"
}
```

**Response (200 OK)**:
```json
{
  "status": "success",
  "data": {
    "message": "Payment of 2800 PHP processed successfully.",
    "payment": {
      "id": "766a18de-ff23-4732-9584-bc40f57d87f7",
      "paymentNumber": "PAY-1739632500000",
      "amount": 2800,
      "status": "COMPLETED"
    },
    "invoice": {
      "id": "5fb69e16-c3bd-4413-8765-7ed881f6e6f3",
      "status": "PAID",
      "balance": 0
    }
  },
  "requestId": "req-2"
}
```

---

### 2.2 List All Payments
**Endpoint**: `GET /v1/accounting/payments`
**Context**: Retrieves a comprehensive history of all payments processed in the system.
**Permissions**: Finance Owner, Admin

---

### 2.3 List Customer Payments
**Endpoint**: `GET /v1/accounting/payments/customer/{id}`
**Context**: Retrieves all payments associated with a specific customer profile.
**Permissions**: Finance Owner, Admin, Customer (Own only)
**Path Parameter `{id}`**: The unique UUID of the **Customer** whose history is being requested.

---

### 2.4 Delete Payment
**Endpoint**: `DELETE /v1/accounting/payments/{id}`
**Context**: Permanent removal of a payment record. Use with caution as this does not auto-reverse ledger entries.
**Permissions**: Admin
**Path Parameter `{id}`**: The unique UUID of the **Payment** record to be removed.

**Response (200 OK)**:
```json
{
  "status": "success",
  "data": { "deleted": true },
  "requestId": "req-4"
}
```

---

## 3. Account Management

### 3.1 List All Accounts
**Endpoint**: `GET /v1/accounting/accounts`
**Context**: Retrieves the full Chart of Accounts with current real-time balances.
**Permissions**: Finance Owner, Admin

---

### 3.2 Create Account
**Endpoint**: `POST /v1/accounts`
**Context**: Adds a new account to the Chart of Accounts.
**Permissions**: Admin

**Request**:
```json
{
  "accountCode": "4100",
  "accountName": "Fuel Revenue",
  "accountType": "REVENUE",
  "description": "Revenue from refueling services"
}
```

**Response (201 Created)**:
```json
{
  "status": "success",
  "data": {
    "id": "d0e1f2a3-b4c5-4d6e-8f90-a1b2c3d4e5f6",
    "accountCode": "4100",
    "accountName": "Fuel Revenue",
    "accountType": "REVENUE",
    "isActive": true,
    "description": "Revenue from refueling services",
    "balance": 0
  },
  "requestId": "req-5"
}
```

---

### 3.3 Update Account details
**Endpoint**: `PUT /v1/accounts/{id}`
**Context**: Modifies details of an existing account in the general ledger.
**Permissions**: Admin
**Path Parameter `{id}`**: The unique UUID of the **Account** entity to update.

**Request**:
```json
{
  "accountCode": "4100",
  "accountName": "Refueling Surcharge Revenue",
  "accountType": "REVENUE",
  "description": "Updated description for fuel revenue",
  "isActive": true
}
```

**Response (200 OK)**:
```json
{
  "status": "success",
  "data": {
    "id": "d0e1f2a3-b4c5-4d6e-8f90-a1b2c3d4e5f6",
    "accountCode": "4100",
    "accountName": "Refueling Surcharge Revenue",
    "accountType": "REVENUE",
    "isActive": true,
    "description": "Updated description for fuel revenue",
    "balance": 0
  },
  "requestId": "req-6"
}
```

---

### 3.4 Delete Account
**Endpoint**: `DELETE /v1/accounts/{id}`
**Context**: Removes an account from the system. (Usually restricted if the account has history).
**Permissions**: Admin
**Path Parameter `{id}`**: The unique UUID of the **Account** to be removed.

**Response (200 OK)**:
```json
{
  "status": "success",
  "data": { "deleted": true },
  "requestId": "req-7"
}
```

---

## 4. Financial Reporting

### 4.1 Revenue Report
**Endpoint**: `GET /v1/reports/revenue`
**Context**: Generates a performance report showing revenue generated across all categories for a date range.
**Permissions**: Finance Owner, Admin
**Query Parameters**: `startDate`, `endDate` (ISO-8601 strings, e.g., `2026-01-01T00:00:00Z`)

**Response (200 OK)**:
```json
{
  "status": "success",
  "data": {
    "startDate": "2026-01-01T00:00:00Z",
    "endDate": "2026-02-01T00:00:00Z",
    "totalRevenue": 150000,
    "items": [
      {
        "category": "Rental Revenue",
        "amount": 140000,
        "description": "Standard vehicle rentals"
      },
      {
        "category": "Maintenance Fees",
        "amount": 10000,
        "description": "Service chargebacks"
      }
    ]
  },
  "requestId": "req-8"
}
```

---

### 4.2 Balance Sheet
**Endpoint**: `GET /v1/reports/balance-sheet`
**Context**: Provides a snapshot of the system's financial position (Assets, Liabilities, Equity) as of a specific date.
**Permissions**: Finance Owner, Admin
**Query Parameter**: `asOf` (ISO-8601 string, e.g., `2026-02-15T18:00:00Z`)

**Response (200 OK)**:
```json
{
  "status": "success",
  "data": {
    "asOfDate": "2026-02-15T18:00:00Z",
    "assets": [
      { "code": "1000", "name": "Cash", "balance": 100000 },
      { "code": "1100", "name": "Accounts Receivable", "balance": 25000 }
    ],
    "liabilities": [
      { "code": "2000", "name": "Accounts Payable", "balance": 5000 }
    ],
    "equity": [
      { "code": "3000", "name": "Retained Earnings", "balance": 120000 }
    ],
    "totalAssets": 125000,
    "totalLiabilities": 5000,
    "totalEquity": 120000,
    "isBalanced": true
  },
  "requestId": "req-9"
}
```

---

## 5. Payment Methods

### 5.1 List Payment Methods
**Endpoint**: `GET /v1/accounting/payment-methods`
**Context**: Lists dynamic payment channels and their mapping to target ledger accounts.
**Permissions**: Finance Owner, Admin, Customer (For selection)

---

### 5.2 Create/Update Payment Method
**Endpoints**: `POST /v1/accounting/payment-methods`, `PUT /v1/accounting/payment-methods/{id}`
**Context**: Configures new payment gateways or regional transfer options.
**Permissions**: Admin
**Path Parameter `{id}`**: (For PUT) The unique UUID of the **Payment Method** configuration.

**Request**:
```json
{
  "code": "MAYA",
  "displayName": "Maya",
  "targetAccountCode": "1020",
  "isActive": true
}
```

---

## 6. Reconciliation & Data Integrity

### 6.1 Invoice Reconciliation
**Endpoint**: `GET /v1/reconciliation/invoices`
**Context**: Scans the system for mismatches between the Operational Data (Invoice records) and the Financial Data (Ledger entries).
**Business Scenario**: This detects "Silent Failures" where a payment was successfully recorded in the customer's portal (operational) but failed to post to the General Ledger due to an infrastructure outage or transaction rollback. This ensures that the cash reported by the bank always matches the revenue reported by the accounting module.
**Technical Relationship**: Used in conjunction with `GET /v1/accounting/invoices/{id}` to verify individual history and `GET /v1/accounting/accounts` to check for abnormal balance fluctuations.
**Permissions**: Admin, Finance Owner

**Response (200 OK - No Mismatches)**:
```json
{
  "status": "success",
  "data": [],
  "requestId": "req-10"
}
```

**Response (200 OK - Mismatches Found)**:
> [!NOTE]
> **Business Reason**: A mismatch occurs when the `operationalValue` (what the customer "thinks" they paid based on the Invoice table) does not match the `ledgerValue` (actual financial entries in the General Ledger). This usually indicates a **Partial Failure**: the payment step succeeded, but the accounting "double-entry" step failed or was interrupted.
> 
> **Technical Implementation**: Handled by `ReconciliationService.verifyInvoices()`, which uses `LedgerRepository.calculateSumForPartialReference` to aggregate all ledger lines matching `invoice-{id}-payment%`.

```json
{
  "status": "success",
  "data": [
    {
      "entityId": "5fb69e16-c3bd-4413-8765-7ed881f6e6f3",
      "operationalValue": 2800,
      "ledgerValue": 1400,
      "type": "INVOICE_LEDGER_MISMATCH"
    }
  ],
  "requestId": "req-11"
}
```

---

### 6.2 Accounting Integrity Check
**Endpoint**: `GET /v1/reconciliation/integrity`
**Context**: Verifies the fundamental accounting equation: `Assets = Liabilities + Equity`.
**Permissions**: Admin, Finance Owner

**Response (200 OK)**:
```json
{
  "status": "success",
  "data": {
    "isBalanced": true
  },
  "requestId": "req-12"
}
```
