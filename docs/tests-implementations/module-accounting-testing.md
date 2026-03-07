# Accounting Module - Test Implementation Guide

This document covers testing strategy and implementations for the Accounting module, including double-entry bookkeeping, invoice lifecycle, payment processing, financial reports, and ledger reconciliation.

---

## 1. Domain Unit Tests

### Invoice Domain Tests
`src/test/kotlin/com/solodev/fleet/modules/accounts/domain/model/InvoiceTest.kt`

```kotlin
package com.solodev.fleet.modules.accounts.domain.model

import java.time.Instant
import java.util.UUID
import kotlin.test.*

class InvoiceTest {

    @Test
    fun `totalAmount is subtotal plus tax`() {
        val invoice = sampleInvoice(subtotalCents = 10000, taxCents = 1200)
        assertEquals(11200, invoice.totalAmount)
    }

    @Test
    fun `balance is totalAmount minus paidAmount`() {
        val invoice = sampleInvoice(subtotalCents = 10000, taxCents = 1200, paidAmount = 5000)
        assertEquals(6200, invoice.balance)
    }

    @Test
    fun `balance is zero when fully paid`() {
        val invoice = sampleInvoice(subtotalCents = 10000, taxCents = 0, paidAmount = 10000)
        assertEquals(0, invoice.balance)
    }

    @Test
    fun `new invoice starts with ISSUED status`() {
        val invoice = sampleInvoice()
        assertEquals(InvoiceStatus.ISSUED, invoice.status)
    }

    @Test
    fun `subtotal cannot be negative`() {
        assertFailsWith<IllegalArgumentException> {
            sampleInvoice(subtotalCents = -1)
        }
    }

    @Test
    fun `taxCents cannot be negative`() {
        assertFailsWith<IllegalArgumentException> {
            sampleInvoice(taxCents = -100)
        }
    }

    private fun sampleInvoice(
        subtotalCents: Int = 10000,
        taxCents: Int = 1200,
        paidAmount: Int = 0
    ) = Invoice(
        id = InvoiceId(UUID.randomUUID().toString()),
        invoiceNumber = "INV-0001",
        rentalId = "rental-001",
        customerId = "cust-001",
        subtotalCents = subtotalCents,
        taxCents = taxCents,
        paidAmountCents = paidAmount,
        status = InvoiceStatus.ISSUED,
        dueDate = Instant.parse("2026-12-31T23:59:59Z")
    )
}
```

### LedgerEntry Balance Tests
`src/test/kotlin/com/solodev/fleet/modules/accounts/domain/model/LedgerEntryTest.kt`

```kotlin
package com.solodev.fleet.modules.accounts.domain.model

import kotlin.test.*

class LedgerEntryTest {

    @Test
    fun `debit lines must sum to equal credit lines for balanced entry`() {
        val lines = listOf(
            LedgerLine(accountCode = "1100", debit = 11200, credit = 0),   // AR debit
            LedgerLine(accountCode = "4000", debit = 0,    credit = 11200) // Revenue credit
        )
        assertTrue(lines.sumOf { it.debit } == lines.sumOf { it.credit })
    }

    @Test
    fun `unbalanced ledger lines should fail validation`() {
        val lines = listOf(
            LedgerLine(accountCode = "1100", debit = 11200, credit = 0),
            LedgerLine(accountCode = "4000", debit = 0, credit = 9000) // intentionally wrong
        )
        val balanced = lines.sumOf { it.debit } == lines.sumOf { it.credit }
        assertFalse(balanced)
    }
}
```

---

## 2. Use Case Unit Tests

### IssueInvoiceUseCase Tests
`src/test/kotlin/com/solodev/fleet/modules/accounts/application/usecases/IssueInvoiceUseCaseTest.kt`

```kotlin
package com.solodev.fleet.modules.accounts.application.usecases

import com.solodev.fleet.modules.accounts.application.dto.InvoiceRequest
import com.solodev.fleet.modules.accounts.domain.model.*
import com.solodev.fleet.modules.accounts.domain.repository.*
import io.mockk.*
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class IssueInvoiceUseCaseTest {

    private val invoiceRepo = mockk<InvoiceRepository>()
    private val accountRepo = mockk<AccountRepository>()
    private val ledgerRepo = mockk<LedgerRepository>()
    private val useCase = IssueInvoiceUseCase(invoiceRepo, accountRepo, ledgerRepo)

    @Test
    fun `issues invoice and posts double-entry DR AR 1100 CR Revenue 4000`() = runBlocking {
        val arAccount = sampleAccount("1100", AccountType.ASSET)
        val revenueAccount = sampleAccount("4000", AccountType.REVENUE)

        coEvery { accountRepo.findByCode("1100") } returns arAccount
        coEvery { accountRepo.findByCode("4000") } returns revenueAccount
        coEvery { invoiceRepo.save(any()) } returnsArgument 0
        coEvery { ledgerRepo.saveEntry(any()) } just Runs

        val result = useCase.execute(validRequest())

        assertEquals(InvoiceStatus.ISSUED, result.status)
        assertEquals(11200, result.totalAmount)  // 10000 + 1200 tax
        assertEquals(11200, result.balance)      // unpaid

        val savedEntry = slot<LedgerEntry>()
        coVerify { ledgerRepo.saveEntry(capture(savedEntry)) }

        val lines = savedEntry.captured.lines
        val drLine = lines.find { it.accountCode == "1100" }
        val crLine = lines.find { it.accountCode == "4000" }
        assertNotNull(drLine)
        assertNotNull(crLine)
        assertEquals(11200, drLine.debit)
        assertEquals(11200, crLine.credit)
    }

    @Test
    fun `throws when AR account 1100 is missing`() = runBlocking {
        coEvery { accountRepo.findByCode("1100") } returns null

        assertFailsWith<IllegalStateException> {
            useCase.execute(validRequest())
        }
    }

    @Test
    fun `throws when Revenue account 4000 is missing`() = runBlocking {
        coEvery { accountRepo.findByCode("1100") } returns sampleAccount("1100", AccountType.ASSET)
        coEvery { accountRepo.findByCode("4000") } returns null

        assertFailsWith<IllegalStateException> {
            useCase.execute(validRequest())
        }
    }

    @Test
    fun `invoice number is auto-generated`() = runBlocking {
        coEvery { accountRepo.findByCode(any()) } returns sampleAccount("1100", AccountType.ASSET)
        coEvery { invoiceRepo.save(any()) } returnsArgument 0
        coEvery { ledgerRepo.saveEntry(any()) } just Runs

        val result = useCase.execute(validRequest())
        assertTrue(result.invoiceNumber.startsWith("INV-"))
    }

    private fun validRequest() = InvoiceRequest(
        rentalId = "rental-001",
        customerId = "cust-001",
        subtotalCents = 10000,
        taxCents = 1200,
        dueDate = "2026-12-31T23:59:59Z"
    )

    private fun sampleAccount(code: String, type: AccountType) = Account(
        id = AccountId("acct-${code}"),
        code = code,
        name = if (type == AccountType.ASSET) "Accounts Receivable" else "Rental Revenue",
        type = type
    )
}
```

### PayInvoiceUseCase Tests
`src/test/kotlin/com/solodev/fleet/modules/accounts/application/usecases/PayInvoiceUseCaseTest.kt`

```kotlin
package com.solodev.fleet.modules.accounts.application.usecases

import com.solodev.fleet.modules.accounts.domain.model.*
import com.solodev.fleet.modules.accounts.domain.repository.*
import io.mockk.*
import kotlinx.coroutines.runBlocking
import java.time.Instant
import kotlin.test.*

class PayInvoiceUseCaseTest {

    private val invoiceRepo      = mockk<InvoiceRepository>()
    private val paymentRepo      = mockk<PaymentRepository>()
    private val accountRepo      = mockk<AccountRepository>()
    private val ledgerRepo       = mockk<LedgerRepository>()
    private val paymentMethodRepo = mockk<PaymentMethodRepository>()
    private val useCase = PayInvoiceUseCase(
        invoiceRepo, paymentRepo, accountRepo, ledgerRepo, paymentMethodRepo
    )

    @Test
    fun `partial payment keeps invoice as ISSUED`() = runBlocking {
        coEvery { invoiceRepo.findById(any()) } returns openInvoice(total = 11200, paid = 0)
        coEvery { paymentMethodRepo.findByCode("CASH") } returns samplePaymentMethod("1000")
        coEvery { accountRepo.findByCode("1000") } returns sampleAccount("1000")
        coEvery { accountRepo.findByCode("1100") } returns sampleAccount("1100")
        coEvery { paymentRepo.save(any()) } returnsArgument 0
        coEvery { invoiceRepo.save(any()) } returnsArgument 0
        coEvery { ledgerRepo.saveEntry(any()) } just Runs

        val result = useCase.execute("inv-001", amount = 5000, paymentMethod = "CASH")

        assertEquals(InvoiceStatus.ISSUED, result.status)
        assertEquals(5000, result.paidAmountCents)
    }

    @Test
    fun `full payment transitions invoice to PAID`() = runBlocking {
        coEvery { invoiceRepo.findById(any()) } returns openInvoice(total = 11200, paid = 0)
        coEvery { paymentMethodRepo.findByCode("CASH") } returns samplePaymentMethod("1000")
        coEvery { accountRepo.findByCode("1000") } returns sampleAccount("1000")
        coEvery { accountRepo.findByCode("1100") } returns sampleAccount("1100")
        coEvery { paymentRepo.save(any()) } returnsArgument 0
        coEvery { invoiceRepo.save(any()) } returnsArgument 0
        coEvery { ledgerRepo.saveEntry(any()) } just Runs

        val result = useCase.execute("inv-001", amount = 11200, paymentMethod = "CASH")

        assertEquals(InvoiceStatus.PAID, result.status)
    }

    @Test
    fun `throws overpayment when amount exceeds balance`() = runBlocking {
        coEvery { invoiceRepo.findById(any()) } returns openInvoice(total = 11200, paid = 0)

        val ex = assertFailsWith<IllegalArgumentException> {
            useCase.execute("inv-001", amount = 99999, paymentMethod = "CASH")
        }
        assertTrue(ex.message!!.contains("Overpayment", ignoreCase = true))
    }

    @Test
    fun `uses dynamic account from paymentMethod lookup`() = runBlocking {
        val gCashAccount = sampleAccount("1010")
        coEvery { invoiceRepo.findById(any()) } returns openInvoice(total = 11200, paid = 0)
        coEvery { paymentMethodRepo.findByCode("GCASH") } returns samplePaymentMethod("1010")
        coEvery { accountRepo.findByCode("1010") } returns gCashAccount
        coEvery { accountRepo.findByCode("1100") } returns sampleAccount("1100")
        coEvery { paymentRepo.save(any()) } returnsArgument 0
        coEvery { invoiceRepo.save(any()) } returnsArgument 0
        coEvery { ledgerRepo.saveEntry(any()) } just Runs

        useCase.execute("inv-001", amount = 11200, paymentMethod = "GCASH")

        coVerify { accountRepo.findByCode("1010") }
    }

    @Test
    fun `falls back to account 1000 when paymentMethod lookup returns null`() = runBlocking {
        coEvery { invoiceRepo.findById(any()) } returns openInvoice(total = 11200, paid = 0)
        coEvery { paymentMethodRepo.findByCode("UNKNOWN") } returns null
        coEvery { accountRepo.findByCode("1000") } returns sampleAccount("1000")
        coEvery { accountRepo.findByCode("1100") } returns sampleAccount("1100")
        coEvery { paymentRepo.save(any()) } returnsArgument 0
        coEvery { invoiceRepo.save(any()) } returnsArgument 0
        coEvery { ledgerRepo.saveEntry(any()) } just Runs

        val result = useCase.execute("inv-001", amount = 11200, paymentMethod = "UNKNOWN")

        assertEquals(InvoiceStatus.PAID, result.status)
        coVerify { accountRepo.findByCode("1000") }
    }

    @Test
    fun `posts DR asset account and CR AR 1100`() = runBlocking {
        coEvery { invoiceRepo.findById(any()) } returns openInvoice(total = 11200, paid = 0)
        coEvery { paymentMethodRepo.findByCode("CASH") } returns samplePaymentMethod("1000")
        coEvery { accountRepo.findByCode("1000") } returns sampleAccount("1000")
        coEvery { accountRepo.findByCode("1100") } returns sampleAccount("1100")
        coEvery { paymentRepo.save(any()) } returnsArgument 0
        coEvery { invoiceRepo.save(any()) } returnsArgument 0
        coEvery { ledgerRepo.saveEntry(any()) } just Runs

        useCase.execute("inv-001", amount = 11200, paymentMethod = "CASH")

        val entry = slot<LedgerEntry>()
        coVerify { ledgerRepo.saveEntry(capture(entry)) }
        val drLine = entry.captured.lines.find { it.accountCode == "1000" }
        val crLine = entry.captured.lines.find { it.accountCode == "1100" }
        assertNotNull(drLine); assertEquals(11200, drLine.debit)
        assertNotNull(crLine); assertEquals(11200, crLine.credit)
    }

    @Test
    fun `throws when invoice is not found`() = runBlocking {
        coEvery { invoiceRepo.findById(any()) } returns null

        assertFailsWith<IllegalArgumentException> {
            useCase.execute("missing-id", amount = 5000, paymentMethod = "CASH")
        }
    }

    private fun openInvoice(total: Int, paid: Int) = Invoice(
        id = InvoiceId("inv-001"),
        invoiceNumber = "INV-0001",
        rentalId = "rental-001",
        customerId = "cust-001",
        subtotalCents = total,
        taxCents = 0,
        paidAmountCents = paid,
        status = InvoiceStatus.ISSUED,
        dueDate = Instant.parse("2026-12-31T23:59:59Z")
    )

    private fun sampleAccount(code: String) = Account(
        id = AccountId("acct-$code"),
        code = code,
        name = "Account $code",
        type = AccountType.ASSET
    )

    private fun samplePaymentMethod(accountCode: String) = PaymentMethod(
        id = "pm-001",
        code = "CASH",
        name = "Cash",
        accountCode = accountCode
    )
}
```

---

## 3. HTTP Route Integration Tests

### Invoice Routes
`src/test/kotlin/com/solodev/fleet/modules/accounts/infrastructure/http/InvoiceRoutesTest.kt`

```kotlin
package com.solodev.fleet.modules.accounts.infrastructure.http

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.*

class InvoiceRoutesTest {

    // --- POST /v1/accounting/invoices ---

    @Test
    fun `POST invoices returns 201 with invoice body`() = testApplication {
        val response = client.post("/v1/accounting/invoices") {
            bearerAuth(TEST_JWT)
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "rentalId": "$RENTAL_ID",
                    "customerId": "$CUSTOMER_ID",
                    "subtotalCents": 10000,
                    "taxCents": 1200,
                    "dueDate": "2026-12-31T23:59:59Z"
                }
            """.trimIndent())
        }
        assertEquals(HttpStatusCode.Created, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("ISSUED"))
        assertTrue(body.contains("invoiceNumber"))
    }

    @Test
    fun `POST invoices returns 400 for missing required fields`() = testApplication {
        val response = client.post("/v1/accounting/invoices") {
            bearerAuth(TEST_JWT)
            contentType(ContentType.Application.Json)
            setBody("""{ "rentalId": "$RENTAL_ID" }""")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST invoices returns 401 without token`() = testApplication {
        val response = client.post("/v1/accounting/invoices") {
            contentType(ContentType.Application.Json)
            setBody("""{}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    // --- GET /v1/accounting/invoices ---

    @Test
    fun `GET invoices returns 200 list`() = testApplication {
        val response = client.get("/v1/accounting/invoices") {
            bearerAuth(TEST_JWT)
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("success"))
    }

    // --- POST /v1/accounting/invoices/{id}/pay ---

    @Test
    fun `POST pay returns 200 and updates invoice status`() = testApplication {
        val response = client.post("/v1/accounting/invoices/$INVOICE_ID/pay") {
            bearerAuth(TEST_JWT)
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "amount": 11200,
                    "paymentMethod": "CASH"
                }
            """.trimIndent())
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `POST pay returns 400 on overpayment`() = testApplication {
        val response = client.post("/v1/accounting/invoices/$INVOICE_ID/pay") {
            bearerAuth(TEST_JWT)
            contentType(ContentType.Application.Json)
            setBody("""{ "amount": 999999, "paymentMethod": "CASH" }""")
        }
        assertTrue(response.status.value in 400..500)
        assertTrue(response.bodyAsText().contains("Overpayment", ignoreCase = true))
    }

    companion object {
        const val TEST_JWT = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
        const val RENTAL_ID = "r0000001-0000-0000-0000-000000000001"
        const val CUSTOMER_ID = "c0000001-0000-0000-0000-000000000001"
        const val INVOICE_ID = "a0000001-0000-0000-0000-000000000001"
    }
}
```

### Payment & Payment Method Routes
`src/test/kotlin/com/solodev/fleet/modules/accounts/infrastructure/http/PaymentRoutesTest.kt`

```kotlin
package com.solodev.fleet.modules.accounts.infrastructure.http

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.*

class PaymentRoutesTest {

    @Test
    fun `GET accounting payments returns 200 list`() = testApplication {
        val response = client.get("/v1/accounting/payments") {
            bearerAuth(InvoiceRoutesTest.TEST_JWT)
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `GET payment methods returns 200 list`() = testApplication {
        val response = client.get("/v1/accounting/payment-methods") {
            bearerAuth(InvoiceRoutesTest.TEST_JWT)
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `POST payment methods creates new method`() = testApplication {
        val response = client.post("/v1/accounting/payment-methods") {
            bearerAuth(InvoiceRoutesTest.TEST_JWT)
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "code": "GCASH",
                    "name": "GCash",
                    "accountCode": "1010"
                }
            """.trimIndent())
        }
        assertEquals(HttpStatusCode.Created, response.status)
    }
}
```

### Chart of Accounts Routes
`src/test/kotlin/com/solodev/fleet/modules/accounts/infrastructure/http/AccountRoutesTest.kt`

```kotlin
package com.solodev.fleet.modules.accounts.infrastructure.http

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.*

class AccountRoutesTest {

    @Test
    fun `GET accounts returns 200 list`() = testApplication {
        val response = client.get("/v1/accounts") {
            bearerAuth(InvoiceRoutesTest.TEST_JWT)
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `POST accounts creates new account`() = testApplication {
        val response = client.post("/v1/accounts") {
            bearerAuth(InvoiceRoutesTest.TEST_JWT)
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "code": "1020",
                    "name": "Bank Account - BPI",
                    "type": "ASSET"
                }
            """.trimIndent())
        }
        assertEquals(HttpStatusCode.Created, response.status)
    }

    @Test
    fun `PUT accounts updates existing account`() = testApplication {
        val response = client.put("/v1/accounts/1020") {
            bearerAuth(InvoiceRoutesTest.TEST_JWT)
            contentType(ContentType.Application.Json)
            setBody("""{ "name": "Bank Account - BDO", "type": "ASSET" }""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `DELETE accounts removes account`() = testApplication {
        val response = client.delete("/v1/accounts/1020") {
            bearerAuth(InvoiceRoutesTest.TEST_JWT)
        }
        assertTrue(response.status.value in 200..204)
    }

    @Test
    fun `POST accounts returns 400 for unknown account type`() = testApplication {
        val response = client.post("/v1/accounts") {
            bearerAuth(InvoiceRoutesTest.TEST_JWT)
            contentType(ContentType.Application.Json)
            setBody("""{ "code": "9999", "name": "Bad", "type": "INVALID" }""")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }
}
```

### Financial Report Routes
`src/test/kotlin/com/solodev/fleet/modules/accounts/infrastructure/http/ReportRoutesTest.kt`

```kotlin
package com.solodev.fleet.modules.accounts.infrastructure.http

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.*

class ReportRoutesTest {

    @Test
    fun `GET revenue report returns 200`() = testApplication {
        val response = client.get("/v1/reports/revenue") {
            bearerAuth(InvoiceRoutesTest.TEST_JWT)
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `GET balance sheet returns 200`() = testApplication {
        val response = client.get("/v1/reports/balance-sheet") {
            bearerAuth(InvoiceRoutesTest.TEST_JWT)
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `GET reconciliation invoices returns 200`() = testApplication {
        val response = client.get("/v1/reconciliation/invoices") {
            bearerAuth(InvoiceRoutesTest.TEST_JWT)
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `GET reconciliation integrity check returns 200`() = testApplication {
        val response = client.get("/v1/reconciliation/integrity") {
            bearerAuth(InvoiceRoutesTest.TEST_JWT)
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `report routes return 401 without auth`() = testApplication {
        listOf(
            "/v1/reports/revenue",
            "/v1/reports/balance-sheet",
            "/v1/reconciliation/invoices",
            "/v1/reconciliation/integrity"
        ).forEach { path ->
            val response = client.get(path)
            assertEquals(HttpStatusCode.Unauthorized, response.status, "Expected 401 for $path")
        }
    }
}
```

---

## 4. Error Scenario Tests

```kotlin
package com.solodev.fleet.modules.accounts.infrastructure.http

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.*

class AccountingRoutesErrorTest {

    @Test
    fun `POST pay for missing invoice returns 404`() = testApplication {
        val response = client.post("/v1/accounting/invoices/00000000-0000-0000-0000-000000000000/pay") {
            bearerAuth(InvoiceRoutesTest.TEST_JWT)
            contentType(ContentType.Application.Json)
            setBody("""{ "amount": 100, "paymentMethod": "CASH" }""")
        }
        assertTrue(response.status.value in 400..500)
    }

    @Test
    fun `POST pay with zero amount returns 400`() = testApplication {
        val response = client.post("/v1/accounting/invoices/${InvoiceRoutesTest.INVOICE_ID}/pay") {
            bearerAuth(InvoiceRoutesTest.TEST_JWT)
            contentType(ContentType.Application.Json)
            setBody("""{ "amount": 0, "paymentMethod": "CASH" }""")
        }
        assertTrue(response.status.value in 400..500)
    }

    @Test
    fun `POST pay with negative amount returns 400`() = testApplication {
        val response = client.post("/v1/accounting/invoices/${InvoiceRoutesTest.INVOICE_ID}/pay") {
            bearerAuth(InvoiceRoutesTest.TEST_JWT)
            contentType(ContentType.Application.Json)
            setBody("""{ "amount": -100, "paymentMethod": "CASH" }""")
        }
        assertTrue(response.status.value in 400..500)
    }

    @Test
    fun `POST invoices with negative subtotal returns 400`() = testApplication {
        val response = client.post("/v1/accounting/invoices") {
            bearerAuth(InvoiceRoutesTest.TEST_JWT)
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "rentalId": "${InvoiceRoutesTest.RENTAL_ID}",
                    "customerId": "${InvoiceRoutesTest.CUSTOMER_ID}",
                    "subtotalCents": -5000,
                    "taxCents": 0,
                    "dueDate": "2026-12-31T23:59:59Z"
                }
            """.trimIndent())
        }
        assertTrue(response.status.value in 400..500)
    }
}
```

---

## 5. Test Summary

| Test Class | Layer | Coverage |
|---|---|---|
| `InvoiceTest` | Unit – Domain | `totalAmount = subtotal + tax`, `balance = totalAmount - paidAmount`, negative validation, `ISSUED` default status |
| `LedgerEntryTest` | Unit – Domain | Balanced DEBITs == CREDITs invariant, unbalanced detection |
| `IssueInvoiceUseCaseTest` | Unit – Use Case | DR 1100 / CR 4000 double-entry, invoice saved, missing account throws (1100, 4000), auto invoice number |
| `PayInvoiceUseCaseTest` | Unit – Use Case | Partial stays ISSUED, full → PAID, overpayment throws, dynamic account lookup, fallback to "1000", DR asset / CR AR, missing invoice throws |
| `InvoiceRoutesTest` | Integration – HTTP | POST 201, GET 200, POST pay 200, overpayment 400, auth enforcement |
| `PaymentRoutesTest` | Integration – HTTP | GET payments 200, GET/POST payment methods |
| `AccountRoutesTest` | Integration – HTTP | GET/POST/PUT/DELETE accounts, invalid type 400 |
| `ReportRoutesTest` | Integration – HTTP | Revenue, balance sheet, reconciliation invoices + integrity — all 200, all 401 without auth |
| `AccountingRoutesErrorTest` | Integration – Errors | Missing invoice, zero/negative amounts, negative subtotal |
