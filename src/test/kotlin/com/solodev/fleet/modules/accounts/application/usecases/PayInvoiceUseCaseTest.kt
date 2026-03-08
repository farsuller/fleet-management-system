package com.solodev.fleet.modules.accounts.application.usecases

import com.solodev.fleet.modules.accounts.domain.model.*
import com.solodev.fleet.modules.accounts.domain.repository.*
import com.solodev.fleet.modules.rentals.domain.model.CustomerId
import io.mockk.*
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class PayInvoiceUseCaseTest {

    private val invoiceRepo = mockk<InvoiceRepository>()
    private val paymentRepo = mockk<PaymentRepository>()
    private val accountRepo = mockk<AccountRepository>()
    private val ledgerRepo = mockk<LedgerRepository>()
    private val paymentMethodRepo = mockk<PaymentMethodRepository>()
    private val useCase = PayInvoiceUseCase(invoiceRepo, paymentRepo, accountRepo, ledgerRepo, paymentMethodRepo)

    @Test
    fun shouldKeepIssuedStatus_WhenPaymentIsPartial() = runBlocking {
        // Arrange
        val invoice = sampleInvoice(subtotal = 10000, tax = 1200, status = InvoiceStatus.ISSUED)
        coEvery { invoiceRepo.findById(invoice.id) } returns invoice
        coEvery { paymentMethodRepo.findByCode("CASH") } returns null
        coEvery { accountRepo.findByCode("1000") } returns sampleAccount("1000", AccountType.ASSET)
        coEvery { accountRepo.findByCode("1100") } returns sampleAccount("1100", AccountType.ASSET)
        coEvery { paymentRepo.save(any()) } returnsArgument 0
        coEvery { invoiceRepo.save(any()) } returnsArgument 0
        coEvery { ledgerRepo.save(any()) } returnsArgument 0

        // Act
        val result = useCase.execute(invoice.id.toString(), 5000, "CASH")

        // Assert
        assertThat(result.updatedInvoice.status).isEqualTo(InvoiceStatus.ISSUED)
        assertThat(result.updatedInvoice.balance).isEqualTo(6200)
    }

    @Test
    fun shouldTransitionToPaid_WhenPaymentIsFull() = runBlocking {
        // Arrange
        val invoice = sampleInvoice(subtotal = 10000, tax = 1200, status = InvoiceStatus.ISSUED)
        coEvery { invoiceRepo.findById(invoice.id) } returns invoice
        coEvery { paymentMethodRepo.findByCode("CASH") } returns null
        coEvery { accountRepo.findByCode("1000") } returns sampleAccount("1000", AccountType.ASSET)
        coEvery { accountRepo.findByCode("1100") } returns sampleAccount("1100", AccountType.ASSET)
        coEvery { paymentRepo.save(any()) } returnsArgument 0
        coEvery { invoiceRepo.save(any()) } returnsArgument 0
        coEvery { ledgerRepo.save(any()) } returnsArgument 0

        // Act
        val result = useCase.execute(invoice.id.toString(), 11200, "CASH")

        // Assert
        assertThat(result.updatedInvoice.status).isEqualTo(InvoiceStatus.PAID)
        assertThat(result.updatedInvoice.balance).isEqualTo(0)
    }

    @Test
    fun shouldThrowIllegalArgument_WhenPaymentExceedsBalance() {
        // Arrange
        val invoice = sampleInvoice(subtotal = 10000, tax = 1200, status = InvoiceStatus.ISSUED)
        coEvery { invoiceRepo.findById(invoice.id) } returns invoice

        // Act / Assert
        assertThatThrownBy { runBlocking { useCase.execute(invoice.id.toString(), 15000, "CASH") } }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun shouldThrowIllegalArgument_WhenInvoiceNotFound() {
        // Arrange
        val missingId = UUID.randomUUID()
        coEvery { invoiceRepo.findById(missingId) } returns null

        // Act / Assert
        assertThatThrownBy { runBlocking { useCase.execute(missingId.toString(), 5000, "CASH") } }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    private fun sampleInvoice(
        subtotal: Int = 10000,
        tax: Int = 1200,
        status: InvoiceStatus = InvoiceStatus.ISSUED
    ) = Invoice(
        id = UUID.randomUUID(),
        invoiceNumber = "INV-2026-001",
        customerId = CustomerId("cust-001"),
        subtotal = subtotal,
        tax = tax,
        paidAmount = 0,
        status = status,
        issueDate = Instant.now(),
        dueDate = Instant.now().plusSeconds(86400L * 30)
    )

    private fun sampleAccount(code: String, type: AccountType) = Account(
        id = AccountId(code),
        accountCode = code,
        accountName = "Account $code",
        accountType = type
    )
}

