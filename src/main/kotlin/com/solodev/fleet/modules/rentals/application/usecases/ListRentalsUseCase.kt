package com.solodev.fleet.modules.rentals.application.usecases

import com.solodev.fleet.modules.rentals.domain.model.Rental
import com.solodev.fleet.modules.rentals.domain.repository.RentalRepository

class ListRentalsUseCase(private val repository: RentalRepository) {
    suspend fun execute(): List<Rental> {
        return repository.findAll()
    }
}
