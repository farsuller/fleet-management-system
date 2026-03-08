package com.solodev.fleet.modules.rentals.application.usecases

import com.solodev.fleet.modules.rentals.domain.model.Customer
import com.solodev.fleet.modules.rentals.domain.model.CustomerId
import com.solodev.fleet.modules.rentals.domain.repository.CustomerRepository

/** Toggles the active status of a customer (deactivate / reactivate). */
class DeactivateCustomerUseCase(private val customerRepository: CustomerRepository) {
    suspend fun execute(id: String): Customer? {
        val existing = customerRepository.findById(CustomerId(id)) ?: return null
        return customerRepository.save(existing.copy(isActive = !existing.isActive))
    }
}
