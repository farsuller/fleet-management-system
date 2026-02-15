package com.solodev.fleet.modules.accounts.application.usecases

import com.solodev.fleet.modules.accounts.application.dto.*
import com.solodev.fleet.modules.accounts.domain.model.AccountType
import com.solodev.fleet.modules.accounts.domain.repository.AccountRepository
import com.solodev.fleet.modules.accounts.domain.repository.LedgerRepository
import java.time.Instant

class GenerateFinancialReportsUseCase(
        private val accountRepo: AccountRepository,
        private val ledgerRepo: LedgerRepository
) {
    /** Generates a report showing revenue grouped by account. */
    suspend fun revenueReport(start: Instant, end: Instant): RevenueReportResponse {
        val accounts = accountRepo.findAll().filter { it.accountType == AccountType.REVENUE }
        val items =
                accounts.map { acc ->
                    val rawBalance = ledgerRepo.calculateAccountBalance(acc.id, end)
                    // Revenue accounts have a normal Credit balance.
                    // Calculate as (Credit - Debit) for reporting.
                    val adjustedBalance = -rawBalance
                    RevenueItem(acc.accountName, adjustedBalance, acc.description ?: "")
                }
        return RevenueReportResponse(
                startDate = start.toString(),
                endDate = end.toString(),
                totalRevenue = items.sumOf { it.amount },
                items = items
        )
    }

    /** Generates a Balance Sheet as of a specific date. */
    suspend fun balanceSheet(asOf: Instant): BalanceSheetResponse {
        val accounts = accountRepo.findAll()
        val mapped =
                accounts.map { acc ->
                    val rawBalance = ledgerRepo.calculateAccountBalance(acc.id, asOf)
                    val adjustedBalance =
                            when (acc.accountType) {
                                AccountType.REVENUE, AccountType.LIABILITY, AccountType.EQUITY ->
                                        -rawBalance
                                else -> rawBalance
                            }
                    AccountBalanceInfo(acc.accountCode, acc.accountName, adjustedBalance)
                }

        val assets =
                mapped.filter {
                    accounts.find { a -> a.accountCode == it.code }?.accountType ==
                            AccountType.ASSET
                }
        val liabilities =
                mapped.filter {
                    accounts.find { a -> a.accountCode == it.code }?.accountType ==
                            AccountType.LIABILITY
                }
        val equity =
                mapped.filter {
                    accounts.find { a -> a.accountCode == it.code }?.accountType ==
                            AccountType.EQUITY
                }

        return BalanceSheetResponse(
                asOfDate = asOf.toString(),
                assets = assets,
                liabilities = liabilities,
                equity = equity,
                totalAssets = assets.sumOf { it.balance },
                totalLiabilities = liabilities.sumOf { it.balance },
                totalEquity = equity.sumOf { it.balance },
                isBalanced =
                        (assets.sumOf { it.balance } - liabilities.sumOf { it.balance } ==
                                equity.sumOf { it.balance })
        )
    }
}
