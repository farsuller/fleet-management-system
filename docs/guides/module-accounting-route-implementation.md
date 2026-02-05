# Phase 3: Accounting API Implementation Guide

**Original Implementation**: Pending  
**Enhanced Implementation**: Pending (Awaiting Skills Application)  
**Verification**: Not yet started  
**Server Status**: ⏳ PENDING  
**Compliance**: 0%  
**Standards**: Will follow IMPLEMENTATION-STANDARDS.md  
**Skills to Apply**: Backend Development, Clean Code, API Patterns, Lint & Validate

This guide details the implementation for the Accounting module, focusing on Chart of Accounts and Invoicing.

---

## 1. Directory Structure

```text
src/main/kotlin/com/solodev/fleet/modules/accounting/
├── application/
│   ├── dto/
│   │   ├── AccountDTO.kt
│   │   └── InvoiceDTO.kt
│   └── usecases/
│       ├── CreateAccountUseCase.kt
│       └── IssueInvoiceUseCase.kt
└── infrastructure/
    └── http/
        └── AccountingRoutes.kt
```

---

## 2. Domain Model

### Accounting.kt
`src/main/kotlin/com/solodev/fleet/modules/domain/models/Accounting.kt`

```kotlin
package com.solodev.fleet.modules.domain.models

/** Account type in the chart of accounts. */
enum class AccountType {
    ASSET, LIABILITY, EQUITY, REVENUE, EXPENSE
}

/** Invoice status. */
enum class InvoiceStatus {
    DRAFT, ISSUED, PAID, OVERDUE, CANCELLED
}

/**
 * Account domain entity.
 */
data class Account(
    val id: AccountId,
    val accountCode: String,
    val accountName: String,
    val accountType: AccountType,
    val isActive: Boolean = true
)

/** Invoice domain entity. */
data class Invoice(
    val id: UUID,
    val invoiceNumber: String,
    val customerId: CustomerId,
    val status: InvoiceStatus,
    val subtotalCents: Int,
    val taxCents: Int = 0,
    val currencyCode: String = "PHP"
) {
    val totalCents: Int get() = subtotalCents + taxCents
}
```

---

## 3. Data Transfer Objects (DTOs)

### **AccountDTO.kt**
`src/main/kotlin/com/solodev/fleet/modules/accounting/application/dto/AccountDTO.kt`
```kotlin
package com.solodev.fleet.modules.accounting.application.dto

import com.solodev.fleet.modules.domain.models.Account
import kotlinx.serialization.Serializable

@Serializable
data class AccountRequest(
    val accountCode: String,
    val accountName: String,
    val accountType: String, // ASSET, LIABILITY, etc.
    val description: String? = null
)

@Serializable
data class AccountResponse(
    val id: String,
    val accountCode: String,
    val accountName: String,
    val accountType: String,
    val isActive: Boolean
) {
    companion object {
        fun fromDomain(a: Account) = AccountResponse(
            id = a.id.value,
            accountCode = a.accountCode,
            accountName = a.accountName,
            accountType = a.accountType.name,
            isActive = a.isActive
        )
    }
}
```

### **InvoiceDTO.kt**
`src/main/kotlin/com/solodev/fleet/modules/accounting/application/dto/InvoiceDTO.kt`
```kotlin
package com.solodev.fleet.modules.accounting.application.dto

import com.solodev.fleet.modules.domain.models.Invoice
import kotlinx.serialization.Serializable

@Serializable
data class InvoiceRequest(
    val customerId: String,
    val rentalId: String? = null,
    val subtotalCents: Int,
    val taxCents: Int = 0,
    val dueDate: String // ISO-8601
)

@Serializable
data class InvoiceResponse(
    val id: String,
    val invoiceNumber: String,
    val customerId: String,
    val status: String,
    val totalAmountCents: Int,
    val balanceCents: Int,
    val dueDate: String
) {
    companion object {
        fun fromDomain(i: Invoice) = InvoiceResponse(
            id = i.id.toString(),
            invoiceNumber = i.invoiceNumber,
            customerId = i.customerId.value,
            status = i.status.name,
            totalAmountCents = i.totalCents,
            balanceCents = i.balanceCents,
            dueDate = i.dueDate.toString()
        )
    }
}
```

---

## 4. Repository Implementation

### AccountingRepositoryImpl.kt
`src/main/kotlin/com/solodev/fleet/modules/infrastructure/persistence/AccountingRepositoryImpl.kt`

```kotlin
class AccountingRepositoryImpl : AccountingRepository {
    override suspend fun saveAccount(account: Account): Account = dbQuery {
        val exists = AccountsTable.select { AccountsTable.id eq UUID.fromString(account.id.value) }.count() > 0
        if (exists) {
            AccountsTable.update({ AccountsTable.id eq UUID.fromString(account.id.value) }) {
                it[accountName] = account.accountName
                it[updatedAt] = Instant.now()
            }
        } else {
            AccountsTable.insert {
                it[id] = UUID.fromString(account.id.value)
                it[accountCode] = account.accountCode
                it[accountName] = account.accountName
                it[accountType] = account.accountType.name
                it[createdAt] = Instant.now()
                it[updatedAt] = Instant.now()
            }
        }
        account
    }
}
```

---

## 5. Application Use Cases

### **CreateAccountUseCase.kt**
```kotlin
package com.solodev.fleet.modules.accounting.application.usecases

import com.solodev.fleet.modules.domain.models.*
import com.solodev.fleet.modules.domain.ports.AccountingRepository
import com.solodev.fleet.modules.accounting.application.dto.AccountRequest
import java.util.*

class CreateAccountUseCase(private val repository: AccountingRepository) {
    suspend fun execute(request: AccountRequest): Account {
        val account = Account(
            id = AccountId(UUID.randomUUID().toString()),
            accountCode = request.accountCode,
            accountName = request.accountName,
            accountType = AccountType.valueOf(request.accountType),
            description = request.description
        )
        return repository.saveAccount(account)
    }
}
```

---

## 6. Ktor Routes

### **AccountingRoutes.kt**
```kotlin
package com.solodev.fleet.modules.accounting.infrastructure.http

import com.solodev.fleet.modules.domain.ports.AccountingRepository
import com.solodev.fleet.modules.accounting.application.dto.*
import com.solodev.fleet.modules.accounting.application.usecases.*
import com.solodev.fleet.shared.models.ApiResponse
import com.solodev.fleet.shared.plugins.requestId
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.accountingRoutes(repository: AccountingRepository) {
    val createAccountUseCase = CreateAccountUseCase(repository)

    route("/v1/accounting") {
        route("/accounts") {
            post {
                val request = call.receive<AccountRequest>()
                val account = createAccountUseCase.execute(request)
                call.respond(ApiResponse.success(AccountResponse.fromDomain(account), call.requestId))
            }
            get {
                val accounts = repository.findAllAccounts()
                call.respond(ApiResponse.success(accounts.map { AccountResponse.fromDomain(it) }, call.requestId))
            }
        }

        route("/invoices") {
            get {
                val invoices = repository.findAllInvoices()
                call.respond(ApiResponse.success(invoices.map { InvoiceResponse.fromDomain(it) }, call.requestId))
            }
        }
    }
}
```

---

## 7. Testing

### Integration Tests
```kotlin
@Test fun `should create and find accounting account`() = runBlocking {
    val repo = AccountingRepositoryImpl()
    val account = createSampleAccount()
    repo.saveAccount(account)
    
    val found = repo.findAccountById(account.id)
    assertNotNull(found)
    assertEquals(found.accountCode, account.accountCode)
}
```

---

## 8. API Endpoints & Sample Payloads

### **A. Create Account**
- **Endpoint**: `POST /v1/accounting/accounts`
- **Request Body**:
```json
{
  "accountCode": "1001",
  "accountName": "Fleet Revenue",
  "accountType": "REVENUE",
  "description": "Primary revenue account for vehicle rentals"
}
```

### **B. List Invoices**
- **Endpoint**: `GET /v1/accounting/invoices`
- **Response**:
```json
{
  "success": true,
  "data": [
    {
      "id": "78963015-...",
      "invoiceNumber": "INV-2024-001",
      "customerId": "00000000-...",
      "status": "ISSUED",
      "totalAmountCents": 35000,
      "balanceCents": 35000,
      "dueDate": "2024-06-15T10:00:00Z"
    }
  ],
  "requestId": "..."
}
```

---

## 9. Wiring
In `Routing.kt`:
```kotlin
val accountingRepo = AccountingRepositoryImpl() 
routing {
    accountingRoutes(accountingRepo)
}
```
