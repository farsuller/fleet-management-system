package com.solodev.fleet.modules.accounts.application

import com.solodev.fleet.modules.accounts.domain.model.*
import com.solodev.fleet.modules.accounts.domain.repository.AccountRepository
import com.solodev.fleet.modules.accounts.domain.repository.InvoiceRepository
import com.solodev.fleet.modules.accounts.domain.repository.LedgerRepository
import com.solodev.fleet.modules.rentals.domain.model.CustomerId
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class ReconciliationServiceTest {

    private val invoiceRepo = mockk<InvoiceRepository>()
    private val accountRepo = mockk<AccountRepository>()
    private val ledgerRepo = mockk<LedgerRepository>()
    private val service = ReconciliationService(invoiceRepo, accountRepo, ledgerRepo)

    private val arAccountId = AccountId("ar-account-1")
    private val arAccount = Account(
        id = arAccountId,
        accountCode = "1100",
        accountName = "Accounts Receivable",
        accountType = AccountType.ASSET
    )

    private fun buildInvoice(id: UUID, paidAmount: Int) = Invoice(
        id = id,
        invoiceNumber = "INV-${id.toString().take(8)}",
        customerId = CustomerId("cust-1"),
        status = InvoiceStatus.ISSUED,
        subtotal = 10000,
        paidAmount = paidAmount,
        issueDate = Instant.parse("2026-01-01T00:00:00Z"),
        dueDate = Instant.parse("2026-02-01T00:00:00Z")
    )

    @Test
    fun shouldReturnEmptyList_WhenInvoicePaidAmountMatchesLedger() = runBlocking {
        // Arrange
        val invoiceId = UUID.randomUUID()
        val invoice = buildInvoice(invoiceId, paidAmount = 10000)
        coEvery { invoiceRepo.findAll() } returns listOf(invoice)
        coEvery { accountRepo.findByCode("1100") } returns arAccount
        coEvery {
            ledgerRepo.calculateSumForPartialReference("invoice-$invoiceId-payment", arAccountId)
        } returns 10000L

        // Act
        val mismatches = service.verifyInvoices()

        // Assert
        assertThat(mismatches).isEmpty()
    }

    @Test
    fun shouldReturnMismatch_WhenPaidAmountDiffersFromLedger() = runBlocking {
        // Arrange
        val invoiceId = UUID.randomUUID()
        val invoice = buildInvoice(invoiceId, paidAmount = 5000)
        coEvery { invoiceRepo.findAll() } returns listOf(invoice)
        coEvery { accountRepo.findByCode("1100") } returns arAccount
        coEvery {
            ledgerRepo.calculateSumForPartialReference("invoice-$invoiceId-payment", arAccountId)
        } returns 3000L

        // Act
        val mismatches = service.verifyInvoices()

        // Assert
        assertThat(mismatches).hasSize(1)
        assertThat(mismatches[0].entityId).isEqualTo(invoiceId.toString())
        assertThat(mismatches[0].operationalValue).isEqualTo(5000L)
        assertThat(mismatches[0].ledgerValue).isEqualTo(3000L)
        assertThat(mismatches[0].type).isEqualTo("INVOICE_LEDGER_MISMATCH")
    }

    @Test
    fun shouldReturnEmptyList_WhenARAccountNotFound() = runBlocking {
        // Arrange
        coEvery { invoiceRepo.findAll() } returns listOf(buildInvoice(UUID.randomUUID(), 5000))
        coEvery { accountRepo.findByCode("1100") } returns null

        // Act
        val mismatches = service.verifyInvoices()

        // Assert
        assertThat(mismatches).isEmpty()
    }

    @Test
    fun shouldReturnTrue_WhenAccountingEquationHolds() = runBlocking {
        // Arrange — assets = 50000, liabilities + equity = 50000
        val assetAcc = Account(id = AccountId("a1"), accountCode = "1000", accountName = "Cash", accountType = AccountType.ASSET)
        val liabAcc = Account(id = AccountId("a2"), accountCode = "2000", accountName = "Payables", accountType = AccountType.LIABILITY)
        val equityAcc = Account(id = AccountId("a3"), accountCode = "3000", accountName = "Equity", accountType = AccountType.EQUITY)
        coEvery { accountRepo.findAll() } returns listOf(assetAcc, liabAcc, equityAcc)
        coEvery { ledgerRepo.calculateAccountBalance(AccountId("a1"), any()) } returns 50000L
        coEvery { ledgerRepo.calculateAccountBalance(AccountId("a2"), any()) } returns 20000L
        coEvery { ledgerRepo.calculateAccountBalance(AccountId("a3"), any()) } returns 30000L

        // Act
        val isBalanced = service.verifyAccountingEquation()

        // Assert
        assertThat(isBalanced).isTrue()
    }

    @Test
    fun shouldReturnFalse_WhenAccountingEquationDoesNotHold() = runBlocking {
        // Arrange — assets(50000) != liabilities(10000) + equity(10000)
        val assetAcc = Account(id = AccountId("a1"), accountCode = "1000", accountName = "Cash", accountType = AccountType.ASSET)
        val liabAcc = Account(id = AccountId("a2"), accountCode = "2000", accountName = "Payables", accountType = AccountType.LIABILITY)
        coEvery { accountRepo.findAll() } returns listOf(assetAcc, liabAcc)
        coEvery { ledgerRepo.calculateAccountBalance(AccountId("a1"), any()) } returns 50000L
        coEvery { ledgerRepo.calculateAccountBalance(AccountId("a2"), any()) } returns 10000L

        // Act
        val isBalanced = service.verifyAccountingEquation()

        // Assert
        assertThat(isBalanced).isFalse()
    }
}
