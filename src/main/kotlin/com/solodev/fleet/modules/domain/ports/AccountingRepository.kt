package com.solodev.fleet.modules.domain.ports

import com.solodev.fleet.modules.domain.models.*
import java.time.Instant

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

/**
 * Repository interface for LedgerEntry persistence.
 */
interface LedgerRepository {
    /**
     * Find a ledger entry by its unique identifier.
     */
    suspend fun findById(id: LedgerEntryId): LedgerEntry?

    /**
     * Find a ledger entry by its external reference (for idempotency).
     */
    suspend fun findByExternalReference(externalReference: String): LedgerEntry?

    /**
     * Save a new ledger entry with its lines (atomic transaction).
     * This enforces double-entry bookkeeping rules.
     */
    suspend fun save(entry: LedgerEntry): LedgerEntry

    /**
     * Find ledger entries for a specific account.
     */
    suspend fun findByAccountId(accountId: AccountId): List<LedgerEntry>

    /**
     * Find ledger entries between dates.
     */
    suspend fun findByDateRange(startDate: Instant, endDate: Instant): List<LedgerEntry>

    /**
     * Calculate account balance up to a specific date.
     */
    suspend fun calculateAccountBalance(accountId: AccountId, upToDate: Instant): Long
}

/**
 * Repository interface for Invoice persistence.
 */
interface InvoiceRepository {
    /**
     * Find an invoice by its unique identifier.
     */
    suspend fun findById(id: java.util.UUID): Invoice?

    /**
     * Find an invoice by its invoice number.
     */
    suspend fun findByInvoiceNumber(invoiceNumber: String): Invoice?

    /**
     * Save a new invoice or update an existing one.
     */
    suspend fun save(invoice: Invoice): Invoice

    /**
     * Find all invoices for a specific customer.
     */
    suspend fun findByCustomerId(customerId: CustomerId): List<Invoice>

    /**
     * Find invoices by status.
     */
    suspend fun findByStatus(status: InvoiceStatus): List<Invoice>

    /**
     * Find overdue invoices.
     */
    suspend fun findOverdue(): List<Invoice>
}
