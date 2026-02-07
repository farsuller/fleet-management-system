package com.solodev.fleet.modules.rentals.application.usecases

import com.solodev.fleet.modules.domain.models.*
import com.solodev.fleet.modules.domain.ports.RentalRepository

class GetRentalUseCase(private val repository: RentalRepository) {
    suspend fun execute(id: String): Rental? {
        return repository.findById(RentalId(id))
    }
}
