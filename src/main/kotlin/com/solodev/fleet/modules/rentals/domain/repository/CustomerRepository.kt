package com.solodev.fleet.modules.rentals.domain.repository

import com.solodev.fleet.modules.rentals.domain.model.Customer
import com.solodev.fleet.modules.rentals.domain.model.CustomerId

/** Repository interface for Customer persistence. */
interface CustomerRepository {
    /** Find a customer by their unique identifier. */
    suspend fun findById(id: CustomerId): Customer?

    /** Find a customer by email. */
    suspend fun findByEmail(email: String): Customer?

    /** Find a customer by driver license number. */
    suspend fun findByDriverLicense(licenseNumber: String): Customer?

    /** Save a new customer or update an existing one. */
    suspend fun save(customer: Customer): Customer

    /** Find all customers. */
    suspend fun findAll(): List<Customer>

    /** Delete a customer by ID. */
    suspend fun deleteById(id: CustomerId): Boolean
}
