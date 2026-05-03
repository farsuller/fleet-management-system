package com.solodev.fleet.modules.rentals.application.usecases

import com.solodev.fleet.modules.rentals.domain.model.CustomerId
import com.solodev.fleet.modules.rentals.domain.repository.CustomerRepository

class DeleteCustomerUseCase(private val repository: CustomerRepository) {
    suspend fun execute(id: String): Boolean {
        return repository.deleteById(CustomerId(id))
    }
}
