package com.solodev.fleet.modules.accounts.application.usecases

import com.solodev.fleet.modules.accounts.domain.model.InvoiceStatus
import com.solodev.fleet.modules.accounts.domain.repository.InvoiceRepository
import com.solodev.fleet.modules.rentals.domain.model.Customer
import com.solodev.fleet.modules.rentals.domain.model.CustomerId
import com.solodev.fleet.modules.rentals.domain.model.RentalId
import com.solodev.fleet.modules.rentals.domain.repository.CustomerRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

class GenerateArAgingUseCaseTest {
    private val invoiceRepo = mockk<InvoiceRepository>()
    private val customerRepo = mockk<CustomerRepository>()
    private val useCase = GenerateArAgingUseCase(invoiceRepo, customerRepo)

    private val customerId = CustomerId(UUID.randomUUID().toString())

    private val customer =
        Customer(
            id = customerId,
            firstName = "Juan",
            lastName = "dela Cruz",
            email = "juan@example.com",
            phone = "09171234567",
            driverLicenseNumber = "DL-001",
            driverLicenseExpiry = Instant.now().plusSeconds(86400 * 365),
        )

    private fun invoice(
        dueDate: Instant,
        status: InvoiceStatus = InvoiceStatus.ISSUED,
        subtotal: Int = 10000,
        paidAmount: Int = 0,
    ) = com.solodev.fleet.modules.accounts.domain.model.Invoice(
        id = UUID.randomUUID(),
        invoiceNumber = "INV-${UUID.randomUUID()}",
        customerId = customerId,
        rentalId = RentalId(UUID.randomUUID().toString()),
        status = status,
        subtotal = subtotal,
        paidAmount = paidAmount,
        issueDate = dueDate.minus(30, ChronoUnit.DAYS),
        dueDate = dueDate,
    )

    @Test
    fun `should return empty report when no open invoices exist`() =
        runBlocking {
            coEvery { invoiceRepo.findAll() } returns emptyList()

            val result = useCase.arAging(Instant.now())

            assertThat(result.rows).isEmpty()
            assertThat(result.grandTotal).isEqualTo(0L)
            assertThat(result.totalBucket0to30).isEqualTo(0L)
        }

    @Test
    fun `should bucket invoice in 0-30 days when not yet overdue`() =
        runBlocking {
            val asOf = Instant.parse("2026-04-15T00:00:00Z")
            // due date is today → 0 days overdue → bucket 0–30
            val inv = invoice(dueDate = asOf)
            coEvery { invoiceRepo.findAll() } returns listOf(inv)
            coEvery { customerRepo.findById(customerId) } returns customer

            val result = useCase.arAging(asOf)

            assertThat(result.rows).hasSize(1)
            assertThat(result.rows[0].bucket0to30).isEqualTo(10000L)
            assertThat(result.rows[0].bucket31to60).isEqualTo(0L)
            assertThat(result.rows[0].bucket61to90).isEqualTo(0L)
            assertThat(result.rows[0].bucket91plus).isEqualTo(0L)
            assertThat(result.rows[0].customerName).isEqualTo("Juan dela Cruz")
            assertThat(result.totalBucket0to30).isEqualTo(10000L)
        }

    @Test
    fun `should bucket invoice in 31-60 days when 45 days overdue`() =
        runBlocking {
            val asOf = Instant.parse("2026-04-15T00:00:00Z")
            val inv = invoice(dueDate = asOf.minus(45, ChronoUnit.DAYS))
            coEvery { invoiceRepo.findAll() } returns listOf(inv)
            coEvery { customerRepo.findById(customerId) } returns customer

            val result = useCase.arAging(asOf)

            assertThat(result.rows[0].bucket31to60).isEqualTo(10000L)
            assertThat(result.totalBucket31to60).isEqualTo(10000L)
        }

    @Test
    fun `should bucket invoice in 61-90 days when 75 days overdue`() =
        runBlocking {
            val asOf = Instant.parse("2026-04-15T00:00:00Z")
            val inv = invoice(dueDate = asOf.minus(75, ChronoUnit.DAYS))
            coEvery { invoiceRepo.findAll() } returns listOf(inv)
            coEvery { customerRepo.findById(customerId) } returns customer

            val result = useCase.arAging(asOf)

            assertThat(result.rows[0].bucket61to90).isEqualTo(10000L)
            assertThat(result.totalBucket61to90).isEqualTo(10000L)
        }

    @Test
    fun `should bucket invoice in 91+ days when 120 days overdue`() =
        runBlocking {
            val asOf = Instant.parse("2026-04-15T00:00:00Z")
            val inv = invoice(dueDate = asOf.minus(120, ChronoUnit.DAYS))
            coEvery { invoiceRepo.findAll() } returns listOf(inv)
            coEvery { customerRepo.findById(customerId) } returns customer

            val result = useCase.arAging(asOf)

            assertThat(result.rows[0].bucket91plus).isEqualTo(10000L)
            assertThat(result.totalBucket91plus).isEqualTo(10000L)
        }

    @Test
    fun `should exclude invoices that are fully paid`() =
        runBlocking {
            val asOf = Instant.parse("2026-04-15T00:00:00Z")
            // balance = 0 (paid in full) → should be excluded
            val paidInv = invoice(dueDate = asOf, subtotal = 5000, paidAmount = 5000)
            coEvery { invoiceRepo.findAll() } returns listOf(paidInv)

            val result = useCase.arAging(asOf)

            assertThat(result.rows).isEmpty()
            assertThat(result.grandTotal).isEqualTo(0L)
        }

    @Test
    fun `should exclude invoices with non-open status`() =
        runBlocking {
            val asOf = Instant.parse("2026-04-15T00:00:00Z")
            val paidInv = invoice(dueDate = asOf, status = InvoiceStatus.PAID)
            val draftInv = invoice(dueDate = asOf, status = InvoiceStatus.DRAFT)
            val cancelledInv = invoice(dueDate = asOf, status = InvoiceStatus.CANCELLED)
            coEvery { invoiceRepo.findAll() } returns listOf(paidInv, draftInv, cancelledInv)

            val result = useCase.arAging(asOf)

            assertThat(result.rows).isEmpty()
        }

    @Test
    fun `should use Unknown when customer is not found`() =
        runBlocking {
            val asOf = Instant.parse("2026-04-15T00:00:00Z")
            val inv = invoice(dueDate = asOf)
            coEvery { invoiceRepo.findAll() } returns listOf(inv)
            coEvery { customerRepo.findById(customerId) } returns null

            val result = useCase.arAging(asOf)

            assertThat(result.rows[0].customerName).isEqualTo("Unknown")
        }

    @Test
    fun `should sort rows by total descending`() =
        runBlocking {
            val asOf = Instant.parse("2026-04-15T00:00:00Z")
            val customerId2 = CustomerId(UUID.randomUUID().toString())
            val customer2 = customer.copy(id = customerId2, firstName = "Maria", lastName = "Santos")

            val invSmall =
                com.solodev.fleet.modules.accounts.domain.model.Invoice(
                    id = UUID.randomUUID(),
                    invoiceNumber = "INV-001",
                    customerId = customerId,
                    status = InvoiceStatus.ISSUED,
                    subtotal = 1000,
                    issueDate = asOf.minus(35, ChronoUnit.DAYS),
                    dueDate = asOf,
                )
            val invLarge =
                com.solodev.fleet.modules.accounts.domain.model.Invoice(
                    id = UUID.randomUUID(),
                    invoiceNumber = "INV-002",
                    customerId = customerId2,
                    status = InvoiceStatus.ISSUED,
                    subtotal = 50000,
                    issueDate = asOf.minus(35, ChronoUnit.DAYS),
                    dueDate = asOf,
                )

            coEvery { invoiceRepo.findAll() } returns listOf(invSmall, invLarge)
            coEvery { customerRepo.findById(customerId) } returns customer
            coEvery { customerRepo.findById(customerId2) } returns customer2

            val result = useCase.arAging(asOf)

            // larger total should come first
            assertThat(result.rows[0].total).isGreaterThan(result.rows[1].total)
            assertThat(result.grandTotal).isEqualTo(51000L)
        }
}
