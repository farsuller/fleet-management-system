package com.solodev.fleet.modules.accounts.application.usecases

import com.solodev.fleet.modules.accounts.application.dto.AccountBalanceInfo
import com.solodev.fleet.modules.accounts.application.dto.BalanceSheetResponse
import com.solodev.fleet.modules.accounts.application.dto.MonthlyPnlBar
import com.solodev.fleet.modules.accounts.application.dto.MonthlyTotal
import com.solodev.fleet.modules.accounts.application.dto.PnlCategoryRow
import com.solodev.fleet.modules.accounts.application.dto.ProfitLossResponse
import com.solodev.fleet.modules.accounts.application.dto.RevenueDataPoint
import com.solodev.fleet.modules.accounts.application.dto.RevenueItem
import com.solodev.fleet.modules.accounts.application.dto.RevenueKpisResponse
import com.solodev.fleet.modules.accounts.application.dto.RevenueQuarterlyResponse
import com.solodev.fleet.modules.accounts.application.dto.RevenueReportResponse
import com.solodev.fleet.modules.accounts.application.dto.RevenueTimeSeriesResponse
import com.solodev.fleet.modules.accounts.domain.model.AccountType
import com.solodev.fleet.modules.accounts.domain.repository.AccountRepository
import com.solodev.fleet.modules.accounts.domain.repository.LedgerRepository
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields

class GenerateFinancialReportsUseCase(
    private val accountRepo: AccountRepository,
    private val ledgerRepo: LedgerRepository,
) {
    /** Generates a report showing revenue grouped by account. */
    suspend fun revenueReport(
        start: Instant,
        end: Instant,
    ): RevenueReportResponse {
        val accounts = accountRepo.findAll().filter { it.accountType == AccountType.REVENUE }
        val items =
            accounts.map { acc ->
                val rawBalance = ledgerRepo.calculateAccountBalanceBetween(acc.id, start, end)
                val adjustedBalance = -rawBalance
                RevenueItem(accountName = acc.accountName, amount = adjustedBalance, description = acc.description ?: "")
            }
        return RevenueReportResponse(
            startDate = start.toString(),
            endDate = end.toString(),
            totalRevenue = items.sumOf { it.amount },
            items = items,
        )
    }

    /** Returns pre-computed KPI totals: daily avg, weekly sum, monthly sum, yearly sum. */
    suspend fun revenueKpis(): RevenueKpisResponse {
        val now = Instant.now()
        val today = LocalDate.now(ZoneOffset.UTC)

        val startOfDay = today.atStartOfDay(ZoneOffset.UTC).toInstant()
        val startOfWeek =
            today
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
        val startOfMonth =
            today
                .with(TemporalAdjusters.firstDayOfMonth())
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
        val startOfYear =
            today
                .with(TemporalAdjusters.firstDayOfYear())
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()

        val accounts = accountRepo.findAll().filter { it.accountType == AccountType.REVENUE }

        suspend fun sumBetween(
            from: Instant,
            to: Instant,
        ): Long = accounts.map { acc -> -ledgerRepo.calculateAccountBalanceBetween(acc.id, from, to) }.sum()

        val dailySum = sumBetween(startOfDay, now)
        val weeklySum = sumBetween(startOfWeek, now)
        val monthlySum = sumBetween(startOfMonth, now)
        val yearlySum = sumBetween(startOfYear, now)

        val daysElapsed = (today.dayOfYear).coerceAtLeast(1).toLong()
        val dailyAvg = yearlySum / daysElapsed

        return RevenueKpisResponse(
            dailyAvg = dailyAvg,
            dailySum = dailySum,
            weeklySum = weeklySum,
            monthlySum = monthlySum,
            yearlySum = yearlySum,
        )
    }

    /**
     * Returns per-month and per-quarter revenue totals for the current calendar year.
     * All amounts are in centavos — divide by 100 on the client to get PHP pesos.
     */
    suspend fun revenueQuarterly(): RevenueQuarterlyResponse {
        val today = LocalDate.now(ZoneOffset.UTC)
        val year = today.year
        val accounts = accountRepo.findAll().filter { it.accountType == AccountType.REVENUE }

        suspend fun monthSum(month: Int): Long {
            val from = LocalDate.of(year, month, 1).atStartOfDay(ZoneOffset.UTC).toInstant()
            val to =
                LocalDate
                    .of(year, month, 1)
                    .plusMonths(1)
                    .atStartOfDay(ZoneOffset.UTC)
                    .toInstant()
            return accounts.sumOf { acc ->
                -ledgerRepo.calculateAccountBalanceBetween(acc.id, from, to)
            }
        }

        val monthNames = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
        val fullNames =
            listOf(
                "January",
                "February",
                "March",
                "April",
                "May",
                "June",
                "July",
                "August",
                "September",
                "October",
                "November",
                "December",
            )

        val totals = (1..12).map { m -> monthSum(m) }

        val monthlyTotals =
            totals.mapIndexed { i, amt ->
                MonthlyTotal(month = monthNames[i], monthNumber = i + 1, amount = amt)
            }

        val peakIdx = totals.indices.maxByOrNull { totals[it] } ?: 0
        val peakName = if (totals[peakIdx] > 0L) fullNames[peakIdx] else ""
        val peakAmt = if (totals[peakIdx] > 0L) totals[peakIdx] else 0L

        return RevenueQuarterlyResponse(
            year = year,
            q1 = totals.slice(0..2).sum(),
            q2 = totals.slice(3..5).sum(),
            q3 = totals.slice(6..8).sum(),
            q4 = totals.slice(9..11).sum(),
            peakMonthName = peakName,
            peakMonthAmount = peakAmt,
            monthlyTotals = monthlyTotals,
        )
    }

    /**
     * Returns total revenue grouped by day / week / month for chart display.
     * groupBy: "daily" | "weekly" | "monthly"
     */
    suspend fun revenueTimeSeries(
        groupBy: String,
        start: Instant,
        end: Instant,
    ): RevenueTimeSeriesResponse {
        val revenueAccounts = accountRepo.findAll().filter { it.accountType == AccountType.REVENUE }
        val lines = ledgerRepo.getRevenueLinesInRange(revenueAccounts.map { it.id }, start, end)
        val points =
            when (groupBy) {
                "daily" -> groupByDay(lines, start, end)
                "weekly" -> groupByWeek(lines, start, end)
                "monthly" -> groupByMonth(lines, start, end)
                else -> groupByMonth(lines, start, end)
            }
        return RevenueTimeSeriesResponse(groupBy = groupBy, points = points)
    }

    private fun groupByDay(
        lines: List<Pair<Instant, Long>>,
        start: Instant,
        end: Instant,
    ): List<RevenueDataPoint> {
        val fmt = DateTimeFormatter.ofPattern("MMM dd")
        val startDate = start.atZone(ZoneOffset.UTC).toLocalDate()
        val endDate = end.atZone(ZoneOffset.UTC).toLocalDate()
        val sumByDate =
            lines
                .groupBy { (instant, _) -> instant.atZone(ZoneOffset.UTC).toLocalDate() }
                .mapValues { (_, entries) -> entries.sumOf { it.second } }
        val result = mutableListOf<RevenueDataPoint>()
        var current = startDate
        while (!current.isAfter(endDate)) {
            result.add(RevenueDataPoint(current.format(fmt), sumByDate[current] ?: 0L))
            current = current.plusDays(1)
        }
        return result
    }

    private fun groupByWeek(
        lines: List<Pair<Instant, Long>>,
        start: Instant,
        end: Instant,
    ): List<RevenueDataPoint> {
        val weekField = WeekFields.ISO.weekOfWeekBasedYear()
        val startDate =
            start
                .atZone(ZoneOffset.UTC)
                .toLocalDate()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val endDate = end.atZone(ZoneOffset.UTC).toLocalDate()
        val sumByWeek =
            lines
                .groupBy { (instant, _) -> instant.atZone(ZoneOffset.UTC).toLocalDate().get(weekField) }
                .mapValues { (_, entries) -> entries.sumOf { it.second } }
        val result = mutableListOf<RevenueDataPoint>()
        var current = startDate
        while (!current.isAfter(endDate)) {
            val weekNum = current.get(weekField)
            result.add(RevenueDataPoint("Week $weekNum", sumByWeek[weekNum] ?: 0L))
            current = current.plusWeeks(1)
        }
        return result
    }

    private fun groupByMonth(
        lines: List<Pair<Instant, Long>>,
        start: Instant,
        end: Instant,
    ): List<RevenueDataPoint> {
        val monthNames = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
        val startMonth = start.atZone(ZoneOffset.UTC).monthValue
        val endMonth = end.atZone(ZoneOffset.UTC).monthValue
        val sumByMonth =
            lines
                .groupBy { (instant, _) -> instant.atZone(ZoneOffset.UTC).monthValue }
                .mapValues { (_, entries) -> entries.sumOf { it.second } }
        return (startMonth..endMonth).map { month ->
            RevenueDataPoint(monthNames[month - 1], sumByMonth[month] ?: 0L)
        }
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
                (
                    assets.sumOf { it.balance } - liabilities.sumOf { it.balance } ==
                        equity.sumOf { it.balance }
                ),
        )
    }

    /** Generates a Profit & Loss report for the given date range. */
    suspend fun profitLoss(
        start: Instant,
        end: Instant,
    ): ProfitLossResponse {
        val monthNames = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
        val accounts = accountRepo.findAll()

        val revenueAccounts = accounts.filter { it.accountType == AccountType.REVENUE }
        val expenseAccounts = accounts.filter { it.accountType == AccountType.EXPENSE }

        val revenueRows =
            revenueAccounts
                .map { acc ->
                    val amount = -ledgerRepo.calculateAccountBalanceBetween(acc.id, start, end)
                    PnlCategoryRow(acc.accountCode, acc.accountName, amount)
                }.filter { it.amount != 0L }

        val expenseRows =
            expenseAccounts
                .map { acc ->
                    val amount = ledgerRepo.calculateAccountBalanceBetween(acc.id, start, end)
                    PnlCategoryRow(acc.accountCode, acc.accountName, amount)
                }.filter { it.amount != 0L }

        val totalRevenue = revenueRows.sumOf { it.amount }
        val totalExpenses = expenseRows.sumOf { it.amount }
        val grossProfit = totalRevenue - totalExpenses
        val margin = if (totalRevenue > 0) (grossProfit.toDouble() / totalRevenue.toDouble()) * 100.0 else 0.0

        // Monthly bars: iterate by month between start and end
        val startDate = start.atZone(ZoneOffset.UTC).toLocalDate().withDayOfMonth(1)
        val endDate = end.atZone(ZoneOffset.UTC).toLocalDate()
        val monthlyBars = mutableListOf<MonthlyPnlBar>()
        var cur = startDate
        while (!cur.isAfter(endDate)) {
            val mStart = cur.atStartOfDay(ZoneOffset.UTC).toInstant()
            val mEnd =
                cur
                    .with(TemporalAdjusters.lastDayOfMonth())
                    .plusDays(1)
                    .atStartOfDay(ZoneOffset.UTC)
                    .toInstant()
            val mRevenue = revenueAccounts.sumOf { acc -> -ledgerRepo.calculateAccountBalanceBetween(acc.id, mStart, mEnd) }
            val mExpenses = expenseAccounts.sumOf { acc -> ledgerRepo.calculateAccountBalanceBetween(acc.id, mStart, mEnd) }
            monthlyBars.add(MonthlyPnlBar(monthNames[cur.monthValue - 1], mRevenue, mExpenses))
            cur = cur.plusMonths(1)
        }

        return ProfitLossResponse(
            from = start.toString(),
            to = end.toString(),
            totalRevenue = totalRevenue,
            totalExpenses = totalExpenses,
            grossProfit = grossProfit,
            netProfitMarginPct = margin,
            revenueByCategory = revenueRows,
            expenseByCategory = expenseRows,
            monthlyBars = monthlyBars,
        )
    }
}
