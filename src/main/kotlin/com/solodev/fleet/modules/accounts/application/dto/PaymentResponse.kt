package com.solodev.fleet.modules.accounts.application.dto

import com.solodev.fleet.modules.accounts.domain.model.Payment
import com.solodev.fleet.modules.accounts.domain.model.PaymentReceipt
import kotlinx.serialization.Serializable

@Serializable
data class PaymentResponse(
        val id: String,
        val paymentNumber: String,
        val amount: Double,
        val paymentMethod: String,
        val status: String,
        val paymentDate: String,
        val notes: String? = null
) {
    companion object {
        fun fromDomain(payment: Payment): PaymentResponse =
                PaymentResponse(
                        id = payment.id.toString(),
                        paymentNumber = payment.paymentNumber,
                        amount = payment.amountCents / 100.0,
                        paymentMethod = payment.paymentMethod,
                        status = payment.status.name,
                        paymentDate = payment.paymentDate.toString(),
                        notes = payment.notes
                )
    }
}

@Serializable
data class PaymentReceiptResponse(
        val message: String,
        val receipt: PaymentResponse,
        val invoice: InvoiceResponse
) {
    companion object {
        fun fromDomain(receipt: PaymentReceipt): PaymentReceiptResponse =
                PaymentReceiptResponse(
                        message = receipt.message,
                        receipt = PaymentResponse.fromDomain(receipt.payment),
                        invoice = InvoiceResponse.fromDomain(receipt.updatedInvoice)
                )
    }
}
