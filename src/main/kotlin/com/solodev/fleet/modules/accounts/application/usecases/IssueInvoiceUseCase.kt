package com.solodev.fleet.modules.accounts.application.usecases

import com.solodev.fleet.modules.accounts.application.dto.InvoiceRequest
import com.solodev.fleet.modules.accounts.domain.model.*
import com.solodev.fleet.modules.accounts.domain.repository.AccountRepository
import com.solodev.fleet.modules.accounts.domain.repository.InvoiceRepository
import com.solodev.fleet.modules.accounts.domain.repository.LedgerRepository
import com.solodev.fleet.modules.rentals.domain.model.CustomerId
import com.solodev.fleet.modules.rentals.domain.model.RentalId
import java.time.Instant
import java.util.UUID

class IssueInvoiceUseCase(
        private val repository: InvoiceRepository,
        private val accountRepo: AccountRepository,
        private val ledgerRepo: LedgerRepository
) {
    suspend fun execute(request: InvoiceRequest): Invoice {
        val invoice =
                Invoice(
                        id = UUID.randomUUID(),
                        invoiceNumber = "INV-${System.currentTimeMillis()}",
                        customerId = CustomerId(request.customerId),
                        rentalId = request.rentalId?.let { RentalId(it) },
                        status = InvoiceStatus.ISSUED,
                        subtotalCents = (request.subtotal * 100).toInt(),
                        taxCents = (request.tax * 100).toInt(),
                        paidCents = 0,
                        issueDate = Instant.now(),
                        dueDate = Instant.parse(request.dueDate)
                )
        val savedInvoice = repository.save(invoice)

        // Post to General Ledger
        // When an invoice is issued:
        // Debit (Increase) Accounts Receivable (Account 1100)
        // Credit (Increase) Rental Revenue (Account 4000)

        val arAccount =
                accountRepo.findByCode("1100")
                        ?: throw IllegalStateException(
                                "Accounts Receivable account (1100) not found"
                        )
        val revenueAccount =
                accountRepo.findByCode("4000")
                        ?: throw IllegalStateException("Rental Revenue account (4000) not found")

        val entryDate = Instant.now()
        val entryId = LedgerEntryId(UUID.randomUUID().toString())

        val ledgerEntry =
                LedgerEntry(
                        id = entryId,
                        entryNumber = "JE-${System.currentTimeMillis()}",
                        externalReference = "invoice-${savedInvoice.invoiceNumber}",
                        entryDate = entryDate,
                        description = "Invoice issued: ${savedInvoice.invoiceNumber}",
                        lines =
                                listOf(
                                        LedgerEntryLine(
                                                id = UUID.randomUUID(),
                                                entryId = entryId,
                                                accountId = arAccount.id,
                                                debitAmountCents = savedInvoice.totalCents,
                                                creditAmountCents = 0,
                                                description =
                                                        "Receivable for ${savedInvoice.invoiceNumber}"
                                        ),
                                        LedgerEntryLine(
                                                id = UUID.randomUUID(),
                                                entryId = entryId,
                                                accountId = revenueAccount.id,
                                                debitAmountCents = 0,
                                                creditAmountCents = savedInvoice.totalCents,
                                                description =
                                                        "Revenue for ${savedInvoice.invoiceNumber}"
                                        )
                                )
                )
        ledgerRepo.save(ledgerEntry)

        return savedInvoice
    }
}
