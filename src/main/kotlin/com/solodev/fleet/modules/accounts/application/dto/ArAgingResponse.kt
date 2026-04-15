package com.solodev.fleet.modules.accounts.application.dto

import kotlinx.serialization.Serializable

/** One row in the AR Aging report — summarises open invoice balances for a single customer. */
@Serializable
data class ArAgingRow(
    val customerId: String,
    val customerName: String,
    /** Invoices not yet past due date (including due today). */
    val bucket0to30: Long,
    val bucket31to60: Long,
    val bucket61to90: Long,
    val bucket91plus: Long,
    val total: Long,
)

/** Full AR Aging report as of a specific date. */
@Serializable
data class ArAgingResponse(
    val asOfDate: String,
    val rows: List<ArAgingRow>,
    val totalBucket0to30: Long,
    val totalBucket31to60: Long,
    val totalBucket61to90: Long,
    val totalBucket91plus: Long,
    val grandTotal: Long,
)
