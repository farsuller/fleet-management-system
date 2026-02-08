package com.solodev.fleet.modules.accounts.infrastructure.persistence

import com.solodev.fleet.modules.accounts.domain.model.AccountId
import com.solodev.fleet.modules.accounts.domain.model.LedgerEntry
import com.solodev.fleet.modules.accounts.domain.model.LedgerEntryId
import com.solodev.fleet.modules.accounts.domain.model.LedgerEntryLine
import com.solodev.fleet.modules.accounts.domain.repository.LedgerRepository
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.Instant
import java.util.*

class LedgerRepositoryImpl : LedgerRepository {

    private suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }

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
            debitAmountCents = this[LedgerEntryLinesTable.debitAmountCents],
            creditAmountCents = this[LedgerEntryLinesTable.creditAmountCents],
            currencyCode = this[LedgerEntryLinesTable.currencyCode],
            description = this[LedgerEntryLinesTable.description]
        )

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
                    it[debitAmountCents] = line.debitAmountCents
                    it[creditAmountCents] = line.creditAmountCents
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
                    LedgerEntryLinesTable.debitAmountCents,
                    LedgerEntryLinesTable.creditAmountCents
                )
                .where {
                    (LedgerEntryLinesTable.accountId eq accountUuid) and
                            (LedgerEntriesTable.entryDate lessEq upToDate)
                }
                .fold(0L) { acc, row ->
                    acc + row[LedgerEntryLinesTable.debitAmountCents].toLong() -
                            row[LedgerEntryLinesTable.creditAmountCents].toLong()
                }
        }
}
