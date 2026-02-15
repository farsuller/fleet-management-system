package com.solodev.fleet.modules.accounts.application.usecases

import com.solodev.fleet.modules.accounts.application.dto.AccountRequest
import com.solodev.fleet.modules.accounts.domain.model.*
import com.solodev.fleet.modules.accounts.domain.repository.AccountRepository
import java.util.UUID

class ManageAccountUseCase(private val accountRepository: AccountRepository) {

    /** Creates a new account in the system. */
    suspend fun create(request: AccountRequest): Account {
        val account = Account(
            id = AccountId(UUID.randomUUID().toString()),
            accountCode = request.accountCode,
            accountName = request.accountName,
            accountType = AccountType.valueOf(request.accountType.uppercase()),
            description = request.description,
            isActive = true
        )
        return accountRepository.save(account)
    }

    /** Updates an existing account's details. */
    suspend fun update(id: String, request: AccountRequest): Account {
        val existing = accountRepository.findById(AccountId(id)) ?: throw NoSuchElementException("Account not found")
        val updated = existing.copy(
            accountName = request.accountName,
            accountType = AccountType.valueOf(request.accountType.uppercase()),
            description = request.description,
            isActive = request.isActive
        )
        return accountRepository.save(updated)
    }

    /** Deletes an account (note: usually restricted if the account has entries). */
    suspend fun delete(id: String): Boolean {
        return accountRepository.delete(AccountId(id))
    }
}