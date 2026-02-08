# Accounting Module - Sample Payloads

This document details the request and response structures for the Accounting API, specifically for invoicing and payment processing.

---

## 1. Invoices

### 1.1 Issue Invoice
**Endpoint**: `POST /v1/accounting/invoices`
**Context**: Creates a new invoice for a customer, optionally linked to a specific rental.
**Permissions**: Finance Owner, Admin

**Request**:
```json
{
  "customerId": "550e8400-e29b-41d4-a716-446655440000",
  "rentalId": "660e8400-e29b-41d4-a716-446655441111",
  "subtotalCents": 150000,
  "taxCents": 18000,
  "dueDate": "2026-03-01T10:00:00Z"
}
```

**Response (201 Created)**:
```json
{
  "status": "success",
  "data": {
    "id": "5fb69e16-c3bd-4413-8765-7ed881f6e6f3",
    "invoiceNumber": "INV-1739020417000",
    "customerId": "550e8400-e29b-41d4-a716-446655440000",
    "status": "ISSUED",
    "totalAmount": 1680.00,
    "balanceAmount": 1680.00,
    "dueDate": "2026-03-01T10:00:00Z"
  },
  "requestId": "req-12345"
}
```

**cURL Example**:
```bash
curl -X POST http://localhost:8080/v1/accounting/invoices \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "550e8400-e29b-41d4-a716-446655440000",
    "subtotalCents": 50000,
    "taxCents": 6000,
    "dueDate": "2026-03-15T00:00:00Z"
  }'
```

---

## 2. Payments

### 2.1 Pay Invoice
**Endpoint**: `POST /v1/accounting/invoices/{id}/pay`
**Context**: Records a payment against an existing invoice. If the payment covers the full balance, status updates to `PAID`.
**Permissions**: Finance Owner, Admin

#### Supported Payment Methods:
*   `CASH` (Default)
*   `CREDIT_CARD`
*   `DEBIT_CARD`
*   *Regional Transfers*: `BANK_TRANSFER`, `GCASH`, `PAYMAYA`, `BPI_TRANSFER`

**Request (Default/Cash)**:
```json
{
  "amountCents": 50000,
  "paymentMethod": "CASH",
  "notes": "Partial payment via front-desk"
}
```

**Request (Credit Card)**:
```json
{
  "amountCents": 168000,
  "paymentMethod": "CREDIT_CARD",
  "notes": "Full settlement - VISA *1234",
  "transactionReference": "tx_abc123"
}
```

**Request (E-Wallet/GCash)**:
```json
{
  "amountCents": 100000,
  "paymentMethod": "GCASH",
  "notes": "Reservation fee deposit",
  "transactionReference": "REF-998877"
}
```

**Response (200 OK)**:
```json
{
  "success": true,
  "data": {
    "message": "Payment of 500.0 PHP processed successfully.",
    "receipt": {
      "id": "766a18de-ff23-4732-9584-bc40f57d87f7",
      "paymentNumber": "PAY-1739021673000",
      "amount": 500.0,
      "paymentMethod": "CASH",
      "status": "COMPLETED",
      "paymentDate": "2026-02-08T21:34:00Z",
      "notes": "Partial payment via front-desk"
    },
    "invoice": {
      "id": "5fb69e16-c3bd-4413-8765-7ed881f6e6f3",
      "invoiceNumber": "INV-1739020417000",
      "status": "ISSUED",
      "totalAmount": 1680.0,
      "balanceAmount": 1180.0,
      "dueDate": "2026-03-01T10:00:00Z"
    }
  },
  "requestId": "req-12346"
}
```

**cURL Example**:
```bash
curl -X POST http://localhost:8080/v1/accounting/invoices/5fb69e16-c3bd-4413-8765-7ed881f6e6f3/pay \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "amountCents": 168000,
    "paymentMethod": "BPI_TRANSFER",
    "notes": "Full settlement"
  }'
```

### 2.2 List All Payments
**Endpoint**: `GET /v1/accounting/payments`
**Context**: Retrieves a full history of all payments processed across the fleet.
**Permissions**: Finance Owner, Admin

**Response (200 OK)**:
```json
{
  "success": true,
  "data": [
    {
      "id": "766a18de-ff23-4732-9584-bc40f57d87f7",
      "paymentNumber": "PAY-1739021673000",
      "amount": 500.0,
      "paymentMethod": "CASH",
      "status": "COMPLETED",
      "paymentDate": "2026-02-08T21:34:00Z",
      "notes": "Partial payment via front-desk"
    }
  ],
  "requestId": "req-12347"
}
```

### 2.3 List Customer Payments
**Endpoint**: `GET /v1/accounting/payments/customer/{id}`
**Context**: Retrieves all payments made by a specific customer.
**Permissions**: Finance Owner, Admin, Customer (Own Records Only)

**Response (200 OK)**:
```json
{
  "success": true,
  "data": [
    {
      "id": "766a18de-ff23-4732-9584-bc40f57d87f7",
      "paymentNumber": "PAY-1739021673000",
      "amount": 500.0,
      "paymentMethod": "CASH",
      "status": "COMPLETED",
      "paymentDate": "2026-02-08T21:34:00Z",
      "notes": "Partial payment via front-desk"
    }
  ],
  "requestId": "req-12348"
}
```

---

## 3. Chart of Accounts

### 3.1 List All Accounts
**Endpoint**: `GET /v1/accounting/accounts`
**Context**: Retrieves the full Chart of Accounts used for the General Ledger.

**Response (200 OK)**:
```json
{
  "success": true,
  "data": [
    {
      "id": "1000",
      "accountCode": "1000",
      "accountName": "Cash",
      "accountType": "ASSET",
      "isActive": true,
      "description": "Cash on hand",
      "balance": 1500.00
    },
    {
      "id": "1010",
      "accountCode": "1010",
      "accountName": "Bank Account (BPI)",
      "accountType": "ASSET",
      "isActive": true,
      "description": "Main operating bank account"
    },
    {
      "id": "1020",
      "accountCode": "1020",
      "accountName": "GCash Wallet",
      "accountType": "ASSET",
      "isActive": true,
      "description": "GCash merchant wallet"
    },
    {
      "id": "1040",
      "accountCode": "1040",
      "accountName": "Credit Card Clearing",
      "accountType": "ASSET",
      "isActive": true,
      "description": "Payments via Stripe/Terminal awaiting settlement"
    },
    {
      "id": "1100",
      "accountCode": "1100",
      "accountName": "Accounts Receivable",
      "accountType": "ASSET",
      "isActive": true,
      "description": "Money owed by customers"
    }
  ],
  "requestId": "req-12349"
}
```

### 3.2 Get Account Balance
**Endpoint**: `GET /v1/accounting/accounts/{code}/balance`
**Context**: Calculates the current real-time balance for a specific account based on Ledger entries.

**Response (200 OK)**:
```json
{
  "success": true,
  "data": {
    "account": "Cash",
    "balance": 1500.00
  },
  "requestId": "req-12350"
}
```

---

## 4. Payment Methods

### 4.1 List Payment Methods
**Endpoint**: `GET /v1/accounting/payment-methods`
**Context**: Retrieves the dynamic list of active payment methods and their display names.

**Response (200 OK)**:
```json
{
  "success": true,
  "data": [
    {
      "id": "7fb69e16-c3bd-4413-8765-7ed881f6e6f3",
      "code": "GCASH",
      "displayName": "GCash",
      "targetAccountCode": "1020",
      "isActive": true,
      "description": "GCash merchant wallet"
    },
    {
      "id": "8ab69e16-d4bd-5513-9865-8ed992f7f7g4",
      "code": "MAYA",
      "displayName": "Maya (PayMaya)",
      "targetAccountCode": "1030",
      "isActive": true
    }
  ],
  "requestId": "req-12351"
}
```

### 4.2 Create Payment Method
**Endpoint**: `POST /v1/accounting/payment-methods`
**Permissions**: Admin

**Request**:
```json
{
  "code": "PAYPAL",
  "displayName": "PayPal Business",
  "targetAccountCode": "1010",
  "isActive": true,
  "description": "Online payments via PayPal"
}
```

**Response (201 Created)**:
```json
{
  "success": true,
  "data": {
    "id": "9bc69e16-e5bd-6613-0965-9fe003f8f8h5",
    "code": "PAYPAL",
    "displayName": "PayPal Business",
    "targetAccountCode": "1010",
    "isActive": true,
    "description": "Online payments via PayPal"
  },
  "requestId": "req-12352"
}
```

### 4.3 Update Payment Method
**Endpoint**: `PUT /v1/accounting/payment-methods/{id}`

**Request**:
```json
{
  "code": "GCASH",
  "displayName": "GCash Wallet (Updated)",
  "targetAccountCode": "1020",
  "isActive": false
}
```

---

## 5. Generic Deletion

### 5.1 Delete Payment
**Endpoint**: `DELETE /v1/accounting/payments/{id}`
**Context**: Permanent removal of a payment record.
**Danger**: Use with caution. Does not automatically reverse General Ledger entries.

**Response (200 OK)**:
```json
{
  "success": true,
  "data": {
    "deleted": true
  },
  "requestId": "req-12353"
}
```
