package com.solodev.fleet.modules.accounts.application.dto

import kotlinx.serialization.Serializable

/** Single time-series data point for a revenue chart. */
@Serializable
data class RevenueDataPoint(
    val label: String,
    val amount: Long,
)

/** Response for the revenue time-series endpoint. */
@Serializable
data class RevenueTimeSeriesResponse(
    val groupBy: String,
    val points: List<RevenueDataPoint>,
)

/** Response for the revenue report endpoint. */
@Serializable
data class RevenueReportResponse(
    val startDate: String,
    val endDate: String,
    val totalRevenue: Long,
    val items: List<RevenueItem>,
)

/** Individual item in a revenue report. */
@Serializable
data class RevenueItem(
    val accountName: String,
    val amount: Long,
    val description: String,
)
