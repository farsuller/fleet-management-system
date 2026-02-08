package com.solodev.fleet.modules.accounts.domain.repository

import com.solodev.fleet.modules.accounts.domain.model.Account
import com.solodev.fleet.modules.accounts.domain.model.AccountId
import com.solodev.fleet.modules.accounts.domain.model.AccountType

/**
 * Repository interface for Account persistence.
 */
interface AccountRepository {
    /**
     * Find an account by its unique identifier.
     */
    suspend fun findById(id: AccountId): Account?

    /**
     * Find an account by its account code.
     */
    suspend fun findByCode(accountCode: String): Account?

    /**
     * Save a new account or update an existing one.
     */
    suspend fun save(account: Account): Account

    /**
     * Find all accounts of a specific type.
     */
    suspend fun findByType(accountType: AccountType): List<Account>

    /**
     * Find all active accounts.
     */
    suspend fun findAllActive(): List<Account>

    /**
     * Find all accounts.
     */
    suspend fun findAll(): List<Account>
}