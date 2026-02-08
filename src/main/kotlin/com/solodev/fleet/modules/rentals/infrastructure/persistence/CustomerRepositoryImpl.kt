package com.solodev.fleet.modules.rentals.infrastructure.persistence

import com.solodev.fleet.modules.rentals.domain.model.Customer
import com.solodev.fleet.modules.rentals.domain.model.CustomerId
import com.solodev.fleet.modules.rentals.domain.repository.CustomerRepository
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.*

/** PostgreSQL implementation of CustomerRepository using Exposed ORM. */
class CustomerRepositoryImpl : CustomerRepository {

    private suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }

    private fun ResultRow.toCustomer() =
        Customer(
            id = CustomerId(this[CustomersTable.id].value.toString()),
            userId = this[CustomersTable.userId],
            firstName = this[CustomersTable.firstName],
            lastName = this[CustomersTable.lastName],
            email = this[CustomersTable.email],
            phone = this[CustomersTable.phone],
            driverLicenseNumber = this[CustomersTable.driverLicenseNumber],
            driverLicenseExpiry =
                this[CustomersTable.driverLicenseExpiry]
                    .atStartOfDay()
                    .toInstant(ZoneOffset.UTC),
            address = this[CustomersTable.address],
            city = this[CustomersTable.city],
            state = this[CustomersTable.state],
            postalCode = this[CustomersTable.postalCode],
            country = this[CustomersTable.country],
            isActive = this[CustomersTable.isActive]
        )

    override suspend fun findById(id: CustomerId): Customer? = dbQuery {
        CustomersTable.selectAll()
            .where { CustomersTable.id eq UUID.fromString(id.value) }
            .map { it.toCustomer() }
            .singleOrNull()
    }

    override suspend fun findByEmail(email: String): Customer? = dbQuery {
        CustomersTable.selectAll()
            .where { CustomersTable.email eq email }
            .map { it.toCustomer() }
            .singleOrNull()
    }

    override suspend fun findByDriverLicense(licenseNumber: String): Customer? = dbQuery {
        CustomersTable.selectAll()
            .where { CustomersTable.driverLicenseNumber eq licenseNumber }
            .map { it.toCustomer() }
            .singleOrNull()
    }

    override suspend fun save(customer: Customer): Customer = dbQuery {
        val customerUuid = UUID.fromString(customer.id.value)
        val now = Instant.now()

        val exists =
            CustomersTable.selectAll()
                .where { CustomersTable.id eq customerUuid }
                .count() > 0

        if (exists) {
            CustomersTable.update({ CustomersTable.id eq customerUuid }) {
                it[userId] = customer.userId
                it[firstName] = customer.firstName
                it[lastName] = customer.lastName
                it[email] = customer.email
                it[phone] = customer.phone
                it[driverLicenseNumber] = customer.driverLicenseNumber
                it[driverLicenseExpiry] =
                    LocalDate.ofInstant(
                        customer.driverLicenseExpiry,
                        ZoneOffset.UTC
                    )
                it[address] = customer.address
                it[city] = customer.city
                it[state] = customer.state
                it[postalCode] = customer.postalCode
                it[country] = customer.country
                it[isActive] = customer.isActive
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
                it[driverLicenseExpiry] =
                    LocalDate.ofInstant(
                        customer.driverLicenseExpiry,
                        ZoneOffset.UTC
                    )
                it[address] = customer.address
                it[city] = customer.city
                it[state] = customer.state
                it[postalCode] = customer.postalCode
                it[country] = customer.country
                it[isActive] = customer.isActive
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
        val deletedCount =
            CustomersTable.deleteWhere {
                CustomersTable.id eq UUID.fromString(id.value)
            }
        deletedCount > 0
    }
}
