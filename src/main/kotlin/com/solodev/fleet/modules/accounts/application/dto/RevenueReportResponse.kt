package com.solodev.fleet.modules.accounts.application.dto

import kotlinx.serialization.Serializable

/** Response for the revenue report endpoint. */
@Serializable
data class RevenueReportResponse(
        val startDate: String,
        val endDate: String,
        val totalRevenue: Long,
        val items: List<RevenueItem>
)

/** Individual item in a revenue report. */
@Serializable
data class RevenueItem(val category: String, val amount: Long, val description: String)
