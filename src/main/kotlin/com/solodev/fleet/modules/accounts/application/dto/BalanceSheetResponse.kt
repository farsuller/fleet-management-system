package com.solodev.fleet.modules.accounts.application.dto

import kotlinx.serialization.Serializable

@Serializable
data class BalanceSheetResponse(
        val asOfDate: String,
        val assets: List<AccountBalanceInfo>,
        val liabilities: List<AccountBalanceInfo>,
        val equity: List<AccountBalanceInfo>,
        val totalAssets: Long,
        val totalLiabilities: Long,
        val totalEquity: Long,
        val isBalanced: Boolean
)

/** Basic account information with balance for reports. */
@Serializable data class AccountBalanceInfo(val code: String, val name: String, val balance: Long)
