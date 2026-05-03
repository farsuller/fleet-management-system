package com.solodev.fleet.modules.accounts.infrastructure.persistence

import com.solodev.fleet.modules.accounts.domain.model.PaymentMethod
import com.solodev.fleet.modules.accounts.domain.model.PaymentMethodStatus
import com.solodev.fleet.modules.accounts.domain.repository.PaymentMethodRepository
import com.solodev.fleet.shared.helpers.dbQuery
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.update
import java.time.Instant
import java.util.UUID

class PaymentMethodRepositoryImpl : PaymentMethodRepository {
    private fun ensureStatusColumn() {
        TransactionManager.current().exec(
            """
            ALTER TABLE payment_methods
            ADD COLUMN IF NOT EXISTS status TEXT NOT NULL DEFAULT 'ACTIVE';
            """.trimIndent(),
        )

        TransactionManager.current().exec(
            """
            UPDATE payment_methods
            SET status = CASE
                WHEN status IS NULL OR status = '' THEN 'ACTIVE'
                WHEN UPPER(status) = 'MAINTENANCE' THEN 'DEPRECATED'
                ELSE UPPER(status)
            END;
            """.trimIndent(),
        )
    }

    private fun String.toPaymentMethodStatus(): PaymentMethodStatus =
        when (uppercase()) {
            "ACTIVE" -> PaymentMethodStatus.ACTIVE
            "INACTIVE" -> PaymentMethodStatus.INACTIVE
            "DEPRECATED", "MAINTENANCE" -> PaymentMethodStatus.DEPRECATED
            else -> PaymentMethodStatus.ACTIVE
        }

    private fun ResultRow.toPaymentMethod() =
        PaymentMethod(
            id = this[PaymentMethodsTable.id].value,
            code = this[PaymentMethodsTable.code],
            displayName = this[PaymentMethodsTable.displayName],
            targetAccountCode = this[PaymentMethodsTable.targetAccountCode],
            status = this[PaymentMethodsTable.status].toPaymentMethodStatus(),
            description = this[PaymentMethodsTable.description],
            createdAt = this[PaymentMethodsTable.createdAt],
            updatedAt = this[PaymentMethodsTable.updatedAt],
        )

    override suspend fun findById(id: UUID): PaymentMethod? =
        dbQuery {
            ensureStatusColumn()
            PaymentMethodsTable
                .selectAll()
                .where { PaymentMethodsTable.id eq id }
                .singleOrNull()
                ?.toPaymentMethod()
        }

    override suspend fun findByCode(code: String): PaymentMethod? =
        dbQuery {
            ensureStatusColumn()
            PaymentMethodsTable
                .selectAll()
                .where { PaymentMethodsTable.code eq code }
                .singleOrNull()
                ?.toPaymentMethod()
        }

    override suspend fun findAll(): List<PaymentMethod> =
        dbQuery {
            ensureStatusColumn()
            PaymentMethodsTable.selectAll().map { it.toPaymentMethod() }
        }

    override suspend fun findAllActive(): List<PaymentMethod> =
        dbQuery {
            ensureStatusColumn()
            PaymentMethodsTable.selectAll().where { PaymentMethodsTable.status eq "ACTIVE" }.map {
                it.toPaymentMethod()
            }
        }

    override suspend fun save(paymentMethod: PaymentMethod): PaymentMethod =
        dbQuery {
            ensureStatusColumn()
            val now = Instant.now()
            val exists =
                PaymentMethodsTable
                    .select(PaymentMethodsTable.id)
                    .where { PaymentMethodsTable.id eq paymentMethod.id }
                    .limit(1)
                    .singleOrNull() != null

            if (exists) {
                PaymentMethodsTable.update({ PaymentMethodsTable.id eq paymentMethod.id }) {
                    it[code] = paymentMethod.code
                    it[displayName] = paymentMethod.displayName
                    it[targetAccountCode] = paymentMethod.targetAccountCode
                    it[status] = paymentMethod.status.name
                    it[description] = paymentMethod.description
                    it[updatedAt] = now
                }
            } else {
                PaymentMethodsTable.insert {
                    it[id] = paymentMethod.id
                    it[code] = paymentMethod.code
                    it[displayName] = paymentMethod.displayName
                    it[targetAccountCode] = paymentMethod.targetAccountCode
                    it[status] = paymentMethod.status.name
                    it[description] = paymentMethod.description
                    it[createdAt] = now
                    it[updatedAt] = now
                }
            }
            paymentMethod
        }

    override suspend fun delete(id: UUID): Boolean =
        dbQuery {
            PaymentMethodsTable.deleteWhere { PaymentMethodsTable.id eq id } > 0
        }
}
