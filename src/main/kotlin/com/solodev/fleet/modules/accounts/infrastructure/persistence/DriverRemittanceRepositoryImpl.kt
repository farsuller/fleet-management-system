package com.solodev.fleet.modules.accounts.infrastructure.persistence

import com.solodev.fleet.modules.accounts.domain.model.DriverRemittance
import com.solodev.fleet.modules.accounts.domain.model.RemittanceStatus
import com.solodev.fleet.modules.accounts.domain.repository.DriverRemittanceRepository
import com.solodev.fleet.shared.helpers.dbQuery
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import java.time.Instant
import java.util.UUID

class DriverRemittanceRepositoryImpl : DriverRemittanceRepository {
    override suspend fun save(remittance: DriverRemittance): DriverRemittance =
        dbQuery {
            val exists =
                DriverRemittancesTable
                    .selectAll()
                    .where { DriverRemittancesTable.id eq remittance.id }
                    .any()

            if (!exists) {
                DriverRemittancesTable.insert {
                    it[id] = remittance.id
                    it[remittanceNumber] = remittance.remittanceNumber
                    it[driverId] = remittance.driverId
                    it[remittanceDate] = remittance.remittanceDate
                    it[totalAmount] = remittance.totalAmount
                    it[status] = remittance.status.name
                    it[notes] = remittance.notes
                    it[createdAt] = Instant.now()
                }
                remittance.paymentIds.forEach { paymentId ->
                    DriverRemittancePaymentsTable.insert {
                        it[remittanceId] = remittance.id
                        it[DriverRemittancePaymentsTable.paymentId] = paymentId
                    }
                }
            }

            remittance
        }

    override suspend fun findById(id: UUID): DriverRemittance? =
        dbQuery {
            val row =
                DriverRemittancesTable
                    .selectAll()
                    .where { DriverRemittancesTable.id eq id }
                    .singleOrNull() ?: return@dbQuery null

            val paymentIds =
                DriverRemittancePaymentsTable
                    .selectAll()
                    .where { DriverRemittancePaymentsTable.remittanceId eq id }
                    .map { it[DriverRemittancePaymentsTable.paymentId].value }

            DriverRemittance(
                id = row[DriverRemittancesTable.id].value,
                remittanceNumber = row[DriverRemittancesTable.remittanceNumber],
                driverId = row[DriverRemittancesTable.driverId].value,
                remittanceDate = row[DriverRemittancesTable.remittanceDate],
                totalAmount = row[DriverRemittancesTable.totalAmount],
                status = RemittanceStatus.fromName(row[DriverRemittancesTable.status]),
                paymentIds = paymentIds,
                notes = row[DriverRemittancesTable.notes],
            )
        }

    override suspend fun findByDriverId(driverId: UUID): List<DriverRemittance> =
        dbQuery {
            val rows =
                DriverRemittancesTable
                    .selectAll()
                    .where { DriverRemittancesTable.driverId eq driverId }
                    .toList()

            if (rows.isEmpty()) return@dbQuery emptyList()

            val remittanceIds = rows.map { it[DriverRemittancesTable.id].value }
            val paymentsMap =
                DriverRemittancePaymentsTable
                    .selectAll()
                    .where { DriverRemittancePaymentsTable.remittanceId inList remittanceIds }
                    .toList()
                    .groupBy({ it[DriverRemittancePaymentsTable.remittanceId].value }) {
                        it[DriverRemittancePaymentsTable.paymentId].value
                    }

            rows.map { row ->
                val id = row[DriverRemittancesTable.id].value
                DriverRemittance(
                    id = id,
                    remittanceNumber = row[DriverRemittancesTable.remittanceNumber],
                    driverId = row[DriverRemittancesTable.driverId].value,
                    remittanceDate = row[DriverRemittancesTable.remittanceDate],
                    totalAmount = row[DriverRemittancesTable.totalAmount],
                    status = RemittanceStatus.fromName(row[DriverRemittancesTable.status]),
                    paymentIds = paymentsMap[id] ?: emptyList(),
                    notes = row[DriverRemittancesTable.notes],
                )
            }
        }

    override suspend fun findAll(): List<DriverRemittance> =
        dbQuery {
            val rows = DriverRemittancesTable.selectAll().toList()
            if (rows.isEmpty()) return@dbQuery emptyList()

            val remittanceIds = rows.map { it[DriverRemittancesTable.id].value }
            val paymentsMap =
                DriverRemittancePaymentsTable
                    .selectAll()
                    .where { DriverRemittancePaymentsTable.remittanceId inList remittanceIds }
                    .toList()
                    .groupBy({ it[DriverRemittancePaymentsTable.remittanceId].value }) {
                        it[DriverRemittancePaymentsTable.paymentId].value
                    }

            rows.map { row ->
                val id = row[DriverRemittancesTable.id].value
                DriverRemittance(
                    id = id,
                    remittanceNumber = row[DriverRemittancesTable.remittanceNumber],
                    driverId = row[DriverRemittancesTable.driverId].value,
                    remittanceDate = row[DriverRemittancesTable.remittanceDate],
                    totalAmount = row[DriverRemittancesTable.totalAmount],
                    status = RemittanceStatus.fromName(row[DriverRemittancesTable.status]),
                    paymentIds = paymentsMap[id] ?: emptyList(),
                    notes = row[DriverRemittancesTable.notes],
                )
            }
        }
}
