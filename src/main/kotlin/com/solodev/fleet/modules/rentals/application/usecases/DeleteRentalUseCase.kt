package com.solodev.fleet.modules.rentals.application.usecases

import com.solodev.fleet.modules.rentals.domain.model.RentalId
import com.solodev.fleet.modules.rentals.domain.repository.RentalRepository

class DeleteRentalUseCase(private val repository: RentalRepository) {
    suspend fun execute(id: String): Boolean {
        return repository.deleteById(RentalId(id))
    }
}
