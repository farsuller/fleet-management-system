package com.solodev.fleet.modules.accounts.application.dto

import kotlinx.serialization.Serializable

@Serializable
data class InvoiceRequest(
        val customerId: String,
        val rentalId: String? = null,
        val subtotal: Double,
        val tax: Double,
        val dueDate: String // ISO-8601
)
