package com.solodev.fleet.modules.accounts.infrastructure.persistence

import com.solodev.fleet.modules.accounts.domain.model.Invoice
import com.solodev.fleet.modules.accounts.domain.model.InvoiceCategory
import com.solodev.fleet.modules.accounts.domain.model.InvoiceStatus
import com.solodev.fleet.modules.accounts.domain.repository.InvoiceRepository
import com.solodev.fleet.modules.rentals.domain.model.CustomerId
import com.solodev.fleet.modules.rentals.domain.model.RentalId
import com.solodev.fleet.shared.helpers.dbQuery
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

class InvoiceRepositoryImpl : InvoiceRepository {
    private fun ResultRow.toInvoice() =
        Invoice(
            id = this[InvoicesTable.id].value,
            invoiceNumber = this[InvoicesTable.invoiceNumber],
            customerId = CustomerId(this[InvoicesTable.customerId].value.toString()),
            rentalId =
                this[InvoicesTable.rentalId]?.value?.toString()?.let { RentalId(it) },
            status = InvoiceStatus.valueOf(this[InvoicesTable.status]),
            subtotal = this[InvoicesTable.subtotal],
            tax = this[InvoicesTable.tax],
            paidAmount = this[InvoicesTable.paidAmount],
            currencyCode = this[InvoicesTable.currencyCode],
            issueDate =
                this[InvoicesTable.issueDate].atStartOfDay().toInstant(ZoneOffset.UTC),
            dueDate = this[InvoicesTable.dueDate].atStartOfDay().toInstant(ZoneOffset.UTC),
            paidDate =
                this[InvoicesTable.paidDate]?.atStartOfDay()?.toInstant(ZoneOffset.UTC),
            category = InvoiceCategory.valueOf(this[InvoicesTable.category]),
            notes = this[InvoicesTable.notes],
        )

    override suspend fun findAll(): List<Invoice> =
        dbQuery {
            InvoicesTable.selectAll().map { it.toInvoice() }
        }

    override suspend fun findById(id: UUID): Invoice? =
        dbQuery {
            InvoicesTable
                .selectAll()
                .where { InvoicesTable.id eq id }
                .singleOrNull()
                ?.toInvoice()
        }

    override suspend fun findByInvoiceNumber(invoiceNumber: String): Invoice? =
        dbQuery {
            InvoicesTable
                .selectAll()
                .where { InvoicesTable.invoiceNumber eq invoiceNumber }
                .singleOrNull()
                ?.toInvoice()
        }

    override suspend fun findByCustomerId(customerId: UUID): List<Invoice> =
        dbQuery {
            InvoicesTable
                .selectAll()
                .where { InvoicesTable.customerId eq customerId }
                .map { it.toInvoice() }
        }

    override suspend fun findByRentalId(rentalId: UUID): Invoice? =
        dbQuery {
            InvoicesTable
                .selectAll()
                .where { InvoicesTable.rentalId eq rentalId }
                .singleOrNull()
                ?.toInvoice()
        }

    override suspend fun save(invoice: Invoice): Invoice =
        dbQuery {
            val now = Instant.now()

            val exists =
                InvoicesTable
                    .select(InvoicesTable.id)
                    .where { InvoicesTable.id eq invoice.id }
                    .limit(1)
                    .singleOrNull() != null

            if (exists) {
                InvoicesTable.update({ InvoicesTable.id eq invoice.id }) {
                    it[invoiceNumber] = invoice.invoiceNumber
                    it[customerId] = UUID.fromString(invoice.customerId.value)
                    it[rentalId] = invoice.rentalId?.value?.let { UUID.fromString(it) }
                    it[status] = invoice.status.name
                    it[subtotal] = invoice.subtotal
                    it[tax] = invoice.tax
                    it[paidAmount] = invoice.paidAmount
                    it[currencyCode] = invoice.currencyCode
                    it[issueDate] = LocalDate.ofInstant(invoice.issueDate, ZoneOffset.UTC)
                    it[dueDate] = LocalDate.ofInstant(invoice.dueDate, ZoneOffset.UTC)
                    it[paidDate] = invoice.paidDate?.let { LocalDate.ofInstant(it, ZoneOffset.UTC) }
                    it[category] = invoice.category.name
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
                    it[subtotal] = invoice.subtotal
                    it[tax] = invoice.tax
                    it[paidAmount] = invoice.paidAmount
                    it[currencyCode] = invoice.currencyCode
                    it[issueDate] = LocalDate.ofInstant(invoice.issueDate, ZoneOffset.UTC)
                    it[dueDate] = LocalDate.ofInstant(invoice.dueDate, ZoneOffset.UTC)
                    it[paidDate] = invoice.paidDate?.let { LocalDate.ofInstant(it, ZoneOffset.UTC) }
                    it[category] = invoice.category.name
                    it[notes] = invoice.notes
                    it[createdAt] = now
                    it[updatedAt] = now
                }
            }

            invoice
        }

    override suspend fun findByCategory(category: InvoiceCategory): List<Invoice> =
        dbQuery {
            InvoicesTable
                .selectAll()
                .where { InvoicesTable.category eq category.name }
                .map { it.toInvoice() }
        }
}
