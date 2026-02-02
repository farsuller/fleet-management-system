# Phase 3: Accounting API Implementation Guide

This guide details the implementation for the Accounting domain.

## 1. Directory Structure

```text
src/main/kotlin/com/solodev/fleet/modules/
├── accounting/
│   ├── application/
│   │   ├── dto/            <-- AccountDTO, LedgerEntryRequest, InvoiceResponse
│   │   └── usecases/       <-- CreateLedgerEntryUseCase, PayInvoiceUseCase
│   └── infrastructure/
│       └── http/           <-- AccountingRoutes.kt
```

## 2. Implementation Checklist

### A. DTOs
- [ ] `AccountDTO.kt`: For managing the chart of accounts.
- [ ] `LedgerEntryRequest.kt`: Includes multiple lines for double-entry validation.
- [ ] `InvoiceResponse.kt`: Details on invoice status and balance.

### B. Use Cases
- [ ] `CreateJournalEntryUseCase.kt`: Ensure debits == credits before saving.
- [ ] `IssueInvoiceUseCase.kt`: Generate invoice for a completed rental.
- [ ] `RecordPaymentUseCase.kt`: Update invoice status and balance.

### C. Routes
- [ ] `AccountingRoutes.kt`: 
  - `GET /v1/accounting/accounts`
  - `POST /v1/accounting/ledger`
  - `GET /v1/accounting/invoices/{id}`
  - `POST /v1/accounting/invoices/{id}/pay`

## 3. Code Samples

### InvoiceResponse Mapper
```kotlin
fun fromDomain(i: Invoice) = InvoiceResponse(
    id = i.id.toString(),
    invoiceNumber = i.invoiceNumber,
    status = i.status.name,
    totalAmount = i.totalCents,
    balance = i.balanceCents,
    isPaid = i.isPaid
)
```

---

## 3. API Endpoints & Sample Payloads

### **A. Issue Invoice**
- **Endpoint**: `POST /v1/accounting/invoices`
- **Request Body**:
```json
{
  "rentalId": "r-1a2b3c",
  "customerId": "c-5a4b3c2d",
  "subtotalCents": 25000,
  "taxCents": 2500,
  "issueDate": "2024-02-15T10:00:00Z",
  "dueDate": "2024-03-01T10:00:00Z"
}
```
- **Response Body (200 OK)**:
```json
{
  "success": true,
  "data": {
    "id": "inv-505",
    "invoiceNumber": "INV-2024-001",
    "status": "ISSUED",
    "totalAmount": 27500,
    "balance": 27500,
    "isPaid": false
  },
  "requestId": "req-333"
}
```

---

## 4. Wiring
In `Routing.kt`:
```kotlin
val accountingRepo = AccountingRepositoryImpl()
accountingRoutes(accountingRepo)
```
