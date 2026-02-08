package com.solodev.fleet.modules.accounting.domain.repository

import com.solodev.fleet.modules.accounting.domain.model.Account
import com.solodev.fleet.modules.accounting.domain.model.AccountId
import com.solodev.fleet.modules.accounting.domain.model.AccountType
import com.solodev.fleet.modules.accounting.domain.model.Invoice
import com.solodev.fleet.modules.accounting.domain.model.InvoiceStatus
import com.solodev.fleet.modules.accounting.domain.model.LedgerEntry
import com.solodev.fleet.modules.accounting.domain.model.LedgerEntryId
import com.solodev.fleet.modules.accounting.domain.model.LedgerEntryLine
import com.solodev.fleet.modules.accounting.infrastructure.persistence.AccountsTable
import com.solodev.fleet.modules.accounting.infrastructure.persistence.InvoicesTable
import com.solodev.fleet.modules.accounting.infrastructure.persistence.LedgerEntriesTable
import com.solodev.fleet.modules.accounting.infrastructure.persistence.LedgerEntryLinesTable
import com.solodev.fleet.modules.rentals.domain.model.CustomerId
import com.solodev.fleet.modules.rentals.domain.model.RentalId
import java.time.Instant
import java.util.*
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * PostgreSQL implementation of AccountRepository using Exposed ORM.
 */
class AccountRepositoryImpl : AccountRepository {

    private suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }

    private fun ResultRow.toAccount() = Account(
        id = AccountId(this[AccountsTable.id].value.toString()),
        accountCode = this[AccountsTable.accountCode],
        accountName = this[AccountsTable.accountName],
        accountType = AccountType.valueOf(this[AccountsTable.accountType]),
        parentAccountId = this[AccountsTable.parentAccountId]?.value?.toString()?.let { AccountId(it) },
        isActive = this[AccountsTable.isActive],
        description = this[AccountsTable.description]
    )

    override suspend fun findById(id: AccountId): Account? = dbQuery {
        AccountsTable.selectAll().where { AccountsTable.id eq UUID.fromString(id.value) }
            .map { it.toAccount() }
            .singleOrNull()
    }

    override suspend fun findByCode(accountCode: String): Account? = dbQuery {
        AccountsTable.selectAll().where { AccountsTable.accountCode eq accountCode }
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
        AccountsTable.selectAll().where { AccountsTable.accountType eq accountType.name }
            .map { it.toAccount() }
    }

    override suspend fun findAllActive(): List<Account> = dbQuery {
        AccountsTable.selectAll().where { AccountsTable.isActive eq true }
            .map { it.toAccount() }
    }

    override suspend fun findAll(): List<Account> = dbQuery {
        AccountsTable.selectAll().map { it.toAccount() }
    }
}

/**
 * PostgreSQL implementation of LedgerRepository using Exposed ORM.
 */
class LedgerRepositoryImpl : LedgerRepository {

    private suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }

    private suspend fun ResultRow.toLedgerEntry(): LedgerEntry {
        val entryId = LedgerEntryId(this[LedgerEntriesTable.id].value.toString())
        
        // Fetch associated lines
        val lines =
            LedgerEntryLinesTable.selectAll().where { LedgerEntryLinesTable.entryId eq UUID.fromString(entryId.value) }
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

    private fun ResultRow.toLedgerEntryLine() = LedgerEntryLine(
        id = this[LedgerEntryLinesTable.id].value,
        entryId = LedgerEntryId(this[LedgerEntryLinesTable.entryId].value.toString()),
        accountId = AccountId(this[LedgerEntryLinesTable.accountId].value.toString()),
        debitAmountCents = this[LedgerEntryLinesTable.debitAmountCents],
        creditAmountCents = this[LedgerEntryLinesTable.creditAmountCents],
        currencyCode = this[LedgerEntryLinesTable.currencyCode],
        description = this[LedgerEntryLinesTable.description]
    )

    override suspend fun findById(id: LedgerEntryId): LedgerEntry? = dbQuery {
        LedgerEntriesTable.selectAll().where { LedgerEntriesTable.id eq UUID.fromString(id.value) }
            .map { it.toLedgerEntry() }
            .singleOrNull()
    }

    override suspend fun findByExternalReference(externalReference: String): LedgerEntry? = dbQuery {
        LedgerEntriesTable.selectAll().where { LedgerEntriesTable.externalReference eq externalReference }
            .map { it.toLedgerEntry() }
            .singleOrNull()
    }

    override suspend fun save(entry: LedgerEntry): LedgerEntry = dbQuery {
        val entryUuid = UUID.fromString(entry.id.value)
        val now = Instant.now()

        // Check if entry exists
        val exists = LedgerEntriesTable.selectAll().where { LedgerEntriesTable.id eq entryUuid }.count() > 0

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

    override suspend fun findByAccountId(accountId: AccountId): List<LedgerEntry> = dbQuery {
        val accountUuid = UUID.fromString(accountId.value)
        
        // Find all entry IDs that have lines for this account
        val entryIds = LedgerEntryLinesTable
            .select(LedgerEntryLinesTable.entryId)
            .where { LedgerEntryLinesTable.accountId eq accountUuid }
            .map { it[LedgerEntryLinesTable.entryId].value }
            .distinct()

        // Fetch the full entries
        LedgerEntriesTable.selectAll().where { LedgerEntriesTable.id inList entryIds }
            .map { it.toLedgerEntry() }
    }

    override suspend fun findByDateRange(startDate: Instant, endDate: Instant): List<LedgerEntry> = dbQuery {
        LedgerEntriesTable.selectAll().where {
            (LedgerEntriesTable.entryDate greaterEq startDate) and
                    (LedgerEntriesTable.entryDate lessEq endDate)
        }
            .orderBy(LedgerEntriesTable.entryDate to SortOrder.ASC)
            .map { it.toLedgerEntry() }
    }

    override suspend fun calculateAccountBalance(accountId: AccountId, upToDate: Instant): Long = dbQuery {
        val accountUuid = UUID.fromString(accountId.value)

        // Get all lines for this account up to the date
        val entryIds = LedgerEntriesTable
            .select(LedgerEntriesTable.id)
            .where { LedgerEntriesTable.entryDate lessEq upToDate }
            .map { it[LedgerEntriesTable.id].value }

        val lines = LedgerEntryLinesTable.selectAll().where {
            (LedgerEntryLinesTable.accountId eq accountUuid) and
                    (LedgerEntryLinesTable.entryId inList entryIds)
        }

        val totalDebits = lines.sumOf { it[LedgerEntryLinesTable.debitAmountCents].toLong() }
        val totalCredits = lines.sumOf { it[LedgerEntryLinesTable.creditAmountCents].toLong() }

        totalDebits - totalCredits
    }
}

/**
 * PostgreSQL implementation of InvoiceRepository using Exposed ORM.
 */
class InvoiceRepositoryImpl : InvoiceRepository {

    private suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }

    private fun ResultRow.toInvoice() = Invoice(
        id = this[InvoicesTable.id].value,
        invoiceNumber = this[InvoicesTable.invoiceNumber],
        customerId = CustomerId(this[InvoicesTable.customerId].value.toString()),
        rentalId = this[InvoicesTable.rentalId]?.value?.toString()?.let { RentalId(it) },
        status = InvoiceStatus.valueOf(this[InvoicesTable.status]),
        subtotalCents = this[InvoicesTable.subtotalCents],
        taxCents = this[InvoicesTable.taxCents],
        paidCents = this[InvoicesTable.paidCents],
        currencyCode = this[InvoicesTable.currencyCode],
        issueDate = this[InvoicesTable.issueDate].atStartOfDay().toInstant(ZoneOffset.UTC),
        dueDate = this[InvoicesTable.dueDate].atStartOfDay().toInstant(ZoneOffset.UTC),
        paidDate = this[InvoicesTable.paidDate]?.atStartOfDay()?.toInstant(ZoneOffset.UTC),
        notes = this[InvoicesTable.notes]
    )

    override suspend fun findById(id: UUID): Invoice? = dbQuery {
        InvoicesTable.selectAll().where { InvoicesTable.id eq id }
            .map { it.toInvoice() }
            .singleOrNull()
    }

    override suspend fun findByInvoiceNumber(invoiceNumber: String): Invoice? = dbQuery {
        InvoicesTable.selectAll().where { InvoicesTable.invoiceNumber eq invoiceNumber }
            .map { it.toInvoice() }
            .singleOrNull()
    }

    override suspend fun save(invoice: Invoice): Invoice = dbQuery {
        val now = Instant.now()

        val exists = InvoicesTable.selectAll().where { InvoicesTable.id eq invoice.id }.count() > 0

        if (exists) {
            InvoicesTable.update({ InvoicesTable.id eq invoice.id }) {
                it[invoiceNumber] = invoice.invoiceNumber
                it[customerId] = UUID.fromString(invoice.customerId.value)
                it[rentalId] = invoice.rentalId?.value?.let { UUID.fromString(it) }
                it[status] = invoice.status.name
                it[subtotalCents] = invoice.subtotalCents
                it[taxCents] = invoice.taxCents
                it[paidCents] = invoice.paidCents
                it[currencyCode] = invoice.currencyCode
                it[issueDate] = LocalDate.ofInstant(invoice.issueDate, ZoneOffset.UTC)
                it[dueDate] = LocalDate.ofInstant(invoice.dueDate, ZoneOffset.UTC)
                it[paidDate] = invoice.paidDate?.let { LocalDate.ofInstant(it, ZoneOffset.UTC) }
                it[notes] = invoice.notes
                it[updatedAt] = now
            }
        } else {
            InvoicesTable.insert {
                it[id] = invoice.id
                it[invoiceNumber] = invoice.invoiceNumber
                it[customerId] = UUID.fromString(invoice.customerId.value)
                it[rentalId] = invoice.rentalId?.value?.let { UUID.fromString(it) }
                it[status] = invoice.status.name
                it[subtotalCents] = invoice.subtotalCents
                it[taxCents] = invoice.taxCents
                it[paidCents] = invoice.paidCents
                it[currencyCode] = invoice.currencyCode
                it[issueDate] = LocalDate.ofInstant(invoice.issueDate, ZoneOffset.UTC)
                it[dueDate] = LocalDate.ofInstant(invoice.dueDate, ZoneOffset.UTC)
                it[paidDate] = invoice.paidDate?.let { LocalDate.ofInstant(it, ZoneOffset.UTC) }
                it[notes] = invoice.notes
                it[createdAt] = now
                it[updatedAt] = now
            }
        }

        invoice
    }

    override suspend fun findByCustomerId(customerId: CustomerId): List<Invoice> = dbQuery {
        InvoicesTable.selectAll().where { InvoicesTable.customerId eq UUID.fromString(customerId.value) }
            .orderBy(InvoicesTable.issueDate to SortOrder.DESC)
            .map { it.toInvoice() }
    }

    override suspend fun findByStatus(status: InvoiceStatus): List<Invoice> = dbQuery {
        InvoicesTable.selectAll().where { InvoicesTable.status eq status.name }
            .orderBy(InvoicesTable.dueDate to SortOrder.ASC)
            .map { it.toInvoice() }
    }

    override suspend fun findOverdue(): List<Invoice> = dbQuery {
        val today = LocalDate.now()
        InvoicesTable.selectAll().where {
            (InvoicesTable.status inList listOf(InvoiceStatus.ISSUED.name, InvoiceStatus.OVERDUE.name)) and
                    (InvoicesTable.dueDate less today)
        }
            .orderBy(InvoicesTable.dueDate to SortOrder.ASC)
            .map { it.toInvoice() }
    }
}
