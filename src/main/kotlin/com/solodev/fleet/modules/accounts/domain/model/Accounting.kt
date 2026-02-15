package com.solodev.fleet.modules.accounts.domain.model

import com.solodev.fleet.modules.rentals.domain.model.CustomerId
import com.solodev.fleet.modules.rentals.domain.model.RentalId
import java.time.Instant
import java.util.*

/** Value object representing a unique account identifier. */
@JvmInline
value class AccountId(val value: String) {
    init {
        require(value.isNotBlank()) { "Account ID cannot be blank" }
    }
}

/** Value object representing a unique ledger entry identifier. */
@JvmInline
value class LedgerEntryId(val value: String) {
    init {
        require(value.isNotBlank()) { "Ledger entry ID cannot be blank" }
    }
}

/** Account type in the chart of accounts. */
enum class AccountType {
    ASSET,
    LIABILITY,
    EQUITY,
    REVENUE,
    EXPENSE
}

/** Invoice status. */
enum class InvoiceStatus {
    DRAFT,
    ISSUED,
    PAID,
    OVERDUE,
    CANCELLED
}

/**
 * Account domain entity.
 *
 * Represents an account in the chart of accounts.
 */
data class Account(
        val id: AccountId,
        val accountCode: String,
        val accountName: String,
        val accountType: AccountType,
        val parentAccountId: AccountId? = null,
        val isActive: Boolean = true,
        val description: String? = null
) {
    init {
        require(accountCode.isNotBlank()) { "Account code cannot be blank" }
        require(accountName.isNotBlank()) { "Account name cannot be blank" }
    }
}

/**
 * Ledger entry domain entity.
 *
 * Represents a double-entry bookkeeping journal entry.
 */
data class LedgerEntry(
        val id: LedgerEntryId,
        val entryNumber: String,
        val externalReference: String,
        val entryDate: Instant,
        val description: String,
        val lines: List<LedgerEntryLine> = emptyList(),
        val createdByUserId: UUID? = null
) {
    init {
        require(entryNumber.isNotBlank()) { "Entry number cannot be blank" }
        require(externalReference.isNotBlank()) { "External reference cannot be blank" }
        require(description.isNotBlank()) { "Description cannot be blank" }

        // Validate double-entry balance
        if (lines.isNotEmpty()) {
            val totalDebits = lines.sumOf { it.debitAmount }
            val totalCredits = lines.sumOf { it.creditAmount }
            require(totalDebits == totalCredits) {
                "Ledger entry must be balanced: debits=$totalDebits, credits=$totalCredits"
            }
        }
    }

    /** Check if this entry is balanced. */
    fun isBalanced(): Boolean {
        val totalDebits = lines.sumOf { it.debitAmount }
        val totalCredits = lines.sumOf { it.creditAmount }
        return totalDebits == totalCredits
    }
}

/**
 * Ledger entry line.
 *
 * Represents a single debit or credit line in a journal entry.
 */
data class LedgerEntryLine(
        val id: UUID,
        val entryId: LedgerEntryId,
        val accountId: AccountId,
        val debitAmount: Int = 0,
        val creditAmount: Int = 0,
        val currencyCode: String = "PHP",
        val description: String? = null
) {
    init {
        require(debitAmount >= 0) { "Debit amount cannot be negative" }
        require(creditAmount >= 0) { "Credit amount cannot be negative" }
        require((debitAmount > 0 && creditAmount == 0) || (debitAmount == 0 && creditAmount > 0)) {
            "Each line must be either debit or credit, not both"
        }
    }

    val isDebit: Boolean
        get() = debitAmount > 0

    val isCredit: Boolean
        get() = creditAmount > 0

    val amount: Int
        get() = if (isDebit) debitAmount else creditAmount
}

/** Invoice domain entity. */
data class Invoice(
        val id: UUID,
        val invoiceNumber: String,
        val customerId: CustomerId,
        val rentalId: RentalId? = null,
        val status: InvoiceStatus,
        val subtotal: Int,
        val tax: Int = 0,
        val paidAmount: Int = 0,
        val currencyCode: String = "PHP",
        val issueDate: Instant,
        val dueDate: Instant,
        val paidDate: Instant? = null,
        val notes: String? = null
) {
    init {
        require(invoiceNumber.isNotBlank()) { "Invoice number cannot be blank" }
        require(subtotal >= 0) { "Subtotal cannot be negative" }
        require(tax >= 0) { "Tax cannot be negative" }
        require(paidAmount >= 0) { "Paid amount cannot be negative" }
        require(dueDate.isAfter(issueDate) || dueDate == issueDate) {
            "Due date must be on or after issue date"
        }
    }

    val totalAmount: Int
        get() = subtotal + tax

    val balance: Int
        get() = totalAmount - paidAmount

    val isPaid: Boolean
        get() = balance == 0

    val isOverdue: Boolean
        get() = !isPaid && Instant.now().isAfter(dueDate)
}

/** Payment domain entity. */
data class Payment(
        val id: UUID,
        val paymentNumber: String,
        val customerId: CustomerId,
        val invoiceId: UUID?,
        val amount: Int,
        val paymentMethod: String,
        val transactionReference: String? = null,
        val status: PaymentStatus,
        val paymentDate: Instant,
        val notes: String? = null
)

/** Payment status. */
enum class PaymentStatus {
    PENDING,
    COMPLETED,
    FAILED,
    REFUNDED
}

/** Result object for payment processing. */
data class PaymentReceipt(val message: String, val payment: Payment, val updatedInvoice: Invoice)
