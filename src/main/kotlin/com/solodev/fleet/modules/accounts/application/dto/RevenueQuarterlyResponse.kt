package com.solodev.fleet.modules.accounts.application.dto

import kotlinx.serialization.Serializable

/**
 * Quarterly revenue breakdown for the current year.
 * All amounts are in **centavos** (Long) — divide by 100 on the client to get PHP.
 * Quarters with no data return 0.
 */
@Serializable
data class RevenueQuarterlyResponse(
    val year: Int,
    /** Total revenue for Jan–Mar (centavos). */
    val q1: Long,
    /** Total revenue for Apr–Jun (centavos). */
    val q2: Long,
    /** Total revenue for Jul–Sep (centavos). */
    val q3: Long,
    /** Total revenue for Oct–Dec (centavos). */
    val q4: Long,
    /** Full name of the month with highest revenue, e.g. "March". Empty if no data. */
    val peakMonthName: String,
    /** Revenue of the peak month (centavos). */
    val peakMonthAmount: Long,
    /** Per-month totals Jan–Dec (centavos), always 12 entries. */
    val monthlyTotals: List<MonthlyTotal>,
)

@Serializable
data class MonthlyTotal(
    /** Short month name, e.g. "Jan". */
    val month: String,
    /** Month number 1–12. */
    val monthNumber: Int,
    /** Revenue for this month (centavos). */
    val amount: Long,
)
