package com.solodev.fleet.modules.accounts.application.dto

import com.solodev.fleet.modules.accounts.domain.model.Payment
import kotlinx.serialization.Serializable

@Serializable
data class PaymentResponse(
        val id: String,
        val paymentNumber: String,
        val amount: Int,
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
                                amount = payment.amount,
                                paymentMethod = payment.paymentMethod,
                                status = payment.status.name,
                                paymentDate = payment.paymentDate.toString(),
                                notes = payment.notes
                        )
        }
}
