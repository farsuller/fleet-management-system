package com.solodev.fleet.modules.accounts.infrastructure.persistence

import com.solodev.fleet.modules.accounts.domain.model.Account
import com.solodev.fleet.modules.accounts.domain.model.AccountId
import com.solodev.fleet.modules.accounts.domain.model.AccountType
import com.solodev.fleet.modules.accounts.domain.repository.AccountRepository
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.update
import java.time.Instant
import java.util.*

/** PostgreSQL implementation of AccountRepository using Exposed ORM. */
class AccountRepositoryImpl : AccountRepository {

    private suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }

    private fun ResultRow.toAccount() =
        Account(
            id = AccountId(this[AccountsTable.id].value.toString()),
            accountCode = this[AccountsTable.accountCode],
            accountName = this[AccountsTable.accountName],
            accountType = AccountType.valueOf(this[AccountsTable.accountType]),
            parentAccountId =
                this[AccountsTable.parentAccountId]?.value?.toString()?.let {
                    AccountId(it)
                },
            isActive = this[AccountsTable.isActive],
            description = this[AccountsTable.description]
        )

    override suspend fun findById(id: AccountId): Account? = dbQuery {
        AccountsTable.selectAll()
            .where { AccountsTable.id eq UUID.fromString(id.value) }
            .map { it.toAccount() }
            .singleOrNull()
    }

    override suspend fun findByCode(accountCode: String): Account? = dbQuery {
        AccountsTable.selectAll()
            .where { AccountsTable.accountCode eq accountCode }
            .map { it.toAccount() }
            .singleOrNull()
    }

    override suspend fun save(account: Account): Account = dbQuery {
        val accountUuid = UUID.fromString(account.id.value)
        val now = Instant.now()

        val exists = AccountsTable.selectAll().where { AccountsTable.id eq accountUuid }.count() > 0

        if (exists) {
            AccountsTable.update({ AccountsTable.id eq accountUuid }) {
                it[accountCode] = account.accountCode
                it[accountName] = account.accountName
                it[accountType] = account.accountType.name
                it[parentAccountId] = account.parentAccountId?.value?.let { UUID.fromString(it) }
                it[isActive] = account.isActive
                it[description] = account.description
                it[updatedAt] = now
            }
        } else {
            AccountsTable.insert {
                it[id] = accountUuid
                it[accountCode] = account.accountCode
                it[accountName] = account.accountName
                it[accountType] = account.accountType.name
                it[parentAccountId] = account.parentAccountId?.value?.let { UUID.fromString(it) }
                it[isActive] = account.isActive
                it[description] = account.description
                it[createdAt] = now
                it[updatedAt] = now
            }
        }

        account
    }

    override suspend fun findByType(accountType: AccountType): List<Account> = dbQuery {
        AccountsTable.selectAll().where { AccountsTable.accountType eq accountType.name }.map {
            it.toAccount()
        }
    }

    override suspend fun findAllActive(): List<Account> = dbQuery {
        AccountsTable.selectAll().where { AccountsTable.isActive eq true }.map { it.toAccount() }
    }

    override suspend fun findAll(): List<Account> = dbQuery {
        AccountsTable.selectAll().map { it.toAccount() }
    }
}
