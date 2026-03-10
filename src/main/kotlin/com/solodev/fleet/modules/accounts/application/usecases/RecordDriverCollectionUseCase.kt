package com.solodev.fleet.modules.accounts.application.usecases

import com.solodev.fleet.modules.accounts.application.dto.DriverCollectionRequest
import com.solodev.fleet.modules.accounts.domain.model.InvoiceStatus
import com.solodev.fleet.modules.accounts.domain.model.Payment
import com.solodev.fleet.modules.accounts.domain.model.PaymentCollectionType
import com.solodev.fleet.modules.accounts.domain.model.PaymentStatus
import com.solodev.fleet.modules.accounts.domain.repository.InvoiceRepository
import com.solodev.fleet.modules.accounts.domain.repository.PaymentRepository
import java.time.Instant
import java.util.UUID

/**
 * Records a payment collected by a driver from a customer in the field.
 *
 * The payment is created in PENDING / DRIVER_COLLECTED state. No GL entries are posted until
 * the driver submits a remittance via [RecordDriverRemittanceUseCase].
 */
class RecordDriverCollectionUseCase(
    private val invoiceRepository: InvoiceRepository,
    private val paymentRepository: PaymentRepository
) {
    suspend fun execute(request: DriverCollectionRequest): Payment {
        val invoice = invoiceRepository.findById(UUID.fromString(request.invoiceId))
            ?: throw IllegalArgumentException("Invoice not found: ${request.invoiceId}")

        require(invoice.customerId.value == request.customerId) {
            "Invoice does not belong to customer ${request.customerId}"
        }
        require(invoice.status == InvoiceStatus.ISSUED || invoice.status == InvoiceStatus.OVERDUE) {
            "Invoice must be ISSUED or OVERDUE to accept payment, current status: ${invoice.status}"
        }
        require(request.amount > 0) { "Collection amount must be positive" }
        require(request.amount <= invoice.balance) {
            "Collection amount (${request.amount}) exceeds outstanding invoice balance (${invoice.balance})"
        }

        val payment = Payment(
            id = UUID.randomUUID(),
            paymentNumber = "PAY-DRV-${System.currentTimeMillis()}",
            customerId = invoice.customerId,
            invoiceId = invoice.id,
            driverId = UUID.fromString(request.driverId),
            amount = request.amount,
            paymentMethod = request.paymentMethod,
            transactionReference = request.transactionReference,
            status = PaymentStatus.PENDING,
            paymentDate = Instant.parse(request.collectedAt),
            collectionType = PaymentCollectionType.DRIVER_COLLECTED,
            notes = "Collected by driver ${request.driverId} — awaiting remittance"
        )

        return paymentRepository.save(payment)
    }
}
