# Accounting API - Complete Implementation Guide

**Version**: 1.8 (Ledger Integrated)
**Last Updated**: 2026-02-08
**Status**: ACTIVE

---

## 1. Directory Structure (Clean Architecture)

Follow this structure strictly to avoid "logic bleed" between layers:

```text
src/main/kotlin/com/solodev/fleet/modules/accounts/
├── domain/
│   ├── model/
│   │   └── Accounting.kt (Invoice, Account, LedgerEntry, Payment, PaymentReceipt)
│   └── repository/
│       ├── AccountRepository.kt
│       ├── InvoiceRepository.kt
│       ├── LedgerRepository.kt
│       └── PaymentRepository.kt
├── application/
│   ├── dto/
│   │   ├── InvoiceRequest.kt
│   │   ├── InvoiceResponse.kt
│   │   ├── PaymentRequest.kt
│   │   └── PaymentResponse.kt (Inc. PaymentReceiptResponse)
│   └── usecases/
│       ├── IssueInvoiceUseCase.kt
│       └── PayInvoiceUseCase.kt
└── infrastructure/
    ├── persistence/
    │   ├── AccountingTables.kt (Accounts, Ledger, Invoices, Payments Tables)
    │   ├── AccountRepositoryImpl.kt
    │   ├── InvoiceRepositoryImpl.kt
    │   ├── LedgerRepositoryImpl.kt
    │   ├── PaymentRepositoryImpl.kt
    │   └── PaymentMethodRepositoryImpl.kt
    └── http/
        └── AccountingRoutes.kt
```

---

## 2. Infrastructure Layer: Persistence

### **Full Schema Definition (Aligned)**
Ensure your `InvoicesTable` and `PaymentsTable` include all required fields for auditing and tracking.

**Key Table: InvoicesTable**
```kotlin
object InvoicesTable : UUIDTable("invoices") {
    val invoiceNumber = varchar("invoice_number", 50).uniqueIndex()
    val customerId = reference("customer_id", CustomersTable, onDelete = ReferenceOption.RESTRICT)
    val status = varchar("status", 20)
    val subtotalCents = integer("subtotal_cents")
    val taxCents = integer("tax_cents").default(0)
    val paidCents = integer("paid_cents").default(0)
    val balanceCents = integer("balance_cents")
    val issueDate = date("issue_date")
    val dueDate = date("due_date")
    val paidDate = date("paid_date").nullable()
}
```

---

## 3. Application Layer: Use Cases & DTOs

### **UseCase: IssueInvoiceUseCase.kt**
Coordinates recording the new invoice and posting to the general ledger (Revenue recognition).

```kotlin
class IssueInvoiceUseCase(
    private val repository: InvoiceRepository,
    private val accountRepo: AccountRepository,
    private val ledgerRepo: LedgerRepository
) {
    suspend fun execute(request: InvoiceRequest): Invoice {
        val invoice = Invoice(
            id = UUID.randomUUID(),
            invoiceNumber = "INV-${System.currentTimeMillis()}",
            customerId = CustomerId(request.customerId),
            rentalId = request.rentalId?.let { RentalId(it) },
            status = InvoiceStatus.ISSUED,
            subtotalCents = request.subtotalCents,
            taxCents = request.taxCents,
            paidCents = 0,
            issueDate = Instant.now(),
            dueDate = Instant.parse(request.dueDate)
        )
        val savedInvoice = repository.save(invoice)

        // Post to General Ledger (Accrual Basis)
        // Debit: Accounts Receivable (1100), Credit: Rental Revenue (4000)
        val arAccount = accountRepo.findByCode("1100") ?: error("AR account not found")
        val revenueAccount = accountRepo.findByCode("4000") ?: error("Revenue account not found")

        val entryId = LedgerEntryId(UUID.randomUUID().toString())
        val ledgerEntry = LedgerEntry(
            id = entryId,
            entryNumber = "JE-${System.currentTimeMillis()}",
            externalReference = "invoice-${savedInvoice.invoiceNumber}",
            entryDate = Instant.now(),
            description = "Invoice issued: ${savedInvoice.invoiceNumber}",
            lines = listOf(
                LedgerEntryLine(id = UUID.randomUUID(), entryId = entryId, accountId = arAccount.id, debitAmountCents = savedInvoice.totalCents, creditAmountCents = 0, description = "Receivable"),
                LedgerEntryLine(id = UUID.randomUUID(), entryId = entryId, accountId = revenueAccount.id, debitAmountCents = 0, creditAmountCents = savedInvoice.totalCents, description = "Revenue")
            )
        )
        ledgerRepo.save(ledgerEntry)
        return savedInvoice
    }
}
```

### **UseCase: PayInvoiceUseCase.kt**
Coordinates recording the payment, updating the invoice, and posting to the ledger with payment method mapping.

```kotlin
class PayInvoiceUseCase(
    private val invoiceRepo: InvoiceRepository,
    private val paymentRepo: PaymentRepository,
    private val accountRepo: AccountRepository,
    private val ledgerRepo: LedgerRepository,
    private val paymentMethodRepo: PaymentMethodRepository
) {
    suspend fun execute(
        invoiceId: String,
        amount: Double,
        paymentMethod: String,
        notes: String? = null
    ): PaymentReceipt {
        val amountCents = (amount * 100).toInt()
        val invoice = invoiceRepo.findById(UUID.fromString(invoiceId)) ?: throw IllegalArgumentException("Invoice not found")

        if (amountCents > invoice.balanceCents) throw IllegalArgumentException("Overpayment")

        // 1. Create Payment Record
        val payment = Payment(
            id = UUID.randomUUID(),
            paymentNumber = "PAY-${System.currentTimeMillis()}",
            customerId = invoice.customerId,
            invoiceId = invoice.id,
            amountCents = amountCents,
            paymentMethod = paymentMethod,
            status = PaymentStatus.COMPLETED,
            paymentDate = Instant.now(),
            notes = notes
        )
        paymentRepo.save(payment)

        // 2. Update Invoice state
        val updatedPaidCents = invoice.paidCents + amountCents
        val updatedInvoice = invoice.copy(
            paidCents = updatedPaidCents,
            status = if (invoice.totalCents <= updatedPaidCents) InvoiceStatus.PAID else InvoiceStatus.ISSUED,
            paidDate = if (invoice.totalCents <= updatedPaidCents) Instant.now() else null
        )
        val savedInvoice = invoiceRepo.save(updatedInvoice)

        // 3. Post to General Ledger
        // Map payment method to the correct Asset account via dynamic lookup
        val targetAccountCode = paymentMethodRepo.findByCode(paymentMethod.uppercase())?.targetAccountCode
            ?: "1000" // Fallback to Cash if not found or not configured

        val assetAccount = accountRepo.findByCode(targetAccountCode)
            ?: throw IllegalStateException("Asset account ($targetAccountCode) not found")
        val arAccount = accountRepo.findByCode("1100")
            ?: throw IllegalStateException("Accounts Receivable account (1100) not found")

        val entryId = LedgerEntryId(UUID.randomUUID().toString())
        val ledgerEntry = LedgerEntry(
            id = entryId,
            entryNumber = "JE-${System.currentTimeMillis()}",
            externalReference = "payment-${payment.paymentNumber}",
            entryDate = Instant.now(),
            description = "Payment ($paymentMethod) received for Invoice ${invoice.invoiceNumber}. ${notes ?: ""}",
            lines = listOf(
                LedgerEntryLine(
                    id = UUID.randomUUID(),
                    entryId = entryId,
                    accountId = assetAccount.id,
                    debitAmountCents = amountCents,
                    creditAmountCents = 0,
                    description = "Payment for ${invoice.invoiceNumber} via $paymentMethod"
                ),
                LedgerEntryLine(
                    id = UUID.randomUUID(),
                    entryId = entryId,
                    accountId = arAccount.id,
                    debitAmountCents = 0,
                    creditAmountCents = amountCents,
                    description = "Payment for ${invoice.invoiceNumber}"
                )
            )
        )
        ledgerRepo.save(ledgerEntry)

        // 4. Return Receipt
        return PaymentReceipt(
            message = "Payment of ${amountCents / 100.0} ${invoice.currencyCode} processed successfully.",
            payment = payment,
            updatedInvoice = savedInvoice
        )
    }
}
```
```

---

## 4. Ktor Routes (Secured)

```kotlin
fun Route.accountingRoutes(
    invoiceRepository: InvoiceRepository,
    paymentRepository: PaymentRepository,
    accountRepository: AccountRepository,
    ledgerRepository: LedgerRepository,
    paymentMethodRepository: PaymentMethodRepository
) {
    val issueInvoiceUseCase = IssueInvoiceUseCase(invoiceRepository, accountRepository, ledgerRepository)
    val payInvoiceUseCase = PayInvoiceUseCase(
        invoiceRepository,
        paymentRepository,
        accountRepository,
        ledgerRepository,
        paymentMethodRepository
    )

    authenticate("auth-jwt") {
        route("/v1/accounting") {
            // Invoices
            post("/invoices") { /* calls issueInvoiceUseCase.execute(request) */ }
            post("/invoices/{id}/pay") { /* calls payInvoiceUseCase.execute(...) */ }

            // Ledger
            get("/accounts/{code}/balance") { /* calls ledgerRepository.calculateAccountBalance(...) */ }

            // Payment Methods
            get("/payment-methods") { /* calls paymentMethodRepository.findAll() */ }
            post("/payment-methods") { /* calls paymentMethodRepository.save(...) */ }
            put("/payment-methods/{id}") { /* calls paymentMethodRepository.save(updated) */ }
            delete("/payment-methods/{id}") { /* calls paymentMethodRepository.delete(...) */ }
            
            // Payments Deletion
            delete("/payments/{id}") { /* calls paymentRepository.delete(...) */ }
        }
    }
}
```

---

## 5. Checklist for Review

1.  [x] **Audit Trail**: Does every payment create a entry in the `PaymentsTable`?
2.  [x] **State Sync**: Does the `PayInvoice` use case update both `Payment` and `Invoice` states?
3.  [x] **User Feedback**: Does the response include a meaningful `message` for the UI?
4.  [x] **General Ledger**: Ledger posting implementation completed with specialized accounts (Bank, GCash, PayMaya).

---
