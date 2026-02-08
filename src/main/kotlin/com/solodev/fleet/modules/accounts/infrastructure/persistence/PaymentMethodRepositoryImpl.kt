package com.solodev.fleet.modules.accounts.infrastructure.persistence

import com.solodev.fleet.modules.accounts.domain.model.PaymentMethod
import com.solodev.fleet.modules.accounts.domain.repository.PaymentMethodRepository
import java.time.Instant
import java.util.*
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

class PaymentMethodRepositoryImpl : PaymentMethodRepository {

    private suspend fun <T> dbQuery(block: suspend () -> T): T =
            newSuspendedTransaction(Dispatchers.IO) { block() }

    private fun ResultRow.toPaymentMethod() =
            PaymentMethod(
                    id = this[PaymentMethodsTable.id].value,
                    code = this[PaymentMethodsTable.code],
                    displayName = this[PaymentMethodsTable.displayName],
                    targetAccountCode = this[PaymentMethodsTable.targetAccountCode],
                    isActive = this[PaymentMethodsTable.isActive],
                    description = this[PaymentMethodsTable.description],
                    createdAt = this[PaymentMethodsTable.createdAt],
                    updatedAt = this[PaymentMethodsTable.updatedAt]
            )

    override suspend fun findById(id: UUID): PaymentMethod? = dbQuery {
        PaymentMethodsTable.selectAll()
                .where { PaymentMethodsTable.id eq id }
                .map { it.toPaymentMethod() }
                .singleOrNull()
    }

    override suspend fun findByCode(code: String): PaymentMethod? = dbQuery {
        PaymentMethodsTable.selectAll()
                .where { PaymentMethodsTable.code eq code }
                .map { it.toPaymentMethod() }
                .singleOrNull()
    }

    override suspend fun findAll(): List<PaymentMethod> = dbQuery {
        PaymentMethodsTable.selectAll().map { it.toPaymentMethod() }
    }

    override suspend fun findAllActive(): List<PaymentMethod> = dbQuery {
        PaymentMethodsTable.selectAll().where { PaymentMethodsTable.isActive eq true }.map {
            it.toPaymentMethod()
        }
    }

    override suspend fun save(paymentMethod: PaymentMethod): PaymentMethod = dbQuery {
        val now = Instant.now()
        val exists =
                PaymentMethodsTable.selectAll()
                        .where { PaymentMethodsTable.id eq paymentMethod.id }
                        .count() > 0

        if (exists) {
            PaymentMethodsTable.update({ PaymentMethodsTable.id eq paymentMethod.id }) {
                it[code] = paymentMethod.code
                it[displayName] = paymentMethod.displayName
                it[targetAccountCode] = paymentMethod.targetAccountCode
                it[isActive] = paymentMethod.isActive
                it[description] = paymentMethod.description
                it[updatedAt] = now
            }
        } else {
            PaymentMethodsTable.insert {
                it[id] = paymentMethod.id
                it[code] = paymentMethod.code
                it[displayName] = paymentMethod.displayName
                it[targetAccountCode] = paymentMethod.targetAccountCode
                it[isActive] = paymentMethod.isActive
                it[description] = paymentMethod.description
                it[createdAt] = now
                it[updatedAt] = now
            }
        }
        paymentMethod
    }

    override suspend fun delete(id: UUID): Boolean = dbQuery {
        PaymentMethodsTable.deleteWhere { PaymentMethodsTable.id eq id } > 0
    }
}
