package com.solodev.fleet.modules.accounts.application.usecases

import com.solodev.fleet.modules.accounts.application.dto.DriverCollectionRequest
import com.solodev.fleet.modules.accounts.domain.model.*
import com.solodev.fleet.modules.accounts.domain.repository.InvoiceRepository
import com.solodev.fleet.modules.accounts.domain.repository.PaymentRepository
import com.solodev.fleet.modules.rentals.domain.model.CustomerId
import io.mockk.*
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class RecordDriverCollectionUseCaseTest {

    private val invoiceRepository = mockk<InvoiceRepository>()
    private val paymentRepository = mockk<PaymentRepository>()
    private val useCase = RecordDriverCollectionUseCase(invoiceRepository, paymentRepository)

    private val customerId = "cust-001"
    private val driverId = UUID.randomUUID().toString()
    private val invoiceId = UUID.randomUUID()

    @Test
    fun shouldCreatePendingDriverCollectedPayment_WhenRequestIsValid() = runBlocking {
        // Arrange
        val invoice = sampleInvoice(status = InvoiceStatus.ISSUED, paidAmount = 0)
        coEvery { invoiceRepository.findById(invoiceId) } returns invoice
        coEvery { paymentRepository.save(any()) } returnsArgument 0

        val request = DriverCollectionRequest(
            driverId = driverId,
            customerId = customerId,
            invoiceId = invoiceId.toString(),
            amount = 5000,
            paymentMethod = "CASH",
            transactionReference = null,
            collectedAt = Instant.now().toString()
        )

        // Act
        val result = useCase.execute(request)

        // Assert
        assertThat(result.status).isEqualTo(PaymentStatus.PENDING)
        assertThat(result.collectionType).isEqualTo(PaymentCollectionType.DRIVER_COLLECTED)
        assertThat(result.driverId).isEqualTo(UUID.fromString(driverId))
        assertThat(result.amount).isEqualTo(5000)
        coVerify(exactly = 1) { paymentRepository.save(any()) }
    }

    @Test
    fun shouldThrowIllegalArgument_WhenInvoiceNotFound() {
        // Arrange
        coEvery { invoiceRepository.findById(invoiceId) } returns null

        val request = sampleRequest()

        // Act / Assert
        assertThatThrownBy { runBlocking { useCase.execute(request) } }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Invoice not found")
    }

    @Test
    fun shouldThrowIllegalArgument_WhenInvoiceBelongsToDifferentCustomer() {
        // Arrange
        val invoice = sampleInvoice(customerId = "other-customer")
        coEvery { invoiceRepository.findById(invoiceId) } returns invoice

        val request = sampleRequest(customerId = "cust-001")

        // Act / Assert
        assertThatThrownBy { runBlocking { useCase.execute(request) } }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("does not belong to customer")
    }

    @Test
    fun shouldThrowIllegalArgument_WhenInvoiceIsPaid() {
        // Arrange
        val invoice = sampleInvoice(status = InvoiceStatus.PAID)
        coEvery { invoiceRepository.findById(invoiceId) } returns invoice

        val request = sampleRequest()

        // Act / Assert
        assertThatThrownBy { runBlocking { useCase.execute(request) } }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("ISSUED or OVERDUE")
    }

    @Test
    fun shouldThrowIllegalArgument_WhenAmountExceedsBalance() {
        // Arrange
        val invoice = sampleInvoice(status = InvoiceStatus.ISSUED, subtotal = 5000, paidAmount = 3000)
        coEvery { invoiceRepository.findById(invoiceId) } returns invoice

        // balance = 5000 - 3000 = 2000; requesting 3000 should fail
        val request = sampleRequest(amount = 3000)

        // Act / Assert
        assertThatThrownBy { runBlocking { useCase.execute(request) } }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("exceeds outstanding invoice balance")
    }

    @Test
    fun shouldThrowIllegalArgument_WhenAmountIsZeroOrNegative() {
        // Arrange
        val invoice = sampleInvoice(status = InvoiceStatus.ISSUED)
        coEvery { invoiceRepository.findById(invoiceId) } returns invoice

        val request = sampleRequest(amount = 0)

        // Act / Assert
        assertThatThrownBy { runBlocking { useCase.execute(request) } }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("must be positive")
    }

    // ---- helpers ----

    private fun sampleRequest(
        customerId: String = this.customerId,
        amount: Int = 5000
    ) = DriverCollectionRequest(
        driverId = driverId,
        customerId = customerId,
        invoiceId = invoiceId.toString(),
        amount = amount,
        paymentMethod = "CASH",
        transactionReference = null,
        collectedAt = Instant.now().toString()
    )

    private fun sampleInvoice(
        customerId: String = this.customerId,
        status: InvoiceStatus = InvoiceStatus.ISSUED,
        subtotal: Int = 10000,
        paidAmount: Int = 0
    ) = Invoice(
        id = invoiceId,
        invoiceNumber = "INV-TEST-001",
        customerId = CustomerId(customerId),
        status = status,
        subtotal = subtotal,
        tax = 0,
        paidAmount = paidAmount,
        issueDate = Instant.now().minus(1, ChronoUnit.DAYS),
        dueDate = Instant.now().plus(29, ChronoUnit.DAYS)
    )
}
