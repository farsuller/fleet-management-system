package com.solodev.fleet.modules.rentals.infrastructure.persistence

import com.solodev.fleet.modules.rentals.domain.model.CustomerId
import com.solodev.fleet.modules.rentals.domain.model.Rental
import com.solodev.fleet.modules.rentals.domain.model.RentalId
import com.solodev.fleet.modules.rentals.domain.model.RentalStatus
import com.solodev.fleet.modules.rentals.domain.repository.RentalRepository
import com.solodev.fleet.modules.vehicles.domain.model.VehicleId
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.Instant
import java.util.*

/** PostgreSQL implementation of RentalRepository using Exposed ORM. */
class RentalRepositoryImpl : RentalRepository {

    private suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }

    private fun ResultRow.toRental() =
        Rental(
            id = RentalId(this[RentalsTable.id].value.toString()),
            rentalNumber = this[RentalsTable.rentalNumber],
            customerId = CustomerId(this[RentalsTable.customerId].value.toString()),
            vehicleId = VehicleId(this[RentalsTable.vehicleId].value.toString()),
            status = RentalStatus.valueOf(this[RentalsTable.status]),
            startDate = this[RentalsTable.startDate],
            endDate = this[RentalsTable.endDate],
            actualStartDate = this[RentalsTable.actualStartDate],
            actualEndDate = this[RentalsTable.actualEndDate],
            dailyRateCents = this[RentalsTable.dailyRateCents],
            totalAmountCents = this[RentalsTable.totalAmountCents],
            currencyCode = this[RentalsTable.currencyCode],
            startOdometerKm = this[RentalsTable.startOdometerKm],
            endOdometerKm = this[RentalsTable.endOdometerKm]
        )

    override suspend fun findById(id: RentalId): Rental? = dbQuery {
        RentalsTable.selectAll()
            .where { RentalsTable.id eq UUID.fromString(id.value) }
            .map { it.toRental() }
            .singleOrNull()
    }

    override suspend fun findByRentalNumber(rentalNumber: String): Rental? = dbQuery {
        RentalsTable.selectAll()
            .where { RentalsTable.rentalNumber eq rentalNumber }
            .map { it.toRental() }
            .singleOrNull()
    }

    override suspend fun save(rental: Rental): Rental = dbQuery {
        val exists =
            RentalsTable.selectAll()
                .where { RentalsTable.id eq UUID.fromString(rental.id.value) }
                .count() > 0
        if (exists) {
            RentalsTable.update({ RentalsTable.id eq UUID.fromString(rental.id.value) }) {
                it[status] = rental.status.name
                it[endDate] = rental.endDate
                it[actualStartDate] = rental.actualStartDate
                it[actualEndDate] = rental.actualEndDate
                it[startOdometerKm] = rental.startOdometerKm
                it[endOdometerKm] = rental.endOdometerKm
                it[totalAmountCents] = rental.totalAmountCents
                it[updatedAt] = Instant.now()
            }
        } else {
            RentalsTable.insert {
                it[id] = UUID.fromString(rental.id.value)
                it[rentalNumber] = rental.rentalNumber
                it[customerId] = UUID.fromString(rental.customerId.value)
                it[vehicleId] = UUID.fromString(rental.vehicleId.value)
                it[status] = rental.status.name
                it[startDate] = rental.startDate
                it[endDate] = rental.endDate
                it[dailyRateCents] = rental.dailyRateCents
                it[totalAmountCents] = rental.totalAmountCents
                it[createdAt] = Instant.now()
                it[updatedAt] = Instant.now()
            }
        }
        rental
    }

    override suspend fun findByCustomerId(customerId: CustomerId): List<Rental> = dbQuery {
        RentalsTable.selectAll()
            .where { RentalsTable.customerId eq UUID.fromString(customerId.value) }
            .map { it.toRental() }
    }

    override suspend fun findByVehicleId(vehicleId: VehicleId): List<Rental> = dbQuery {
        RentalsTable.selectAll()
            .where { RentalsTable.vehicleId eq UUID.fromString(vehicleId.value) }
            .map { it.toRental() }
    }

    override suspend fun findAll(): List<Rental> = dbQuery {
        RentalsTable.selectAll().map { it.toRental() }
    }

    override suspend fun findConflictingRentals(
        vehicleId: VehicleId,
        startDate: Instant,
        endDate: Instant
    ): List<Rental> = dbQuery {
        RentalsTable.selectAll()
            .where {
                (RentalsTable.vehicleId eq UUID.fromString(vehicleId.value)) and
                        (RentalsTable.status inList
                                listOf(
                                    RentalStatus.RESERVED.name,
                                    RentalStatus.ACTIVE.name
                                )) and
                        (RentalsTable.startDate lessEq endDate) and
                        (RentalsTable.endDate greaterEq startDate)
            }
            .map { it.toRental() }
    }

    override suspend fun findByStatus(status: RentalStatus): List<Rental> = dbQuery {
        RentalsTable.selectAll().where { RentalsTable.status eq status.name }.map { it.toRental() }
    }

    override suspend fun deleteById(id: RentalId): Boolean = dbQuery {
        val deletedCount = RentalsTable.deleteWhere { RentalsTable.id eq UUID.fromString(id.value) }
        deletedCount > 0
    }
}
