package com.solodev.fleet.modules.accounts.application.usecases

import com.solodev.fleet.modules.accounts.application.dto.AccountRequest
import com.solodev.fleet.modules.accounts.domain.model.Account
import com.solodev.fleet.modules.accounts.domain.model.AccountId
import com.solodev.fleet.modules.accounts.domain.model.AccountType
import com.solodev.fleet.modules.accounts.domain.repository.AccountRepository
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class ManageAccountUseCaseTest {

    private val repository = mockk<AccountRepository>()
    private val useCase = ManageAccountUseCase(repository)

    private val existingAccount = Account(
        id = AccountId("account-1"),
        accountCode = "1100",
        accountName = "Accounts Receivable",
        accountType = AccountType.ASSET,
        isActive = true
    )

    @Test
    fun shouldCreateAccount_WhenRequestIsValid() = runBlocking {
        // Arrange
        val request = AccountRequest(
            accountCode = "5000",
            accountName = "Operating Expenses",
            accountType = "EXPENSE"
        )
        val savedSlot = slot<Account>()
        coEvery { repository.save(capture(savedSlot)) } returnsArgument 0

        // Act
        val result = useCase.create(request)

        // Assert
        assertThat(result.accountCode).isEqualTo("5000")
        assertThat(result.accountName).isEqualTo("Operating Expenses")
        assertThat(result.accountType).isEqualTo(AccountType.EXPENSE)
        assertThat(savedSlot.captured.isActive).isTrue()
    }

    @Test
    fun shouldUpdateAccount_WhenAccountExists() = runBlocking {
        // Arrange
        val request = AccountRequest(
            accountCode = "1100",
            accountName = "Trade Receivables",
            accountType = "ASSET"
        )
        val savedSlot = slot<Account>()
        coEvery { repository.findById(AccountId("account-1")) } returns existingAccount
        coEvery { repository.save(capture(savedSlot)) } returnsArgument 0

        // Act
        val result = useCase.update("account-1", request)

        // Assert
        assertThat(result.accountName).isEqualTo("Trade Receivables")
        assertThat(savedSlot.captured.accountName).isEqualTo("Trade Receivables")
        assertThat(savedSlot.captured.accountCode).isEqualTo("1100") // unchanged
    }

    @Test
    fun shouldThrowNoSuchElement_WhenUpdatingNonExistentAccount() {
        // Arrange
        val request = AccountRequest(
            accountCode = "9999",
            accountName = "Ghost Account",
            accountType = "ASSET"
        )
        coEvery { repository.findById(AccountId("unknown")) } returns null

        // Act + Assert
        assertThatThrownBy { runBlocking { useCase.update("unknown", request) } }
            .isInstanceOf(NoSuchElementException::class.java)
            .hasMessageContaining("Account not found")
    }

    @Test
    fun shouldReturnTrue_WhenDeletingExistingAccount() = runBlocking {
        // Arrange
        coEvery { repository.delete(AccountId("account-1")) } returns true

        // Act
        val result = useCase.delete("account-1")

        // Assert
        assertThat(result).isTrue()
    }

    @Test
    fun shouldReturnFalse_WhenDeletingNonExistentAccount() = runBlocking {
        // Arrange
        coEvery { repository.delete(AccountId("unknown")) } returns false

        // Act
        val result = useCase.delete("unknown")

        // Assert
        assertThat(result).isFalse()
    }
}
