package com.solodev.fleet.modules.accounts.application.dto

import kotlinx.serialization.Serializable

@Serializable
data class PaymentRequest(val amount: Int, val paymentMethod: String, val notes: String? = null)
