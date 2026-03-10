package com.solodev.fleet.modules.accounts.application.dto

import kotlinx.serialization.Serializable

/** Request body to submit a driver remittance (batch hand-over of collected payments). */
@Serializable
data class DriverRemittanceRequest(
    val driverId: String,
    /** IDs of PENDING driver-collected payments to include in this remittance. */
    val paymentIds: List<String>,
    /** ISO-8601 timestamp of when the driver physically submitted the funds. */
    val remittanceDate: String,
    val notes: String? = null
)
