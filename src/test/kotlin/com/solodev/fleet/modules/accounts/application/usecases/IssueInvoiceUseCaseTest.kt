package com.solodev.fleet.modules.accounts.application.usecases

import com.solodev.fleet.modules.accounts.application.dto.InvoiceRequest
import com.solodev.fleet.modules.accounts.domain.model.Account
import com.solodev.fleet.modules.accounts.domain.model.AccountId
import com.solodev.fleet.modules.accounts.domain.model.AccountType
import com.solodev.fleet.modules.accounts.domain.model.LedgerEntry
import com.solodev.fleet.modules.accounts.domain.repository.AccountRepository
import com.solodev.fleet.modules.accounts.domain.repository.InvoiceRepository
import com.solodev.fleet.modules.accounts.domain.repository.LedgerRepository
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class IssueInvoiceUseCaseTest {
    private val invoiceRepo = mockk<InvoiceRepository>()
    private val accountRepo = mockk<AccountRepository>()
    private val ledgerRepo = mockk<LedgerRepository>()
    private val useCase = IssueInvoiceUseCase(invoiceRepo, accountRepo, ledgerRepo)

    private val validRequest =
        InvoiceRequest(
            customerId = "cust-001",
            subtotal = 10000,
            tax = 1200,
            dueDate = "2027-12-31T00:00:00Z",
        )

    @Test
    fun shouldIssueInvoiceAndPostDoubleEntry_WhenAccountsExist(): Unit =
        runBlocking {
            // Arrange
            val arAccount = sampleAccount("1100", AccountType.ASSET)
            val revenueAccount = sampleAccount("4000", AccountType.REVENUE)
            val savedLedger = slot<LedgerEntry>()
            coEvery { accountRepo.findByCode("1100") } returns arAccount
            coEvery { accountRepo.findByCode("4000") } returns revenueAccount
            coEvery { invoiceRepo.save(any()) } returnsArgument 0
            coEvery { ledgerRepo.save(capture(savedLedger)) } returnsArgument 0

            // Act
            val result = useCase.execute(validRequest)

            // Assert
            assertThat(result.customerId.value).isEqualTo("cust-001")
            assertThat(result.totalAmount).isEqualTo(11200)
            assertThat(savedLedger.captured).isNotNull()
        }

    @Test
    fun shouldThrowIllegalState_WhenArAccountIsMissing() {
        // Arrange
        coEvery { invoiceRepo.save(any()) } returnsArgument 0
        coEvery { accountRepo.findByCode("1100") } returns null

        // Act / Assert
        assertThatThrownBy { runBlocking { useCase.execute(validRequest) } }
            .isInstanceOf(IllegalStateException::class.java)
    }

    private fun sampleAccount(
        code: String,
        type: AccountType,
    ) = Account(
        id = AccountId(code),
        accountCode = code,
        accountName = "Account $code",
        accountType = type,
    )
}
