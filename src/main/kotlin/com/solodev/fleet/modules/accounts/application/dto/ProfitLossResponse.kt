package com.solodev.fleet.modules.accounts.application.dto

import kotlinx.serialization.Serializable

/** Row representing revenue or expense for a single account in the P&L report. */
@Serializable
data class PnlCategoryRow(
    val accountCode: String,
    val accountName: String,
    val amount: Long,
)

/** Monthly bar in the P&L chart. */
@Serializable
data class MonthlyPnlBar(
    val label: String,
    val revenue: Long,
    val expenses: Long,
)

/** Full Profit & Loss report response. */
@Serializable
data class ProfitLossResponse(
    val from: String,
    val to: String,
    val totalRevenue: Long,
    val totalExpenses: Long,
    val grossProfit: Long,
    val netProfitMarginPct: Double,
    val revenueByCategory: List<PnlCategoryRow>,
    val expenseByCategory: List<PnlCategoryRow>,
    val monthlyBars: List<MonthlyPnlBar>,
)
