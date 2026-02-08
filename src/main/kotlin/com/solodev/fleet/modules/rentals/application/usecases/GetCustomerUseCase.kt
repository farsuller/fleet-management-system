package com.solodev.fleet.modules.rentals.application.usecases

import com.solodev.fleet.modules.rentals.domain.model.Customer
import com.solodev.fleet.modules.rentals.domain.model.CustomerId
import com.solodev.fleet.modules.rentals.domain.repository.CustomerRepository

/** Retrieves a customer by ID. */
class GetCustomerUseCase(private val customerRepository: CustomerRepository) {
    suspend fun execute(id: String): Customer? {
        return customerRepository.findById(CustomerId(id))
    }
}
