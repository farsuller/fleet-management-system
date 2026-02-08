package com.solodev.fleet.modules.accounts.infrastructure.persistence

import com.solodev.fleet.modules.accounts.domain.model.Invoice
import com.solodev.fleet.modules.accounts.domain.model.InvoiceStatus
import com.solodev.fleet.modules.accounts.domain.repository.InvoiceRepository
import com.solodev.fleet.modules.rentals.domain.model.CustomerId
import com.solodev.fleet.modules.rentals.domain.model.RentalId
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.update
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.*

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
        notes = this[InvoicesTable.notes],
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


}