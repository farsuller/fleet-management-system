package com.solodev.fleet.modules.accounts.application.usecases

import com.solodev.fleet.modules.accounts.domain.model.Account
import com.solodev.fleet.modules.accounts.domain.model.AccountId
import com.solodev.fleet.modules.accounts.domain.model.AccountType
import com.solodev.fleet.modules.accounts.domain.repository.AccountRepository
import com.solodev.fleet.modules.accounts.domain.repository.LedgerRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class GenerateFinancialReportsUseCaseTest {
    private val accountRepo = mockk<AccountRepository>()
    private val ledgerRepo = mockk<LedgerRepository>()
    private val useCase = GenerateFinancialReportsUseCase(accountRepo, ledgerRepo)

    private val revenueAccount =
        Account(
            id = AccountId("acc-rev-1"),
            accountCode = "4000",
            accountName = "Rental Revenue",
            accountType = AccountType.REVENUE,
        )
    private val assetAccount =
        Account(
            id = AccountId("acc-asset-1"),
            accountCode = "1100",
            accountName = "Accounts Receivable",
            accountType = AccountType.ASSET,
        )
    private val liabilityAccount =
        Account(
            id = AccountId("acc-liab-1"),
            accountCode = "2000",
            accountName = "Accounts Payable",
            accountType = AccountType.LIABILITY,
        )
    private val equityAccount =
        Account(
            id = AccountId("acc-equity-1"),
            accountCode = "3000",
            accountName = "Owner Equity",
            accountType = AccountType.EQUITY,
        )

    @Test
    fun shouldReturnRevenueReport_WhenRevenueAccountsExist(): Unit =
        runBlocking {
            // Arrange
            val start = Instant.parse("2026-01-01T00:00:00Z")
            val end = Instant.parse("2026-03-01T00:00:00Z")
            coEvery { accountRepo.findAll() } returns listOf(revenueAccount)
            // Revenue accounts have a normal Credit balance (debit-credit = negative raw)
            coEvery { ledgerRepo.calculateAccountBalanceBetween(AccountId("acc-rev-1"), start, end) } returns -120000L

            // Act
            val result = useCase.revenueReport(start, end)

            // Assert
            assertThat(result.items).hasSize(1)
            assertThat(result.items[0].accountName).isEqualTo("Rental Revenue")
            assertThat(result.items[0].amount).isEqualTo(120000L) // negated: -(-120000)
            assertThat(result.totalRevenue).isEqualTo(120000L)
            assertThat(result.startDate).isEqualTo(start.toString())
            assertThat(result.endDate).isEqualTo(end.toString())
        }

    @Test
    fun shouldReturnEmptyRevenue_WhenNoRevenueAccountsExist(): Unit =
        runBlocking {
            // Arrange
            val start = Instant.parse("2026-01-01T00:00:00Z")
            val end = Instant.parse("2026-03-01T00:00:00Z")
            coEvery { accountRepo.findAll() } returns listOf(assetAccount, liabilityAccount)

            // Act
            val result = useCase.revenueReport(start, end)

            // Assert
            assertThat(result.items).isEmpty()
            assertThat(result.totalRevenue).isEqualTo(0L)
        }

    @Test
    fun shouldReturnBalancedBalanceSheet_WhenEquationHolds(): Unit =
        runBlocking {
            // Arrange — assets(50000) - liabilities(10000) == equity(40000)
            val asOf = Instant.parse("2026-03-08T00:00:00Z")
            coEvery { accountRepo.findAll() } returns listOf(assetAccount, liabilityAccount, equityAccount)
            coEvery { ledgerRepo.calculateAccountBalance(AccountId("acc-asset-1"), asOf) } returns 50000L
            // LIABILITY/EQUITY are negated in the use case; raw -10000 → adjusted +10000
            coEvery { ledgerRepo.calculateAccountBalance(AccountId("acc-liab-1"), asOf) } returns -10000L
            // raw -40000 → adjusted +40000; totalAssets(50000) - totalLiabilities(10000) == totalEquity(40000) ✓
            coEvery { ledgerRepo.calculateAccountBalance(AccountId("acc-equity-1"), asOf) } returns -40000L

            // Act
            val result = useCase.balanceSheet(asOf)

            // Assert
            assertThat(result.assets).hasSize(1)
            assertThat(result.liabilities).hasSize(1)
            assertThat(result.equity).hasSize(1)
            assertThat(result.totalAssets).isEqualTo(50000L)
            assertThat(result.totalLiabilities).isEqualTo(10000L)
            assertThat(result.totalEquity).isEqualTo(40000L)
            assertThat(result.isBalanced).isTrue()
        }

    @Test
    fun shouldReturnUnbalancedSheet_WhenEquityDoesNotMatchAssetMinusLiabilities(): Unit =
        runBlocking {
            // Arrange — assets(50000) - liabilities(10000) != equity(20000)
            val asOf = Instant.parse("2026-03-08T00:00:00Z")
            coEvery { accountRepo.findAll() } returns listOf(assetAccount, liabilityAccount, equityAccount)
            coEvery { ledgerRepo.calculateAccountBalance(AccountId("acc-asset-1"), asOf) } returns 50000L
            coEvery { ledgerRepo.calculateAccountBalance(AccountId("acc-liab-1"), asOf) } returns -10000L
            coEvery { ledgerRepo.calculateAccountBalance(AccountId("acc-equity-1"), asOf) } returns -20000L

            // Act
            val result = useCase.balanceSheet(asOf)

            // Assert
            assertThat(result.isBalanced).isFalse()
        }

    @Test
    fun shouldReturnRevenueKpis_WhenRevenueAccountsExist(): Unit =
        runBlocking {
            // Use a fixed "now" by providing accounts and stubbing all 4 balance calls.
            // coEvery with any() matchers covers the 4 time window calls made by revenueKpis().
            coEvery { accountRepo.findAll() } returns listOf(revenueAccount)
            coEvery {
                ledgerRepo.calculateAccountBalanceBetween(AccountId("acc-rev-1"), any(), any())
            } returns -5000L

            val result = useCase.revenueKpis()

            // Each of the 4 windows negates -5000 → 5000
            assertThat(result.dailySum).isEqualTo(5000L)
            assertThat(result.weeklySum).isEqualTo(5000L)
            assertThat(result.monthlySum).isEqualTo(5000L)
            assertThat(result.yearlySum).isEqualTo(5000L)
            // dailyAvg = yearlySum / daysElapsed; daysElapsed >= 1 → dailyAvg <= 5000
            assertThat(result.dailyAvg).isLessThanOrEqualTo(5000L)
        }

    @Test
    fun shouldReturnEmptyKpis_WhenNoRevenueAccounts(): Unit =
        runBlocking {
            coEvery { accountRepo.findAll() } returns listOf(assetAccount)

            val result = useCase.revenueKpis()

            assertThat(result.dailySum).isEqualTo(0L)
            assertThat(result.weeklySum).isEqualTo(0L)
            assertThat(result.monthlySum).isEqualTo(0L)
            assertThat(result.yearlySum).isEqualTo(0L)
            assertThat(result.dailyAvg).isEqualTo(0L)
        }

    @Test
    fun shouldReturnDailyTimeSeries_WhenGroupByIsDaily(): Unit =
        runBlocking {
            val start = Instant.parse("2026-04-01T00:00:00Z")
            val end = Instant.parse("2026-04-03T00:00:00Z")
            val line1 = Pair(Instant.parse("2026-04-01T10:00:00Z"), 1000L)
            val line2 = Pair(Instant.parse("2026-04-02T10:00:00Z"), 2000L)
            coEvery { accountRepo.findAll() } returns listOf(revenueAccount)
            coEvery { ledgerRepo.getRevenueLinesInRange(listOf(AccountId("acc-rev-1")), start, end) } returns listOf(line1, line2)

            val result = useCase.revenueTimeSeries("daily", start, end)

            assertThat(result.groupBy).isEqualTo("daily")
            assertThat(result.points).isNotEmpty()
            assertThat(result.points.map { it.amount }).contains(1000L, 2000L)
        }

    @Test
    fun shouldReturnWeeklyTimeSeries_WhenGroupByIsWeekly(): Unit =
        runBlocking {
            val start = Instant.parse("2026-04-01T00:00:00Z")
            val end = Instant.parse("2026-04-14T00:00:00Z")
            val line = Pair(Instant.parse("2026-04-07T10:00:00Z"), 3000L)
            coEvery { accountRepo.findAll() } returns listOf(revenueAccount)
            coEvery { ledgerRepo.getRevenueLinesInRange(listOf(AccountId("acc-rev-1")), start, end) } returns listOf(line)

            val result = useCase.revenueTimeSeries("weekly", start, end)

            assertThat(result.groupBy).isEqualTo("weekly")
            assertThat(result.points).isNotEmpty()
        }

    @Test
    fun shouldReturnMonthlyTimeSeries_WhenGroupByIsMonthly(): Unit =
        runBlocking {
            val start = Instant.parse("2026-01-01T00:00:00Z")
            val end = Instant.parse("2026-03-01T00:00:00Z")
            val line = Pair(Instant.parse("2026-02-10T00:00:00Z"), 7000L)
            coEvery { accountRepo.findAll() } returns listOf(revenueAccount)
            coEvery { ledgerRepo.getRevenueLinesInRange(listOf(AccountId("acc-rev-1")), start, end) } returns listOf(line)

            val result = useCase.revenueTimeSeries("monthly", start, end)

            assertThat(result.groupBy).isEqualTo("monthly")
            // Jan, Feb, Mar → 3 data points
            assertThat(result.points).hasSize(3)
        }

    @Test
    fun shouldFallbackToMonthlyTimeSeries_WhenGroupByIsUnknown(): Unit =
        runBlocking {
            val start = Instant.parse("2026-01-01T00:00:00Z")
            val end = Instant.parse("2026-02-01T00:00:00Z")
            coEvery { accountRepo.findAll() } returns listOf(revenueAccount)
            coEvery { ledgerRepo.getRevenueLinesInRange(listOf(AccountId("acc-rev-1")), start, end) } returns emptyList()

            val result = useCase.revenueTimeSeries("unknown_value", start, end)

            assertThat(result.groupBy).isEqualTo("unknown_value")
            assertThat(result.points).hasSize(2) // Jan + Feb
        }

    @Test
    fun shouldReturnProfitLoss_WhenRevenueAndExpenseAccountsExist(): Unit =
        runBlocking {
            val start = Instant.parse("2026-01-01T00:00:00Z")
            val end = Instant.parse("2026-01-31T23:59:59Z")
            val expenseAccount =
                Account(
                    id = AccountId("acc-exp-1"),
                    accountCode = "5000",
                    accountName = "Fuel Expense",
                    accountType = AccountType.EXPENSE,
                )
            coEvery { accountRepo.findAll() } returns listOf(revenueAccount, expenseAccount)
            // revenue: raw -80000 → adjusted +80000
            coEvery { ledgerRepo.calculateAccountBalanceBetween(AccountId("acc-rev-1"), any(), any()) } returns -80000L
            // expense: raw +30000 → adjusted +30000
            coEvery { ledgerRepo.calculateAccountBalanceBetween(AccountId("acc-exp-1"), any(), any()) } returns 30000L

            val result = useCase.profitLoss(start, end)

            assertThat(result.totalRevenue).isEqualTo(80000L)
            assertThat(result.totalExpenses).isEqualTo(30000L)
            assertThat(result.grossProfit).isEqualTo(50000L)
            assertThat(result.netProfitMarginPct).isEqualTo(62.5)
            assertThat(result.revenueByCategory).hasSize(1)
            assertThat(result.expenseByCategory).hasSize(1)
            assertThat(result.monthlyBars).isNotEmpty()
        }

    @Test
    fun shouldReturnZeroMargin_WhenNoRevenue(): Unit =
        runBlocking {
            val start = Instant.parse("2026-01-01T00:00:00Z")
            val end = Instant.parse("2026-01-31T23:59:59Z")
            coEvery { accountRepo.findAll() } returns listOf(assetAccount)

            val result = useCase.profitLoss(start, end)

            assertThat(result.totalRevenue).isEqualTo(0L)
            assertThat(result.netProfitMarginPct).isEqualTo(0.0)
            assertThat(result.monthlyBars).isNotEmpty()
        }
}
