package com.solodev.fleet.modules.accounts.application.dto

import com.solodev.fleet.modules.accounts.domain.model.PaymentMethod
import kotlinx.serialization.Serializable

@Serializable
data class PaymentMethodResponse(
    val id: String,
    val code: String,
    val displayName: String,
    val targetAccountCode: String,
    val status: String,
    val description: String? = null,
) {
    companion object {
        fun fromDomain(method: PaymentMethod) =
            PaymentMethodResponse(
                id = method.id.toString(),
                code = method.code,
                displayName = method.displayName,
                targetAccountCode = method.targetAccountCode,
                status = method.status.name,
                description = method.description,
            )
    }
}

@Serializable
data class PaymentMethodRequest(
    val code: String,
    val displayName: String,
    val targetAccountCode: String,
    val status: String = "ACTIVE",
    val description: String? = null,
)
