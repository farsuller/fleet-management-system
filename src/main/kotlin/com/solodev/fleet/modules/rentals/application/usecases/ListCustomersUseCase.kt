package com.solodev.fleet.modules.rentals.application.usecases

import com.solodev.fleet.modules.domain.models.Customer
import com.solodev.fleet.modules.domain.ports.CustomerRepository

/** Lists all customers in the system. */
class ListCustomersUseCase(private val customerRepository: CustomerRepository) {
    suspend fun execute(): List<Customer> {
        return customerRepository.findAll()
    }
}
