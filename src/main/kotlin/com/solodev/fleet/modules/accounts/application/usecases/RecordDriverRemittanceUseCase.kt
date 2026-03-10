package com.solodev.fleet.modules.accounts.application.usecases

import com.solodev.fleet.modules.accounts.application.dto.DriverRemittanceRequest
import com.solodev.fleet.modules.accounts.domain.model.*
import com.solodev.fleet.modules.accounts.domain.repository.*
import java.time.Instant
import java.util.UUID

/**
 * Processes a driver remittance — the physical hand-over of collected customer payments
 * to the back-office.
 *
 * For each included PENDING payment:
 * - Payment status → COMPLETED
 * - Linked invoice paidAmount is updated; invoice status recalculated
 * - GL entry posted: DR Asset account (per payment method) / CR Accounts Receivable (1100)
 */
class RecordDriverRemittanceUseCase(
    private val paymentRepository: PaymentRepository,
    private val invoiceRepository: InvoiceRepository,
    private val accountRepository: AccountRepository,
    private val ledgerRepository: LedgerRepository,
    private val paymentMethodRepository: PaymentMethodRepository,
    private val remittanceRepository: DriverRemittanceRepository
) {
    suspend fun execute(request: DriverRemittanceRequest): DriverRemittance {
        val driverId = UUID.fromString(request.driverId)
        val paymentIds = request.paymentIds.map { UUID.fromString(it) }

        val payments = paymentRepository.findByIds(paymentIds)

        require(payments.size == paymentIds.size) {
            "One or more payment IDs not found; expected ${paymentIds.size}, found ${payments.size}"
        }
        require(payments.all { it.driverId == driverId }) {
            "All payments must belong to driver $driverId"
        }
        require(payments.all { it.status == PaymentStatus.PENDING }) {
            "All payments must be in PENDING status before remittance"
        }
        require(payments.all { it.collectionType == PaymentCollectionType.DRIVER_COLLECTED }) {
            "All payments must be DRIVER_COLLECTED type"
        }

        val totalAmount = payments.sumOf { it.amount }

        val remittance = DriverRemittance(
            id = UUID.randomUUID(),
            remittanceNumber = "REM-${System.currentTimeMillis()}",
            driverId = driverId,
            remittanceDate = Instant.parse(request.remittanceDate),
            totalAmount = totalAmount,
            status = RemittanceStatus.SUBMITTED,
            paymentIds = paymentIds,
            notes = request.notes
        )
        val savedRemittance = remittanceRepository.save(remittance)

        val arAccount = accountRepository.findByCode("1100")
            ?: throw IllegalStateException("Accounts Receivable account (1100) not found")

        for (payment in payments) {
            // 1. Complete the payment
            paymentRepository.save(payment.copy(status = PaymentStatus.COMPLETED))

            // 2. Update linked invoice
            payment.invoiceId?.let { invoiceId ->
                val invoice = invoiceRepository.findById(invoiceId) ?: return@let
                val updatedPaid = invoice.paidAmount + payment.amount
                invoiceRepository.save(
                    invoice.copy(
                        paidAmount = updatedPaid,
                        status = if (invoice.totalAmount <= updatedPaid) InvoiceStatus.PAID
                                 else InvoiceStatus.ISSUED,
                        paidDate = if (invoice.totalAmount <= updatedPaid) Instant.now() else null
                    )
                )
            }

            // 3. Post GL: DR Cash/eWallet / CR Accounts Receivable
            val targetCode = paymentMethodRepository
                .findByCode(payment.paymentMethod.uppercase())
                ?.targetAccountCode ?: "1000"
            val assetAccount = accountRepository.findByCode(targetCode)
                ?: throw IllegalStateException("Asset account ($targetCode) not found")

            val entryId = LedgerEntryId(UUID.randomUUID().toString())
            ledgerRepository.save(
                LedgerEntry(
                    id = entryId,
                    entryNumber = "JE-${System.currentTimeMillis()}",
                    externalReference =
                        "remittance-${savedRemittance.id}-payment-${payment.id}",
                    entryDate = Instant.now(),
                    description =
                        "Driver remittance ${savedRemittance.remittanceNumber}: " +
                        "${payment.paymentMethod} collected by driver $driverId",
                    lines = listOf(
                        LedgerEntryLine(
                            id = UUID.randomUUID(),
                            entryId = entryId,
                            accountId = assetAccount.id,
                            debitAmount = payment.amount,
                            creditAmount = 0,
                            description = "Remitted via ${payment.paymentMethod}"
                        ),
                        LedgerEntryLine(
                            id = UUID.randomUUID(),
                            entryId = entryId,
                            accountId = arAccount.id,
                            debitAmount = 0,
                            creditAmount = payment.amount,
                            description = "AR cleared — remittance ${savedRemittance.remittanceNumber}"
                        )
                    )
                )
            )
        }

        return savedRemittance
    }
}
