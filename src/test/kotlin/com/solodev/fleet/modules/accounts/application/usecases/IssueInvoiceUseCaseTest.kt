package com.solodev.fleet.modules.accounts.application.usecases

import com.solodev.fleet.modules.accounts.application.dto.InvoiceRequest
import com.solodev.fleet.modules.accounts.domain.model.*
import com.solodev.fleet.modules.accounts.domain.repository.*
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class IssueInvoiceUseCaseTest {

    private val invoiceRepo = mockk<InvoiceRepository>()
    private val accountRepo = mockk<AccountRepository>()
    private val ledgerRepo = mockk<LedgerRepository>()
    private val useCase = IssueInvoiceUseCase(invoiceRepo, accountRepo, ledgerRepo)

    @Test
    fun `issues invoice and posts double-entry DR AR 1100 CR Revenue 4000`() = runBlocking {
        val arAccount = sampleAccount("1100", AccountType.ASSET)
        val revenueAccount = sampleAccount("4000", AccountType.REVENUE)

        coEvery { accountRepo.findByCode("1100") } returns arAccount
        coEvery { accountRepo.findByCode("4000") } returns revenueAccount
        coEvery { invoiceRepo.save(any()) } returnsArgument 0
        coEvery { ledgerRepo.save(any()) } returnsArgument 0

        val request = InvoiceRequest(
            customerId = "cust-001",
            subtotal = 10000,
            tax = 1200,
            dueDate = "2027-12-31T00:00:00Z"
        )
        val result = useCase.execute(request)

        assertEquals("cust-001", result.customerId.value)
        assertEquals(11200, result.totalAmount)
        coVerify { ledgerRepo.save(any()) }
    }

    @Test
    fun `throws when AR account 1100 is missing`(): Unit = runBlocking {
        coEvery { invoiceRepo.save(any()) } returnsArgument 0
        coEvery { accountRepo.findByCode("1100") } returns null

        val request = InvoiceRequest(
            customerId = "cust-001",
            subtotal = 10000,
            tax = 1200,
            dueDate = "2027-12-31T00:00:00Z"
        )
        assertFailsWith<IllegalStateException> {
            useCase.execute(request)
        }
    }

    private fun sampleAccount(code: String, type: AccountType) = Account(
        id = AccountId(code),
        accountCode = code,
        accountName = "Account $code",
        accountType = type
    )
}
