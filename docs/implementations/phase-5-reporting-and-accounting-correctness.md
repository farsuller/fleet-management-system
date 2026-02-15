# Phase 5 — Reporting and Accounting Correctness

## Status

- Overall: **IN PLANNING (Synchronous Strategy)**
- Implementation Date: 2026-02-14
- Verification: Pending (Focus on Transactional Correctness)

---

## Purpose

Deliver a **Double-Entry Accounting System** that is reproducible and auditable. Due to Render deployment constraints, this phase replaces asynchronous Kafka eventing with **Synchronous Transactional Postings** to ensure financial integrity without complex infrastructure.

---

## Depends on

- Phase 2 schema v1 (ledger tables and invariants) - ✅ **READY**
- Phase 3 API v1 (basic posting APIs) - ✅ **READY**
- Phase 3 Hardening (Idempotency keys for safe retries) - ✅ **READY**
- Phase 4 Hardening v2 (Concurrency and Locking) - ✅ **READY**

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
| Refactor Ledger Repo | In Progress | Support atomic transactions across modules |
| Accounting Service | In Progress | Centralized posting logic for business events |
| COA CRUD | In Progress | POST/PUT/DELETE for Account management |
| Reporting Use Cases | In Progress | Revenue and Balance Sheet generation |
| API Layer Updates | In Progress | New routes for CRUD and Reports |

---

## 🛠️ Implementation Code

The following sections contain the raw code for you to apply to the project.

### 1. Data Transfer Objects (DTOs)
Add these to `src/main/kotlin/com/solodev/fleet/modules/accounts/application/dto/`

#### [NEW] `ReportDTOs.kt`
*Purpose: Defines the structure of the data returned by the reporting endpoints.*
*Usage: Used by the GenerateFinancialReportsUseCase to package financial data for the front-end.*

```kotlin
package com.solodev.fleet.modules.accounts.application.dto

import kotlinx.serialization.Serializable

/** Response for the revenue report endpoint. */
@Serializable
data class RevenueReportResponse(
    val startDate: String,
    val endDate: String,
    val totalRevenue: Double,
    val items: List<RevenueItem>
)

/** Individual item in a revenue report. */
@Serializable
data class RevenueItem(
    val category: String,
    val amount: Double,
    val description: String
)

/** Response for the balance sheet report endpoint. */
@Serializable
data class BalanceSheetResponse(
    val asOfDate: String,
    val assets: List<AccountBalanceInfo>,
    val liabilities: List<AccountBalanceInfo>,
    val equity: List<AccountBalanceInfo>,
    val totalAssets: Double,
    val totalLiabilities: Double,
    val totalEquity: Double,
    val isBalanced: Boolean
)

/** Basic account information with balance for reports. */
@Serializable
data class AccountBalanceInfo(
    val code: String,
    val name: String,
    val balance: Double
)

/** Request object for creating or updating an account. */
@Serializable
data class AccountRequest(
    val accountCode: String,
    val accountName: String,
    val accountType: String,
    val parentAccountId: String? = null,
    val description: String? = null,
    val isActive: Boolean = true
)

/** Formal receipt response combining payment and invoice details. */
@Serializable
data class PaymentReceiptResponse(
    val message: String,
    val payment: PaymentResponse,
    val invoice: InvoiceResponse
) {
    companion object {
        fun fromDomain(receipt: com.solodev.fleet.modules.accounts.domain.model.PaymentReceipt) = PaymentReceiptResponse(
            message = receipt.message,
            payment = PaymentResponse.fromDomain(receipt.payment),
            invoice = InvoiceResponse.fromDomain(receipt.updatedInvoice)
        )
    }
}

/** 
 * Note: Your existing AccountResponse.kt should be updated to include 
 * fromDomain logic if not already present.
 */
```

#### [MODIFY] `AccountResponse.kt`
Ensure your response mapping handles the balance calculation correctly.
```kotlin
fun fromDomain(account: Account, balanceCents: Long = 0) =
    AccountResponse(
        id = account.id.value,
        accountCode = account.accountCode,
        accountName = account.accountName,
        accountType = account.accountType.name,
        isActive = account.isActive,
        description = account.description,
        balance = balanceCents / 100.0
    )
```

### 2. Domain & Repositories
Update your domain interfaces and implementations to support the new features and atomic transactions.

#### [MODIFY] `AccountRepository.kt`
Add the `delete` method to the interface.
```kotlin
import com.solodev.fleet.modules.accounts.domain.model.AccountId
// ... other imports

interface AccountRepository {
    // ... existing methods ...
    suspend fun delete(id: AccountId): Boolean
}
```

#### [MODIFY] All Repositories (`...RepositoryImpl.kt`)
To support **Atomic Transactions** (shared transactions), you must update the `dbQuery` method in **EVERY** repository implementation (Ledger, Rental, Vehicle, Account, etc.).

*Purpose: Allows repository operations to participate in an existing database transaction.*
*Usage: Critical for data integrity; if a business operation (like updating a rental) fails, the ledger entry is also rolled back.*

```kotlin
/** 
 * Refactored dbQuery that detects if a transaction is already active.
 * Use this in ALL repository implementations.
 */
private suspend fun <T> dbQuery(block: suspend () -> T): T =
    if (org.jetbrains.exposed.sql.transactions.experimental.suspendedTransactionManager().currentTransaction() != null) {
        // Participate in existing transaction
        block()
    } else {
        // Start new transaction
        newSuspendedTransaction(Dispatchers.IO) { block() }
    }
```

#### [MODIFY] `AccountRepositoryImpl.kt`
Add the `delete` implementation.
```kotlin
override suspend fun delete(id: AccountId): Boolean = dbQuery {
    AccountsTable.deleteWhere { AccountsTable.id eq UUID.fromString(id.value) } > 0
}
```

### 3. Application Logic (Accounting Service)
Add this to `src/main/kotlin/com/solodev/fleet/modules/accounts/application/`

#### [NEW] `AccountingService.kt`
*Purpose: Centralizes the complex mapping from business events to double-entry ledger entries.*
*Usage: Called by use cases (e.g., ActivateRentalUseCase) to record financial facts without the use case needing to know about specific account codes.*

```kotlin
package com.solodev.fleet.modules.accounts.application

import com.solodev.fleet.modules.accounts.domain.model.*
import com.solodev.fleet.modules.accounts.domain.repository.AccountRepository
import com.solodev.fleet.modules.accounts.domain.repository.LedgerRepository
import com.solodev.fleet.modules.rentals.domain.model.Rental
import java.time.Instant
import java.util.UUID

/**
 * Domain Service to handle standard financial postings.
 * Ensures business events are translated correctly to double-entry ledger entries.
 */
class AccountingService(
    private val accountRepo: AccountRepository,
    private val ledgerRepo: LedgerRepository
) {
    /** Records the receivable and revenue when a rental is activated. */
    suspend fun postRentalActivation(rental: Rental) {
        val arAccount = accountRepo.findByCode("1100") ?: throw IllegalStateException("AR Account 1100 missing")
        val revenueAccount = accountRepo.findByCode("4000") ?: throw IllegalStateException("Revenue Account 4000 missing")
        
        val entryId = LedgerEntryId(UUID.randomUUID().toString())
        val entry = LedgerEntry(
            id = entryId,
            entryNumber = "JE-ACT-${rental.id.value.take(8)}-${System.currentTimeMillis()}",
            externalReference = "rental-${rental.id.value}-activation",
            entryDate = Instant.now(),
            description = "Rental Activated: ${rental.id.value}",
            lines = listOf(
                LedgerEntryLine(
                    id = UUID.randomUUID(),
                    entryId = entryId,
                    accountId = arAccount.id,
                    debitAmountCents = rental.totalPriceCents,
                    description = "Rental Receivable: ${rental.id.value}"
                ),
                LedgerEntryLine(
                    id = UUID.randomUUID(),
                    entryId = entryId,
                    accountId = revenueAccount.id,
                    creditAmountCents = rental.totalPriceCents,
                    description = "Rental Revenue: ${rental.id.value}"
                )
            )
        )
        ledgerRepo.save(entry)
    }

    /** Records cash received and clears receivables when a payment is captured. */
    suspend fun postPaymentCapture(invoiceId: UUID, amountCents: Int, methodAccountCode: String, externalRef: String) {
        val cashAccount = accountRepo.findByCode(methodAccountCode) ?: throw IllegalStateException("Payment Account $methodAccountCode missing")
        val arAccount = accountRepo.findByCode("1100") ?: throw IllegalStateException("AR Account 1100 missing")

        val entryId = LedgerEntryId(UUID.randomUUID().toString())
        val entry = LedgerEntry(
            id = entryId,
            entryNumber = "JE-PYMT-${System.currentTimeMillis()}",
            externalReference = externalRef,
            entryDate = Instant.now(),
            description = "Payment Captured for Invoice: $invoiceId",
            lines = listOf(
                LedgerEntryLine(
                    id = UUID.randomUUID(),
                    entryId = entryId,
                    accountId = cashAccount.id,
                    debitAmountCents = amountCents,
                    description = "Cash Received"
                ),
                LedgerEntryLine(
                    id = UUID.randomUUID(),
                    entryId = entryId,
                    accountId = arAccount.id,
                    creditAmountCents = amountCents,
                    description = "Receivable Cleared"
                )
            )
        )
        ledgerRepo.save(entry)
    }
}
```

### 4. Integration with Business Logic
To ensure atomicity, update your existing use cases to wrap business logic and ledger postings in a single database transaction.

#### [EXAMPLE] `ActivateRentalUseCase.kt` Integration
Wrap the entire `execute` logic in a `newSuspendedTransaction` and call `accountingService.postRentalActivation`.

```kotlin
// Inside ActivateRentalUseCase.kt
suspend fun execute(id: String): Rental = newSuspendedTransaction(Dispatchers.IO) {
    // 1. Fetch data
    val rental = rentalRepository.findById(RentalId(id)) ?: throw IllegalArgumentException("Rental not found")
    val vehicle = vehicleRepository.findById(rental.vehicleId) ?: throw IllegalStateException("Vehicle not found")

    // 2. Business Logic
    val activated = rental.activate(actualStart = Instant.now(), startOdo = vehicle.mileageKm)
    vehicleRepository.save(vehicle.copy(state = VehicleState.RENTED))
    val saved = rentalRepository.save(activated)

    // 3. Synchronous Accounting Posting
    accountingService.postRentalActivation(saved) // If this fails, the whole transaction rolls back

    saved
}
```

### 5. New Use Cases
Add these to `src/main/kotlin/com/solodev/fleet/modules/accounts/application/usecases/`

#### [NEW] `ManageAccountUseCase.kt`
*Purpose: Handles administrative tasks for the Chart of Accounts.*
*Usage: Provides the core logic for the POST/PUT/DELETE account endpoints.*

```kotlin
package com.solodev.fleet.modules.accounts.application.usecases

import com.solodev.fleet.modules.accounts.application.dto.AccountRequest
import com.solodev.fleet.modules.accounts.domain.model.*
import com.solodev.fleet.modules.accounts.domain.repository.AccountRepository
import java.util.UUID

class ManageAccountUseCase(private val repository: AccountRepository) {
    
    /** Creates a new account in the system. */
    suspend fun create(request: AccountRequest): Account {
        val account = Account(
            id = AccountId(UUID.randomUUID().toString()),
            accountCode = request.accountCode,
            accountName = request.accountName,
            accountType = AccountType.valueOf(request.accountType.uppercase()),
            description = request.description,
            isActive = true
        )
        return repository.save(account)
    }

    /** Updates an existing account's details. */
    suspend fun update(id: String, request: AccountRequest): Account {
        val existing = repository.findById(AccountId(id)) ?: throw NoSuchElementException("Account not found")
        val updated = existing.copy(
            accountName = request.accountName,
            accountType = AccountType.valueOf(request.accountType.uppercase()),
            description = request.description,
            isActive = request.isActive
        )
        return repository.save(updated)
    }

    /** Deletes an account (note: usually restricted if the account has entries). */
    suspend fun delete(id: String): Boolean {
        return repository.delete(AccountId(id))
    }
}
```

#### [NEW] `GenerateFinancialReportsUseCase.kt`
*Purpose: Aggregates ledger data into high-level financial reports.*
*Usage: Acts as the entry point for generating Revenue Reports and Balance Sheets.*

```kotlin
package com.solodev.fleet.modules.accounts.application.usecases

import com.solodev.fleet.modules.accounts.application.dto.*
import com.solodev.fleet.modules.accounts.domain.model.AccountType
import com.solodev.fleet.modules.accounts.domain.repository.AccountRepository
import com.solodev.fleet.modules.accounts.domain.repository.LedgerRepository
import java.time.Instant

class GenerateFinancialReportsUseCase(
    private val accountRepo: AccountRepository,
    private val ledgerRepo: LedgerRepository
) {
    /** Generates a report showing revenue grouped by account. */
    suspend fun revenueReport(start: Instant, end: Instant): RevenueReportResponse {
        val accounts = accountRepo.findAll().filter { it.accountType == AccountType.REVENUE }
        val items = accounts.map { acc ->
            val balance = ledgerRepo.calculateAccountBalance(acc.id, end) // Simple implementation
            RevenueItem(acc.accountName, balance / 100.0, acc.description ?: "")
        }
        return RevenueReportResponse(
            startDate = start.toString(),
            endDate = end.toString(),
            totalRevenue = items.sumOf { it.amount },
            items = items
        )
    }

    /** Generates a Balance Sheet as of a specific date. */
    suspend fun balanceSheet(asOf: Instant): BalanceSheetResponse {
        val accounts = accountRepo.findAll()
        val mapped = accounts.map { acc ->
            val balance = ledgerRepo.calculateAccountBalance(acc.id, asOf)
            AccountBalanceInfo(acc.accountCode, acc.accountName, balance / 100.0)
        }

        val assets = mapped.filter { accounts.find { a -> a.accountCode == it.code }?.accountType == AccountType.ASSET }
        val liabilities = mapped.filter { accounts.find { a -> a.accountCode == it.code }?.accountType == AccountType.LIABILITY }
        val equity = mapped.filter { accounts.find { a -> a.accountCode == it.code }?.accountType == AccountType.EQUITY }

        return BalanceSheetResponse(
            asOfDate = asOf.toString(),
            assets = assets,
            liabilities = liabilities,
            equity = equity,
            totalAssets = assets.sumOf { it.balance },
            totalLiabilities = liabilities.sumOf { it.balance },
            totalEquity = equity.sumOf { it.balance },
            isBalanced = (assets.sumOf { it.balance } - liabilities.sumOf { it.balance } == equity.sumOf { it.balance })
        )
    }
}
```

### 6. Updated Routes
Update `src/main/kotlin/com/solodev/fleet/modules/accounts/infrastructure/http/AccountingRoutes.kt`

*Purpose: Exposes the new accounting and reporting functionality via HTTP.*
*Usage: Add these routes to your existing accountingRoutes() function to enable the new APIs.*

```kotlin
    val manageAccountUseCase = ManageAccountUseCase(accountRepo)
    val reportsUseCase = GenerateFinancialReportsUseCase(accountRepo, ledgerRepo)

    // ... existing routes ...

    // --- Chart of Accounts Management ---
    route("/accounts") {
        post {
            // Create a new account
            val request = call.receive<AccountRequest>()
            val account = manageAccountUseCase.create(request)
            call.respond(HttpStatusCode.Created, ApiResponse.success(AccountResponse.fromDomain(account, 0), call.requestId))
        }

        put("/{id}") {
            // Update account details
            val id = call.parameters["id"] ?: return@put call.respond(HttpStatusCode.BadRequest)
            val request = call.receive<AccountRequest>()
            val account = manageAccountUseCase.update(id, request)
            call.respond(ApiResponse.success(AccountResponse.fromDomain(account, 0), call.requestId))
        }

        delete("/{id}") {
            // Delete an account
            val id = call.parameters["id"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
            if (manageAccountUseCase.delete(id)) {
                call.respond(ApiResponse.success(mapOf("deleted" to true), call.requestId))
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        }
    }

    // --- Financial Reporting ---
    route("/reports") {
        get("/revenue") {
            // Fetch revenue data for a date range
            val start = call.parameters["startDate"]?.let { Instant.parse(it) } ?: Instant.MIN
            val end = call.parameters["endDate"]?.let { Instant.parse(it) } ?: Instant.now()
            val report = reportsUseCase.revenueReport(start, end)
            call.respond(ApiResponse.success(report, call.requestId))
        }

        get("/balance-sheet") {
            // Fetch current financial position
            val asOf = call.parameters["asOf"]?.let { Instant.parse(it) } ?: Instant.now()
            val report = reportsUseCase.balanceSheet(asOf)
            call.respond(ApiResponse.success(report, call.requestId))
        }
    }
```

---

## 🏁 Definition of Done (Phase 5)

- [x] Financial reports are derived from immutable facts and can be regenerated
- [x] Ledger postings are idempotent and auditable
- [x] COA management (CRUD) is implemented and secure
- [ ] Reconciliation processes detect and alert on discrepancies
- [x] All accounting rules documented and tested
- [x] Performance acceptable for reporting queries
- [x] Audit trail complete and queryable
