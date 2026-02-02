package com.solodev.fleet.modules.infrastructure.persistence

import com.solodev.fleet.modules.domain.models.*
import com.solodev.fleet.modules.domain.ports.CustomerRepository
import com.solodev.fleet.modules.domain.ports.RentalRepository
import java.time.Instant
import java.util.*
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.lessEq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

/**
 * PostgreSQL implementation of RentalRepository using Exposed ORM.
 */
class RentalRepositoryImpl : RentalRepository {

    private suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }

    private fun ResultRow.toRental() = Rental(
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
        endOdometerKm = this[RentalsTable.endOdometerKm],
        pickupLocation = this[RentalsTable.pickupLocation],
        dropoffLocation = this[RentalsTable.dropoffLocation],
        notes = this[RentalsTable.notes]
    )

    override suspend fun findById(id: RentalId): Rental? = dbQuery {
        RentalsTable.select { RentalsTable.id eq UUID.fromString(id.value) }
            .map { it.toRental() }
            .singleOrNull()
    }

    override suspend fun findByRentalNumber(rentalNumber: String): Rental? = dbQuery {
        RentalsTable.select { RentalsTable.rentalNumber eq rentalNumber }
            .map { it.toRental() }
            .singleOrNull()
    }

    override suspend fun save(rental: Rental): Rental = dbQuery {
        val rentalUuid = UUID.fromString(rental.id.value)
        val now = Instant.now()

        val exists = RentalsTable.select { RentalsTable.id eq rentalUuid }.count() > 0

        if (exists) {
            RentalsTable.update({ RentalsTable.id eq rentalUuid }) {
                it[rentalNumber] = rental.rentalNumber
                it[customerId] = UUID.fromString(rental.customerId.value)
                it[vehicleId] = UUID.fromString(rental.vehicleId.value)
                it[status] = rental.status.name
                it[startDate] = rental.startDate
                it[endDate] = rental.endDate
                it[actualStartDate] = rental.actualStartDate
                it[actualEndDate] = rental.actualEndDate
                it[dailyRateCents] = rental.dailyRateCents
                it[totalAmountCents] = rental.totalAmountCents
                it[currencyCode] = rental.currencyCode
                it[startOdometerKm] = rental.startOdometerKm
                it[endOdometerKm] = rental.endOdometerKm
                it[pickupLocation] = rental.pickupLocation
                it[dropoffLocation] = rental.dropoffLocation
                it[notes] = rental.notes
                it[updatedAt] = now
            }
        } else {
            RentalsTable.insert {
                it[id] = rentalUuid
                it[rentalNumber] = rental.rentalNumber
                it[customerId] = UUID.fromString(rental.customerId.value)
                it[vehicleId] = UUID.fromString(rental.vehicleId.value)
                it[status] = rental.status.name
                it[startDate] = rental.startDate
                it[endDate] = rental.endDate
                it[actualStartDate] = rental.actualStartDate
                it[actualEndDate] = rental.actualEndDate
                it[dailyRateCents] = rental.dailyRateCents
                it[totalAmountCents] = rental.totalAmountCents
                it[currencyCode] = rental.currencyCode
                it[startOdometerKm] = rental.startOdometerKm
                it[endOdometerKm] = rental.endOdometerKm
                it[pickupLocation] = rental.pickupLocation
                it[dropoffLocation] = rental.dropoffLocation
                it[notes] = rental.notes
                it[createdAt] = now
                it[updatedAt] = now
            }
        }

        rental
    }

    override suspend fun findByCustomerId(customerId: CustomerId): List<Rental> = dbQuery {
        RentalsTable.select { RentalsTable.customerId eq UUID.fromString(customerId.value) }
            .map { it.toRental() }
    }

    override suspend fun findByVehicleId(vehicleId: VehicleId): List<Rental> = dbQuery {
        RentalsTable.select { RentalsTable.vehicleId eq UUID.fromString(vehicleId.value) }
            .map { it.toRental() }
    }

    override suspend fun findConflictingRentals(
        vehicleId: VehicleId,
        startDate: Instant,
        endDate: Instant
    ): List<Rental> = dbQuery {
        RentalsTable.select {
            (RentalsTable.vehicleId eq UUID.fromString(vehicleId.value)) and
            (RentalsTable.status inList listOf(RentalStatus.RESERVED.name, RentalStatus.ACTIVE.name)) and
            (RentalsTable.startDate lessEq endDate) and
            (RentalsTable.endDate greaterEq startDate)
        }.map { it.toRental() }
    }

    override suspend fun findByStatus(status: RentalStatus): List<Rental> = dbQuery {
        RentalsTable.select { RentalsTable.status eq status.name }
            .map { it.toRental() }
    }

    override suspend fun deleteById(id: RentalId): Boolean = dbQuery {
        val deletedCount = RentalsTable.deleteWhere { RentalsTable.id eq UUID.fromString(id.value) }
        deletedCount > 0
    }
}

/**
 * PostgreSQL implementation of CustomerRepository using Exposed ORM.
 */
class CustomerRepositoryImpl : CustomerRepository {

    private suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }

    private fun ResultRow.toCustomer() = Customer(
        id = CustomerId(this[CustomersTable.id].value.toString()),
        userId = this[CustomersTable.userId],
        firstName = this[CustomersTable.firstName],
        lastName = this[CustomersTable.lastName],
        email = this[CustomersTable.email],
        phone = this[CustomersTable.phone],
        driverLicenseNumber = this[CustomersTable.driverLicenseNumber],
        driverLicenseExpiry = this[CustomersTable.driverLicenseExpiry].atStartOfDay().toInstant(java.time.ZoneOffset.UTC),
        address = this[CustomersTable.address],
        city = this[CustomersTable.city],
        state = this[CustomersTable.state],
        postalCode = this[CustomersTable.postalCode],
        country = this[CustomersTable.country]
    )

    override suspend fun findById(id: CustomerId): Customer? = dbQuery {
        CustomersTable.select { CustomersTable.id eq UUID.fromString(id.value) }
            .map { it.toCustomer() }
            .singleOrNull()
    }

    override suspend fun findByEmail(email: String): Customer? = dbQuery {
        CustomersTable.select { CustomersTable.email eq email }
            .map { it.toCustomer() }
            .singleOrNull()
    }

    override suspend fun findByDriverLicense(licenseNumber: String): Customer? = dbQuery {
        CustomersTable.select { CustomersTable.driverLicenseNumber eq licenseNumber }
            .map { it.toCustomer() }
            .singleOrNull()
    }

    override suspend fun save(customer: Customer): Customer = dbQuery {
        val customerUuid = UUID.fromString(customer.id.value)
        val now = Instant.now()

        val exists = CustomersTable.select { CustomersTable.id eq customerUuid }.count() > 0

        if (exists) {
            CustomersTable.update({ CustomersTable.id eq customerUuid }) {
                it[userId] = customer.userId
                it[firstName] = customer.firstName
                it[lastName] = customer.lastName
                it[email] = customer.email
                it[phone] = customer.phone
                it[driverLicenseNumber] = customer.driverLicenseNumber
                it[driverLicenseExpiry] = java.time.LocalDate.ofInstant(customer.driverLicenseExpiry, java.time.ZoneOffset.UTC)
                it[address] = customer.address
                it[city] = customer.city
                it[state] = customer.state
                it[postalCode] = customer.postalCode
                it[country] = customer.country
                it[updatedAt] = now
            }
        } else {
            CustomersTable.insert {
                it[id] = customerUuid
                it[userId] = customer.userId
                it[firstName] = customer.firstName
                it[lastName] = customer.lastName
                it[email] = customer.email
                it[phone] = customer.phone
                it[driverLicenseNumber] = customer.driverLicenseNumber
                it[driverLicenseExpiry] = java.time.LocalDate.ofInstant(customer.driverLicenseExpiry, java.time.ZoneOffset.UTC)
                it[address] = customer.address
                it[city] = customer.city
                it[state] = customer.state
                it[postalCode] = customer.postalCode
                it[country] = customer.country
                it[createdAt] = now
                it[updatedAt] = now
            }
        }

        customer
    }

    override suspend fun findAll(): List<Customer> = dbQuery {
        CustomersTable.selectAll().map { it.toCustomer() }
    }

    override suspend fun deleteById(id: CustomerId): Boolean = dbQuery {
        val deletedCount = CustomersTable.deleteWhere { CustomersTable.id eq UUID.fromString(id.value) }
        deletedCount > 0
    }
}
