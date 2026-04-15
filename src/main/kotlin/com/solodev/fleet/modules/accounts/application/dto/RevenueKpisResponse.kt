package com.solodev.fleet.modules.accounts.application.dto

import kotlinx.serialization.Serializable

/** Precomputed revenue KPIs returned by GET /v1/accounting/reports/revenue-kpis. */
@Serializable
data class RevenueKpisResponse(
    /** Average daily revenue this year (yearlySum / daysElapsed). */
    val dailyAvg: Long,
    /** Total revenue today (current calendar day). */
    val dailySum: Long,
    /** Total revenue since Monday of the current week. */
    val weeklySum: Long,
    /** Total revenue since 1st of the current month. */
    val monthlySum: Long,
    /** Total revenue since 1st of the current year. */
    val yearlySum: Long,
)
