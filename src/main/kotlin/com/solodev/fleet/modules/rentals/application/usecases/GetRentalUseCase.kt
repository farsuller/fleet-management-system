package com.solodev.fleet.modules.rentals.application.usecases

import com.solodev.fleet.modules.rentals.domain.repository.RentalRepository
import com.solodev.fleet.modules.rentals.domain.repository.RentalWithDetails
import com.solodev.fleet.modules.rentals.domain.model.RentalId

class GetRentalUseCase(private val repository: RentalRepository) {
    suspend fun execute(id: String): RentalWithDetails? {
        return repository.findByIdWithDetails(RentalId(id))
    }
}
