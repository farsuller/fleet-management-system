package com.solodev.fleet.modules.accounts.application.dto

import kotlinx.serialization.Serializable

/** Formal receipt response combining payment and invoice details. */
@Serializable
data class PaymentReceiptResponse(
    val message: String,
    val payment: PaymentResponse,
    val invoice: InvoiceResponse
) {
    companion object {
        fun fromDomain(receipt: com.solodev.fleet.modules.accounts.domain.model.PaymentReceipt) = PaymentReceiptResponse(
            message = receipt.message,
            payment = PaymentResponse.fromDomain(receipt.payment),
            invoice = InvoiceResponse.fromDomain(receipt.updatedInvoice)
        )
    }
}