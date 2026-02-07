package com.solodev.fleet.modules.rentals.application.usecases

import com.solodev.fleet.modules.domain.models.Rental
import com.solodev.fleet.modules.domain.ports.RentalRepository

class ListRentalsUseCase(private val repository: RentalRepository) {
    suspend fun execute(): List<Rental> {
        return repository.findAll()
    }
}
