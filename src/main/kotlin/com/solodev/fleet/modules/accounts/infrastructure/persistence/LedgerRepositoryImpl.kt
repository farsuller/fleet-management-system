package com.solodev.fleet.modules.accounts.infrastructure.persistence

import com.solodev.fleet.modules.accounts.domain.model.AccountId
import com.solodev.fleet.modules.accounts.domain.model.LedgerEntry
import com.solodev.fleet.modules.accounts.domain.model.LedgerEntryId
import com.solodev.fleet.modules.accounts.domain.model.LedgerEntryLine
import com.solodev.fleet.modules.accounts.domain.repository.LedgerRepository
import com.solodev.fleet.shared.helpers.dbQuery
import java.time.Instant
import java.util.*
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.sum

class LedgerRepositoryImpl : LedgerRepository {

    private suspend fun ResultRow.toLedgerEntry(): LedgerEntry {
        val entryId = LedgerEntryId(this[LedgerEntriesTable.id].value.toString())

        // Fetch associated lines
        val lines =
                LedgerEntryLinesTable.selectAll()
                        .where { LedgerEntryLinesTable.entryId eq UUID.fromString(entryId.value) }
                        .map { it.toLedgerEntryLine() }

        return LedgerEntry(
                id = entryId,
                entryNumber = this[LedgerEntriesTable.entryNumber],
                externalReference = this[LedgerEntriesTable.externalReference],
                entryDate = this[LedgerEntriesTable.entryDate],
                description = this[LedgerEntriesTable.description],
                lines = lines,
                createdByUserId = this[LedgerEntriesTable.createdByUserId]
        )
    }

    private fun ResultRow.toLedgerEntryLine() =
            LedgerEntryLine(
                    id = this[LedgerEntryLinesTable.id].value,
                    entryId = LedgerEntryId(this[LedgerEntryLinesTable.entryId].value.toString()),
                    accountId = AccountId(this[LedgerEntryLinesTable.accountId].value.toString()),
                    debitAmount = this[LedgerEntryLinesTable.debitAmount],
                    creditAmount = this[LedgerEntryLinesTable.creditAmount],
                    currencyCode = this[LedgerEntryLinesTable.currencyCode],
                    description = this[LedgerEntryLinesTable.description]
            )

    override suspend fun calculateSumForReference(reference: String, accountId: AccountId): Long =
            dbQuery {
                val accountUuid = UUID.fromString(accountId.value)
                LedgerEntryLinesTable.innerJoin(LedgerEntriesTable)
                        .select(
                                LedgerEntryLinesTable.creditAmount.sum(),
                                LedgerEntryLinesTable.debitAmount.sum()
                        )
                        .where {
                            (LedgerEntriesTable.externalReference eq reference) and
                                    (LedgerEntryLinesTable.accountId eq accountUuid)
                        }
                        .map {
                            val credits = it[LedgerEntryLinesTable.creditAmount.sum()] ?: 0
                            val debits = it[LedgerEntryLinesTable.debitAmount.sum()] ?: 0
                            credits.toLong() - debits.toLong()
                        }
                        .singleOrNull()
                        ?: 0L
            }

    override suspend fun calculateSumForPartialReference(
            prefix: String,
            accountId: AccountId
    ): Long = dbQuery {
        val accountUuid = UUID.fromString(accountId.value)
        LedgerEntryLinesTable.innerJoin(LedgerEntriesTable)
                .select(
                        LedgerEntryLinesTable.creditAmount.sum(),
                        LedgerEntryLinesTable.debitAmount.sum()
                )
                .where {
                    (LedgerEntriesTable.externalReference like "$prefix%") and
                            (LedgerEntryLinesTable.accountId eq accountUuid)
                }
                .map {
                    val credits = it[LedgerEntryLinesTable.creditAmount.sum()] ?: 0
                    val debits = it[LedgerEntryLinesTable.debitAmount.sum()] ?: 0
                    credits.toLong() - debits.toLong()
                }
                .singleOrNull()
                ?: 0L
    }

    override suspend fun save(entry: LedgerEntry): LedgerEntry = dbQuery {
        val entryUuid = UUID.fromString(entry.id.value)
        val now = Instant.now()

        // Check if entry exists
        val exists =
                LedgerEntriesTable.selectAll()
                        .where { LedgerEntriesTable.id eq entryUuid }
                        .count() > 0

        if (!exists) {
            // Insert new entry
            LedgerEntriesTable.insert {
                it[id] = entryUuid
                it[entryNumber] = entry.entryNumber
                it[externalReference] = entry.externalReference
                it[entryDate] = entry.entryDate
                it[description] = entry.description
                it[createdByUserId] = entry.createdByUserId
                it[createdAt] = now
            }

            // Insert lines
            entry.lines.forEach { line ->
                LedgerEntryLinesTable.insert {
                    it[id] = line.id
                    it[entryId] = entryUuid
                    it[accountId] = UUID.fromString(line.accountId.value)
                    it[debitAmount] = line.debitAmount
                    it[creditAmount] = line.creditAmount
                    it[currencyCode] = line.currencyCode
                    it[description] = line.description
                }
            }
        }

        entry
    }

    override suspend fun calculateAccountBalance(accountId: AccountId, upToDate: Instant): Long =
            dbQuery {
                val accountUuid = UUID.fromString(accountId.value)

                (LedgerEntryLinesTable innerJoin LedgerEntriesTable)
                        .select(
                                LedgerEntryLinesTable.debitAmount,
                                LedgerEntryLinesTable.creditAmount
                        )
                        .where {
                            (LedgerEntryLinesTable.accountId eq accountUuid) and
                                    (LedgerEntriesTable.entryDate lessEq upToDate)
                        }
                        .map {
                            val debit = it[LedgerEntryLinesTable.debitAmount].toLong()
                            val credit = it[LedgerEntryLinesTable.creditAmount].toLong()
                            debit - credit
                        }
                        .sum()
            }
}
