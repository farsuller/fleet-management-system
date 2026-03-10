package com.solodev.fleet.modules.accounts.application.usecases

import com.solodev.fleet.modules.accounts.application.dto.DriverRemittanceRequest
import com.solodev.fleet.modules.accounts.domain.model.*
import com.solodev.fleet.modules.accounts.domain.repository.*
import com.solodev.fleet.modules.rentals.domain.model.CustomerId
import io.mockk.*
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class RecordDriverRemittanceUseCaseTest {

    private val paymentRepository = mockk<PaymentRepository>()
    private val invoiceRepository = mockk<InvoiceRepository>()
    private val accountRepository = mockk<AccountRepository>()
    private val ledgerRepository = mockk<LedgerRepository>()
    private val paymentMethodRepository = mockk<PaymentMethodRepository>()
    private val remittanceRepository = mockk<DriverRemittanceRepository>()

    private val useCase = RecordDriverRemittanceUseCase(
        paymentRepository,
        invoiceRepository,
        accountRepository,
        ledgerRepository,
        paymentMethodRepository,
        remittanceRepository
    )

    private val driverId = UUID.randomUUID()
    private val invoiceId = UUID.randomUUID()

    @Test
    fun shouldCreateSubmittedRemittanceAndPostGL_WhenRequestIsValid() = runBlocking {
        // Arrange
        val paymentId = UUID.randomUUID()
        val payment = samplePayment(id = paymentId)
        val invoice = sampleInvoice(subtotal = 5000, paidAmount = 0)
        val arAccount = sampleAccount("1100", AccountType.ASSET)
        val cashAccount = sampleAccount("1000", AccountType.ASSET)
        val paymentMethod = PaymentMethod(code = "CASH", displayName = "Cash", targetAccountCode = "1000")

        coEvery { paymentRepository.findByIds(listOf(paymentId)) } returns listOf(payment)
        coEvery { remittanceRepository.save(any()) } returnsArgument 0
        coEvery { accountRepository.findByCode("1100") } returns arAccount
        coEvery { paymentMethodRepository.findByCode("CASH") } returns paymentMethod
        coEvery { accountRepository.findByCode("1000") } returns cashAccount
        coEvery { paymentRepository.save(any()) } returnsArgument 0
        coEvery { invoiceRepository.findById(invoiceId) } returns invoice
        coEvery { invoiceRepository.save(any()) } returnsArgument 0
        coEvery { ledgerRepository.save(any()) } returnsArgument 0

        val request = DriverRemittanceRequest(
            driverId = driverId.toString(),
            paymentIds = listOf(paymentId.toString()),
            remittanceDate = Instant.now().toString(),
            notes = "Test remittance"
        )

        // Act
        val result = useCase.execute(request)

        // Assert
        assertThat(result.status).isEqualTo(RemittanceStatus.SUBMITTED)
        assertThat(result.driverId).isEqualTo(driverId)
        assertThat(result.totalAmount).isEqualTo(5000)
        assertThat(result.paymentIds).containsExactly(paymentId)

        // GL posted once per payment
        coVerify(exactly = 1) { ledgerRepository.save(any()) }
        // Payment marked COMPLETED
        val savedPayment = slot<Payment>()
        coVerify { paymentRepository.save(capture(savedPayment)) }
        assertThat(savedPayment.captured.status).isEqualTo(PaymentStatus.COMPLETED)
    }

    @Test
    fun shouldMarkInvoicePaid_WhenPaymentFullySettlesInvoice() = runBlocking {
        // Arrange
        val paymentId = UUID.randomUUID()
        val payment = samplePayment(id = paymentId, amount = 5000)
        val invoice = sampleInvoice(subtotal = 5000, paidAmount = 0) // balance = 5000

        setupHappyPathMocks(paymentId, payment, invoice)

        val request = DriverRemittanceRequest(
            driverId = driverId.toString(),
            paymentIds = listOf(paymentId.toString()),
            remittanceDate = Instant.now().toString()
        )

        // Act
        useCase.execute(request)

        // Assert: invoice saved with PAID status
        val savedInvoice = slot<Invoice>()
        coVerify { invoiceRepository.save(capture(savedInvoice)) }
        assertThat(savedInvoice.captured.status).isEqualTo(InvoiceStatus.PAID)
        assertThat(savedInvoice.captured.paidDate).isNotNull()
    }

    @Test
    fun shouldKeepInvoiceIssued_WhenPaymentPartiallySettlesInvoice() = runBlocking {
        // Arrange
        val paymentId = UUID.randomUUID()
        val payment = samplePayment(id = paymentId, amount = 2000)
        val invoice = sampleInvoice(subtotal = 5000, paidAmount = 0) // balance = 5000; paying 2000 = partial

        setupHappyPathMocks(paymentId, payment, invoice)

        val request = DriverRemittanceRequest(
            driverId = driverId.toString(),
            paymentIds = listOf(paymentId.toString()),
            remittanceDate = Instant.now().toString()
        )

        // Act
        useCase.execute(request)

        // Assert: invoice stays ISSUED
        val savedInvoice = slot<Invoice>()
        coVerify { invoiceRepository.save(capture(savedInvoice)) }
        assertThat(savedInvoice.captured.status).isEqualTo(InvoiceStatus.ISSUED)
        assertThat(savedInvoice.captured.paidDate).isNull()
        assertThat(savedInvoice.captured.paidAmount).isEqualTo(2000)
    }

    @Test
    fun shouldThrowIllegalArgument_WhenPaymentNotFound() {
        // Arrange
        val paymentId = UUID.randomUUID()
        coEvery { paymentRepository.findByIds(listOf(paymentId)) } returns emptyList()

        val request = DriverRemittanceRequest(
            driverId = driverId.toString(),
            paymentIds = listOf(paymentId.toString()),
            remittanceDate = Instant.now().toString()
        )

        // Act / Assert
        assertThatThrownBy { runBlocking { useCase.execute(request) } }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("not found")
    }

    @Test
    fun shouldThrowIllegalArgument_WhenPaymentBelongsToDifferentDriver() {
        // Arrange
        val paymentId = UUID.randomUUID()
        val differentDriverId = UUID.randomUUID()
        val payment = samplePayment(id = paymentId, driverId = differentDriverId) // different driver
        coEvery { paymentRepository.findByIds(listOf(paymentId)) } returns listOf(payment)

        val request = DriverRemittanceRequest(
            driverId = driverId.toString(),
            paymentIds = listOf(paymentId.toString()),
            remittanceDate = Instant.now().toString()
        )

        // Act / Assert
        assertThatThrownBy { runBlocking { useCase.execute(request) } }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("must belong to driver")
    }

    @Test
    fun shouldThrowIllegalArgument_WhenPaymentIsNotPending() {
        // Arrange
        val paymentId = UUID.randomUUID()
        val payment = samplePayment(id = paymentId, status = PaymentStatus.COMPLETED)
        coEvery { paymentRepository.findByIds(listOf(paymentId)) } returns listOf(payment)

        val request = DriverRemittanceRequest(
            driverId = driverId.toString(),
            paymentIds = listOf(paymentId.toString()),
            remittanceDate = Instant.now().toString()
        )

        // Act / Assert
        assertThatThrownBy { runBlocking { useCase.execute(request) } }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("PENDING status")
    }

    @Test
    fun shouldThrowIllegalArgument_WhenPaymentIsNotDriverCollected() {
        // Arrange
        val paymentId = UUID.randomUUID()
        val payment = samplePayment(id = paymentId, collectionType = PaymentCollectionType.DIRECT)
        coEvery { paymentRepository.findByIds(listOf(paymentId)) } returns listOf(payment)

        val request = DriverRemittanceRequest(
            driverId = driverId.toString(),
            paymentIds = listOf(paymentId.toString()),
            remittanceDate = Instant.now().toString()
        )

        // Act / Assert
        assertThatThrownBy { runBlocking { useCase.execute(request) } }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("DRIVER_COLLECTED")
    }

    // ---- helpers ----

    private fun setupHappyPathMocks(paymentId: UUID, payment: Payment, invoice: Invoice) {
        val arAccount = sampleAccount("1100", AccountType.ASSET)
        val cashAccount = sampleAccount("1000", AccountType.ASSET)
        val paymentMethod = PaymentMethod(code = "CASH", displayName = "Cash", targetAccountCode = "1000")
        coEvery { paymentRepository.findByIds(listOf(paymentId)) } returns listOf(payment)
        coEvery { remittanceRepository.save(any()) } returnsArgument 0
        coEvery { accountRepository.findByCode("1100") } returns arAccount
        coEvery { paymentMethodRepository.findByCode("CASH") } returns paymentMethod
        coEvery { accountRepository.findByCode("1000") } returns cashAccount
        coEvery { paymentRepository.save(any()) } returnsArgument 0
        coEvery { invoiceRepository.findById(invoiceId) } returns invoice
        coEvery { invoiceRepository.save(any()) } returnsArgument 0
        coEvery { ledgerRepository.save(any()) } returnsArgument 0
    }

    private fun samplePayment(
        id: UUID = UUID.randomUUID(),
        driverId: UUID = this.driverId,
        amount: Int = 5000,
        status: PaymentStatus = PaymentStatus.PENDING,
        collectionType: PaymentCollectionType = PaymentCollectionType.DRIVER_COLLECTED
    ) = Payment(
        id = id,
        paymentNumber = "PAY-DRV-001",
        customerId = CustomerId("cust-001"),
        invoiceId = invoiceId,
        driverId = driverId,
        amount = amount,
        paymentMethod = "CASH",
        status = status,
        paymentDate = Instant.now(),
        collectionType = collectionType
    )

    private fun sampleInvoice(subtotal: Int = 5000, paidAmount: Int = 0) = Invoice(
        id = invoiceId,
        invoiceNumber = "INV-TEST-001",
        customerId = CustomerId("cust-001"),
        status = InvoiceStatus.ISSUED,
        subtotal = subtotal,
        tax = 0,
        paidAmount = paidAmount,
        issueDate = Instant.now().minus(1, ChronoUnit.DAYS),
        dueDate = Instant.now().plus(29, ChronoUnit.DAYS)
    )

    private fun sampleAccount(code: String, type: AccountType) = Account(
        id = AccountId(code),
        accountCode = code,
        accountName = "Account $code",
        accountType = type
    )
}
