package com.solodev.fleet.modules.accounts.application

import com.solodev.fleet.modules.accounts.domain.model.LedgerEntry
import com.solodev.fleet.modules.accounts.domain.model.LedgerEntryId
import com.solodev.fleet.modules.accounts.domain.model.LedgerEntryLine
import com.solodev.fleet.modules.accounts.domain.repository.AccountRepository
import com.solodev.fleet.modules.accounts.domain.repository.LedgerRepository
import com.solodev.fleet.modules.rentals.domain.model.Rental
import java.time.Instant
import java.util.*

/**
 * Domain Service to handle standard financial postings. Ensures business events are translated
 * correctly to double-entry ledger entries.
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
            lines =
                listOf(
                    LedgerEntryLine(
                        id = UUID.randomUUID(),
                        entryId = entryId,
                        accountId = arAccount.id,
                        debitAmount = rental.totalAmount,
                        description = "Rental Receivable: ${rental.id.value}"
                    ),
                    LedgerEntryLine(
                        id = UUID.randomUUID(),
                        entryId = entryId,
                        accountId = revenueAccount.id,
                        creditAmount = rental.totalAmount,
                        description = "Rental Revenue: ${rental.id.value}"
                    )
                )
        )
        ledgerRepo.save(entry)
    }

    /** Records cash received and clears receivables when a payment is captured. */
    suspend fun postPaymentCapture(
        invoiceId: UUID,
        amount: Int,
        methodAccountCode: String,
        externalRef: String
    ) {
        val cashAccount = accountRepo.findByCode(methodAccountCode)
            ?: throw IllegalStateException("Payment Account $methodAccountCode missing")
        val arAccount = accountRepo.findByCode("1100") ?: throw IllegalStateException("AR Account 1100 missing")

        val entryId = LedgerEntryId(UUID.randomUUID().toString())
        val entry = LedgerEntry(
            id = entryId,
            entryNumber = "JE-PYMT-${System.currentTimeMillis()}",
            externalReference = externalRef,
            entryDate = Instant.now(),
            description = "Payment Captured for Invoice: $invoiceId",
            lines =
                listOf(
                    LedgerEntryLine(
                        id = UUID.randomUUID(),
                        entryId = entryId,
                        accountId = cashAccount.id,
                        debitAmount = amount,
                        description = "Cash Received"
                    ),
                    LedgerEntryLine(
                        id = UUID.randomUUID(),
                        entryId = entryId,
                        accountId = arAccount.id,
                        creditAmount = amount,
                        description = "Receivable Cleared"
                    )
                )
        )
        ledgerRepo.save(entry)
    }
}
