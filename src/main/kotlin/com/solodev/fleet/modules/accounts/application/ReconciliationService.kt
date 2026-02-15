package com.solodev.fleet.modules.accounts.application

import com.solodev.fleet.modules.accounts.domain.model.AccountType
import com.solodev.fleet.modules.accounts.domain.repository.*
import kotlinx.serialization.Serializable

@Serializable
data class DataMismatch(
        val entityId: String,
        val operationalValue: Long,
        val ledgerValue: Long,
        val type: String
)

class ReconciliationService(
        private val invoiceRepo: InvoiceRepository,
        private val accountRepo: AccountRepository,
        private val ledgerRepo: LedgerRepository
) {
    /** Compares Invoice balances against specific Ledger Entry lines. */
    suspend fun verifyInvoices(): List<DataMismatch> {
        val invoices = invoiceRepo.findAll() // or find recently updated
        val arAccount = accountRepo.findByCode("1100") ?: return emptyList()
        val mismatches = mutableListOf<DataMismatch>()

        invoices.forEach { invoice ->
            // Use logical prefix to aggregate all partial payments for this invoice
            val ledgerPaid =
                    ledgerRepo.calculateSumForPartialReference(
                            "invoice-${invoice.id}-payment",
                            arAccount.id
                    )
            if (invoice.paidAmount.toLong() != ledgerPaid) {
                mismatches.add(
                        DataMismatch(
                                invoice.id.toString(),
                                invoice.paidAmount.toLong(),
                                ledgerPaid,
                                "INVOICE_LEDGER_MISMATCH"
                        )
                )
            }
        }
        return mismatches
    }

    /** Verifies the Fundamental Accounting Equation: Assets = Liabilities + Equity */
    suspend fun verifyAccountingEquation(): Boolean {
        val accounts = accountRepo.findAll()
        val now = java.time.Instant.now()

        var assets = 0L
        var liabilties = 0L
        var equity = 0L

        accounts.forEach { acc ->
            val bal = ledgerRepo.calculateAccountBalance(acc.id, now)
            when (acc.accountType) {
                AccountType.ASSET -> assets += bal
                AccountType.LIABILITY -> liabilties += bal
                AccountType.EQUITY -> equity += bal
                else -> {} // Revenue/Expense are closed into Equity in a formal close
            }
        }
        return assets == (liabilties + equity)
    }
}
