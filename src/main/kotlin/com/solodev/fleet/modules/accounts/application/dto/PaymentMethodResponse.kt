package com.solodev.fleet.modules.accounts.application.dto

import com.solodev.fleet.modules.accounts.domain.model.PaymentMethod
import kotlinx.serialization.Serializable

@Serializable
data class PaymentMethodResponse(
        val id: String,
        val code: String,
        val displayName: String,
        val targetAccountCode: String,
        val isActive: Boolean,
        val description: String? = null
) {
    companion object {
        fun fromDomain(method: PaymentMethod) =
                PaymentMethodResponse(
                        id = method.id.toString(),
                        code = method.code,
                        displayName = method.displayName,
                        targetAccountCode = method.targetAccountCode,
                        isActive = method.isActive,
                        description = method.description
                )
    }
}

@Serializable
data class PaymentMethodRequest(
        val code: String,
        val displayName: String,
        val targetAccountCode: String,
        val isActive: Boolean = true,
        val description: String? = null
)
