package com.solodev.fleet.modules.accounts.application.dto

import com.solodev.fleet.modules.accounts.domain.model.Payment
import kotlinx.serialization.Serializable

@Serializable
data class PaymentResponse(
        val id: String,
        val paymentNumber: String,
        val customerId: String,
        val invoiceId: String?,
        val driverId: String?,
        val amount: Int,
        val paymentMethod: String,
        val transactionReference: String?,
        val collectionType: String,
        val status: String,
        val paymentDate: String,
        val notes: String? = null
) {
        companion object {
                fun fromDomain(payment: Payment): PaymentResponse =
                        PaymentResponse(
                                id = payment.id.toString(),
                                paymentNumber = payment.paymentNumber,
                                customerId = payment.customerId.value,
                                invoiceId = payment.invoiceId?.toString(),
                                driverId = payment.driverId?.toString(),
                                amount = payment.amount,
                                paymentMethod = payment.paymentMethod,
                                transactionReference = payment.transactionReference,
                                collectionType = payment.collectionType.name,
                                status = payment.status.name,
                                paymentDate = payment.paymentDate.toString(),
                                notes = payment.notes
                        )
        }
}
