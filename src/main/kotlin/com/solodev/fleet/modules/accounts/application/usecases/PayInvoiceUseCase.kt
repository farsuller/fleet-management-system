package com.solodev.fleet.modules.accounts.application.usecases

import com.solodev.fleet.modules.accounts.domain.model.*
import com.solodev.fleet.modules.accounts.domain.repository.AccountRepository
import com.solodev.fleet.modules.accounts.domain.repository.InvoiceRepository
import com.solodev.fleet.modules.accounts.domain.repository.LedgerRepository
import com.solodev.fleet.modules.accounts.domain.repository.PaymentMethodRepository
import com.solodev.fleet.modules.accounts.domain.repository.PaymentRepository
import java.time.Instant
import java.util.*

class PayInvoiceUseCase(
        private val invoiceRepo: InvoiceRepository,
        private val paymentRepo: PaymentRepository,
        private val accountRepo: AccountRepository,
        private val ledgerRepo: LedgerRepository,
        private val paymentMethodRepo: PaymentMethodRepository
) {
        suspend fun execute(
                invoiceId: String,
                amount: Int,
                paymentMethod: String,
                notes: String? = null
        ): PaymentReceipt {
                val invoice =
                        invoiceRepo.findById(UUID.fromString(invoiceId))
                                ?: throw IllegalArgumentException("Invoice not found")

                if (amount > invoice.balance) throw IllegalArgumentException("Overpayment")

                // 1. Create Payment Record (The "Receipt" data)
                val payment =
                        Payment(
                                id = UUID.randomUUID(),
                                paymentNumber = "PAY-${System.currentTimeMillis()}",
                                customerId = invoice.customerId,
                                invoiceId = invoice.id,
                                amount = amount,
                                paymentMethod = paymentMethod,
                                status = PaymentStatus.COMPLETED,
                                paymentDate = Instant.now(),
                                notes = notes
                        )
                paymentRepo.save(payment)

                // 2. Update Invoice state
                val updatedPaidAmount = invoice.paidAmount + amount
                val updatedInvoice =
                        invoice.copy(
                                paidAmount = updatedPaidAmount,
                                status =
                                        if (invoice.totalAmount <= updatedPaidAmount)
                                                InvoiceStatus.PAID
                                        else InvoiceStatus.ISSUED,
                                paidDate =
                                        if (invoice.totalAmount <= updatedPaidAmount) Instant.now()
                                        else null
                        )
                val savedInvoice = invoiceRepo.save(updatedInvoice)

                // 3. Post to General Ledger
                // Map payment method to the correct Asset account via dynamic lookup
                val targetAccountCode =
                        paymentMethodRepo.findByCode(paymentMethod.uppercase())?.targetAccountCode
                                ?: "1000" // Fallback to Cash if not found or not configured

                val assetAccount =
                        accountRepo.findByCode(targetAccountCode)
                                ?: throw IllegalStateException(
                                        "Asset account ($targetAccountCode) not found"
                                )
                val arAccount =
                        accountRepo.findByCode("1100")
                                ?: throw IllegalStateException(
                                        "Accounts Receivable account (1100) not found"
                                )

                val entryDate = Instant.now()
                val entryId = LedgerEntryId(UUID.randomUUID().toString())

                val ledgerEntry =
                        LedgerEntry(
                                id = entryId,
                                entryNumber = "JE-${System.currentTimeMillis()}",
                                externalReference = "invoice-${invoice.id}-payment-${payment.id}",
                                entryDate = entryDate,
                                description =
                                        "Payment ($paymentMethod) received for Invoice ${invoice.invoiceNumber}. ${notes ?: ""}",
                                lines =
                                        listOf(
                                                LedgerEntryLine(
                                                        id = UUID.randomUUID(),
                                                        entryId = entryId,
                                                        accountId = assetAccount.id,
                                                        debitAmount = amount,
                                                        creditAmount = 0,
                                                        description =
                                                                "Payment for ${invoice.invoiceNumber} via $paymentMethod"
                                                ),
                                                LedgerEntryLine(
                                                        id = UUID.randomUUID(),
                                                        entryId = entryId,
                                                        accountId = arAccount.id,
                                                        debitAmount = 0,
                                                        creditAmount = amount,
                                                        description =
                                                                "Payment for ${invoice.invoiceNumber}"
                                                )
                                        )
                        )
                ledgerRepo.save(ledgerEntry)

                // 4. Return Receipt (With the success message you requested)
                return PaymentReceipt(
                        message =
                                "Payment of $amount ${invoice.currencyCode} processed successfully.",
                        payment = payment,
                        updatedInvoice = savedInvoice
                )
        }
}
