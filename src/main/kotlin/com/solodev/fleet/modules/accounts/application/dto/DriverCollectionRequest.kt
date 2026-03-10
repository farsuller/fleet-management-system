package com.solodev.fleet.modules.accounts.application.dto

import kotlinx.serialization.Serializable

/** Request body to record a cash/wallet collection made by a driver in the field. */
@Serializable
data class DriverCollectionRequest(
    val driverId: String,
    val customerId: String,
    val invoiceId: String,
    val amount: Int,
    /** Payment method code, e.g. CASH, GCASH, PAYMAYA. */
    val paymentMethod: String,
    val transactionReference: String? = null,
    /** ISO-8601 timestamp of when the driver collected from the customer. */
    val collectedAt: String
)
