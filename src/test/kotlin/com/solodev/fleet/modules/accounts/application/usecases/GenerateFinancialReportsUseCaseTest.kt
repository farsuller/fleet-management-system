package com.solodev.fleet.modules.accounts.application.usecases

import com.solodev.fleet.modules.accounts.domain.model.Account
import com.solodev.fleet.modules.accounts.domain.model.AccountId
import com.solodev.fleet.modules.accounts.domain.model.AccountType
import com.solodev.fleet.modules.accounts.domain.repository.AccountRepository
import com.solodev.fleet.modules.accounts.domain.repository.LedgerRepository
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class GenerateFinancialReportsUseCaseTest {

    private val accountRepo = mockk<AccountRepository>()
    private val ledgerRepo = mockk<LedgerRepository>()
    private val useCase = GenerateFinancialReportsUseCase(accountRepo, ledgerRepo)

    private val revenueAccount = Account(
        id = AccountId("acc-rev-1"),
        accountCode = "4000",
        accountName = "Rental Revenue",
        accountType = AccountType.REVENUE
    )
    private val assetAccount = Account(
        id = AccountId("acc-asset-1"),
        accountCode = "1100",
        accountName = "Accounts Receivable",
        accountType = AccountType.ASSET
    )
    private val liabilityAccount = Account(
        id = AccountId("acc-liab-1"),
        accountCode = "2000",
        accountName = "Accounts Payable",
        accountType = AccountType.LIABILITY
    )
    private val equityAccount = Account(
        id = AccountId("acc-equity-1"),
        accountCode = "3000",
        accountName = "Owner Equity",
        accountType = AccountType.EQUITY
    )

    @Test
    fun shouldReturnRevenueReport_WhenRevenueAccountsExist() = runBlocking {
        // Arrange
        val start = Instant.parse("2026-01-01T00:00:00Z")
        val end = Instant.parse("2026-03-01T00:00:00Z")
        coEvery { accountRepo.findAll() } returns listOf(revenueAccount)
        // Revenue accounts have a normal Credit balance (debit-credit = negative raw)
        coEvery { ledgerRepo.calculateAccountBalance(AccountId("acc-rev-1"), end) } returns -120000L

        // Act
        val result = useCase.revenueReport(start, end)

        // Assert
        assertThat(result.items).hasSize(1)
        assertThat(result.items[0].category).isEqualTo("Rental Revenue")
        assertThat(result.items[0].amount).isEqualTo(120000L) // negated: -(-120000)
        assertThat(result.totalRevenue).isEqualTo(120000L)
        assertThat(result.startDate).isEqualTo(start.toString())
        assertThat(result.endDate).isEqualTo(end.toString())
    }

    @Test
    fun shouldReturnEmptyRevenue_WhenNoRevenueAccountsExist() = runBlocking {
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
    fun shouldReturnBalancedBalanceSheet_WhenEquationHolds() = runBlocking {
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
    fun shouldReturnUnbalancedSheet_WhenEquityDoesNotMatchAssetMinusLiabilities() = runBlocking {
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
}
