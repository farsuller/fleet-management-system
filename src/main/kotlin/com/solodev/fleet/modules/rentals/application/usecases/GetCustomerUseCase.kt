package com.solodev.fleet.modules.rentals.application.usecases

import com.solodev.fleet.modules.domain.models.Customer
import com.solodev.fleet.modules.domain.models.CustomerId
import com.solodev.fleet.modules.domain.ports.CustomerRepository

/** Retrieves a customer by ID. */
class GetCustomerUseCase(private val customerRepository: CustomerRepository) {
    suspend fun execute(id: String): Customer? {
        return customerRepository.findById(CustomerId(id))
    }
}
