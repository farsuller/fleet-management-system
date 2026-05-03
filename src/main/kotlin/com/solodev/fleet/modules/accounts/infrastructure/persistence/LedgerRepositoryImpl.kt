package com.solodev.fleet.modules.accounts.infrastructure.persistence

import com.solodev.fleet.modules.accounts.domain.model.AccountId
import com.solodev.fleet.modules.accounts.domain.model.LedgerEntry
import com.solodev.fleet.modules.accounts.domain.repository.LedgerRepository
import com.solodev.fleet.shared.helpers.dbQuery
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.sum
import java.time.Instant
import java.util.UUID

class LedgerRepositoryImpl : LedgerRepository {
    override suspend fun calculateSumForReference(
        reference: String,
        accountId: AccountId,
    ): Long =
        dbQuery {
            val accountUuid = UUID.fromString(accountId.value)
            LedgerEntryLinesTable
                .innerJoin(LedgerEntriesTable)
                .select(
                    LedgerEntryLinesTable.creditAmount.sum(),
                    LedgerEntryLinesTable.debitAmount.sum(),
                ).where {
                    (LedgerEntriesTable.externalReference eq reference) and
                        (LedgerEntryLinesTable.accountId eq accountUuid)
                }.map {
                    val credits = it[LedgerEntryLinesTable.creditAmount.sum()] ?: 0
                    val debits = it[LedgerEntryLinesTable.debitAmount.sum()] ?: 0
                    credits.toLong() - debits.toLong()
                }.singleOrNull()
                ?: 0L
        }

    override suspend fun calculateSumForPartialReference(
        prefix: String,
        accountId: AccountId,
    ): Long =
        dbQuery {
            val accountUuid = UUID.fromString(accountId.value)
            LedgerEntryLinesTable
                .innerJoin(LedgerEntriesTable)
                .select(
                    LedgerEntryLinesTable.creditAmount.sum(),
                    LedgerEntryLinesTable.debitAmount.sum(),
                ).where {
                    (LedgerEntriesTable.externalReference like "$prefix%") and
                        (LedgerEntryLinesTable.accountId eq accountUuid)
                }.map {
                    val credits = it[LedgerEntryLinesTable.creditAmount.sum()] ?: 0
                    val debits = it[LedgerEntryLinesTable.debitAmount.sum()] ?: 0
                    credits.toLong() - debits.toLong()
                }.singleOrNull()
                ?: 0L
        }

    override suspend fun save(entry: LedgerEntry): LedgerEntry =
        dbQuery {
            val entryUuid = UUID.fromString(entry.id.value)
            val now = Instant.now()

            // Check if entry exists
            val exists =
                LedgerEntriesTable
                    .select(LedgerEntriesTable.id)
                    .where { LedgerEntriesTable.id eq entryUuid }
                    .limit(1)
                    .singleOrNull() != null

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

    override suspend fun calculateAccountBalance(
        accountId: AccountId,
        upToDate: Instant,
    ): Long =
        dbQuery {
            val accountUuid = UUID.fromString(accountId.value)

            val (debits, credits) =
                (LedgerEntryLinesTable innerJoin LedgerEntriesTable)
                    .select(
                        LedgerEntryLinesTable.debitAmount.sum(),
                        LedgerEntryLinesTable.creditAmount.sum(),
                    ).where {
                        (LedgerEntryLinesTable.accountId eq accountUuid) and
                            (LedgerEntriesTable.entryDate lessEq upToDate)
                    }.map {
                        it[LedgerEntryLinesTable.debitAmount.sum()] to it[LedgerEntryLinesTable.creditAmount.sum()]
                    }.singleOrNull() ?: (0L to 0L)

            (debits ?: 0L).toLong() - (credits ?: 0L).toLong()
        }

    override suspend fun calculateAccountBalanceBetween(
        accountId: AccountId,
        from: Instant,
        to: Instant,
    ): Long =
        dbQuery {
            val accountUuid = UUID.fromString(accountId.value)

            val (debits, credits) =
                (LedgerEntryLinesTable innerJoin LedgerEntriesTable)
                    .select(
                        LedgerEntryLinesTable.debitAmount.sum(),
                        LedgerEntryLinesTable.creditAmount.sum(),
                    ).where {
                        (LedgerEntryLinesTable.accountId eq accountUuid) and
                            (LedgerEntriesTable.entryDate greaterEq from) and
                            (LedgerEntriesTable.entryDate less to)
                    }.map {
                        it[LedgerEntryLinesTable.debitAmount.sum()] to it[LedgerEntryLinesTable.creditAmount.sum()]
                    }.singleOrNull() ?: (0L to 0L)

            (debits ?: 0L).toLong() - (credits ?: 0L).toLong()
        }

    override suspend fun getRevenueLinesInRange(
        accountIds: List<AccountId>,
        from: Instant,
        to: Instant,
    ): List<Pair<Instant, Long>> =
        dbQuery {
            if (accountIds.isEmpty()) return@dbQuery emptyList()
            val uuids = accountIds.map { UUID.fromString(it.value) }
            (LedgerEntryLinesTable innerJoin LedgerEntriesTable)
                .select(
                    LedgerEntriesTable.entryDate,
                    LedgerEntryLinesTable.debitAmount,
                    LedgerEntryLinesTable.creditAmount,
                ).where {
                    (LedgerEntryLinesTable.accountId inList uuids) and
                        (LedgerEntriesTable.entryDate greaterEq from) and
                        (LedgerEntriesTable.entryDate less to)
                }.map { row ->
                    val date = row[LedgerEntriesTable.entryDate]
                    val credit = row[LedgerEntryLinesTable.creditAmount].toLong()
                    val debit = row[LedgerEntryLinesTable.debitAmount].toLong()
                    date to (credit - debit)
                }
        }
}
