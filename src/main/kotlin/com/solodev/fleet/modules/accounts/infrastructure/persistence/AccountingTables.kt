package com.solodev.fleet.modules.accounts.infrastructure.persistence

import com.solodev.fleet.modules.rentals.infrastructure.persistence.CustomersTable
import com.solodev.fleet.modules.rentals.infrastructure.persistence.RentalsTable
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.javatime.date
import org.jetbrains.exposed.sql.javatime.timestamp

/** Exposed table definition for accounts (chart of accounts). */
object AccountsTable : UUIDTable("accounts") {
    val accountCode = varchar("account_code", 20).uniqueIndex()
    val accountName = varchar("account_name", 255)
    val accountType = varchar("account_type", 20)
    val parentAccountId =
            reference("parent_account_id", AccountsTable, onDelete = ReferenceOption.SET_NULL)
                    .nullable()
    val isActive = bool("is_active").default(true)
    val description = text("description").nullable()
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
}

/** Exposed table definition for ledger entries. */
object LedgerEntriesTable : UUIDTable("ledger_entries") {
    val entryNumber = varchar("entry_number", 50).uniqueIndex()
    val externalReference = varchar("external_reference", 255).uniqueIndex()
    val entryDate = timestamp("entry_date")
    val description = text("description")
    val createdByUserId = uuid("created_by_user_id").nullable()
    val createdAt = timestamp("created_at")
}

/** Exposed table definition for ledger entry lines. */
object LedgerEntryLinesTable : UUIDTable("ledger_entry_lines") {
    val entryId = reference("entry_id", LedgerEntriesTable, onDelete = ReferenceOption.CASCADE)
    val accountId = reference("account_id", AccountsTable, onDelete = ReferenceOption.RESTRICT)
    val debitAmount = integer("debit_amount").default(0)
    val creditAmount = integer("credit_amount").default(0)
    val currencyCode = varchar("currency_code", 3).default("PHP")
    val description = text("description").nullable()
}

/** Exposed table definition for invoices. */
object InvoicesTable : UUIDTable("invoices") {
    val invoiceNumber = varchar("invoice_number", 50).uniqueIndex()
    val customerId = reference("customer_id", CustomersTable, onDelete = ReferenceOption.RESTRICT)
    val rentalId =
            reference("rental_id", RentalsTable, onDelete = ReferenceOption.SET_NULL).nullable()
    val status = varchar("status", 20)

    // Amounts
    val subtotal = integer("subtotal")
    val tax = integer("tax").default(0)
    val paidAmount = integer("paid_amount").default(0)
    val currencyCode = varchar("currency_code", 3).default("PHP")
    val balance = integer("balance")

    // Dates
    val issueDate = date("issue_date")
    val dueDate = date("due_date")
    val paidDate = date("paid_date").nullable()

    // Metadata
    val notes = text("notes").nullable()
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
}

/** Exposed table definition for invoice line items. */
object InvoiceLineItemsTable : UUIDTable("invoice_line_items") {
    val invoiceId = reference("invoice_id", InvoicesTable, onDelete = ReferenceOption.CASCADE)
    val description = text("description")
    val quantity = decimal("quantity", 10, 2)
    val unitPrice = integer("unit_price")
    val currencyCode = varchar("currency_code", 3).default("PHP")
}

/** Exposed table definition for payments. */
object PaymentsTable : UUIDTable("payments") {
    val paymentNumber = varchar("payment_number", 50).uniqueIndex()
    val customerId = reference("customer_id", CustomersTable, onDelete = ReferenceOption.RESTRICT)
    val invoiceId =
            reference("invoice_id", InvoicesTable, onDelete = ReferenceOption.SET_NULL).nullable()
    val paymentMethod = varchar("payment_method", 50)
    val amount = integer("amount")
    val currencyCode = varchar("currency_code", 3).default("PHP")
    val transactionReference = varchar("transaction_reference", 255).nullable()
    val status = varchar("status", 20)
    val paymentDate = timestamp("payment_date").nullable()
    val notes = text("notes").nullable()
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
}

/** Exposed table definition for payment methods. */
object PaymentMethodsTable : UUIDTable("payment_methods") {
    val code = varchar("code", 20).uniqueIndex()
    val displayName = varchar("display_name", 100)
    val targetAccountCode = reference("target_account_code", AccountsTable.accountCode)
    val isActive = bool("is_active").default(true)
    val description = text("description").nullable()
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
}
