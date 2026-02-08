package com.solodev.fleet.modules.accounts.infrastructure.persistence

import com.solodev.fleet.modules.accounts.domain.model.Payment
import com.solodev.fleet.modules.accounts.domain.model.PaymentStatus
import com.solodev.fleet.modules.accounts.domain.repository.PaymentRepository
import com.solodev.fleet.modules.rentals.domain.model.CustomerId
import java.time.Instant
import java.util.*
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

class PaymentRepositoryImpl : PaymentRepository {
    override suspend fun delete(id: UUID): Boolean {
        return transaction { PaymentsTable.deleteWhere { PaymentsTable.id eq id } > 0 }
    }
    override suspend fun save(payment: Payment): Payment {
        transaction {
            val exists = PaymentsTable.selectAll().where(PaymentsTable.id eq payment.id).any()
            if (exists) {
                PaymentsTable.update({ PaymentsTable.id eq payment.id }) {
                    it[status] = payment.status.name
                    it[notes] = payment.notes
                    it[updatedAt] = Instant.now()
                }
            } else {
                PaymentsTable.insert {
                    it[id] = payment.id
                    it[paymentNumber] = payment.paymentNumber
                    it[customerId] = UUID.fromString(payment.customerId.value)
                    it[invoiceId] = payment.invoiceId
                    it[paymentMethod] = payment.paymentMethod
                    it[amountCents] = payment.amountCents
                    it[transactionReference] = payment.transactionReference
                    it[status] = payment.status.name
                    it[paymentDate] = payment.paymentDate
                    it[notes] = payment.notes
                    it[createdAt] = Instant.now()
                    it[updatedAt] = Instant.now()
                }
            }
        }
        return payment
    }

    override suspend fun findById(id: UUID): Payment? {
        return transaction {
            PaymentsTable.selectAll()
                    .where { PaymentsTable.id eq id }
                    .map { toDomain(it) }
                    .singleOrNull()
        }
    }

    override suspend fun findByInvoiceId(invoiceId: UUID): List<Payment> {
        return transaction {
            PaymentsTable.selectAll().where { PaymentsTable.invoiceId eq invoiceId }.map {
                toDomain(it)
            }
        }
    }

    override suspend fun findByCustomerId(customerId: UUID): List<Payment> {
        return transaction {
            PaymentsTable.selectAll().where { PaymentsTable.customerId eq customerId }.map {
                toDomain(it)
            }
        }
    }

    override suspend fun findAll(): List<Payment> {
        return transaction { PaymentsTable.selectAll().map { toDomain(it) } }
    }

    private fun toDomain(row: ResultRow): Payment {
        return Payment(
                id = row[PaymentsTable.id].value,
                paymentNumber = row[PaymentsTable.paymentNumber],
                customerId = CustomerId(row[PaymentsTable.customerId].toString()),
                invoiceId = row[PaymentsTable.invoiceId]?.value,
                amountCents = row[PaymentsTable.amountCents],
                paymentMethod = row[PaymentsTable.paymentMethod],
                transactionReference = row[PaymentsTable.transactionReference],
                status = PaymentStatus.valueOf(row[PaymentsTable.status]),
                paymentDate = row[PaymentsTable.paymentDate] ?: Instant.now(),
                notes = row[PaymentsTable.notes]
        )
    }
}
