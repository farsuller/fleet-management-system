package com.solodev.fleet.modules.rentals.application.usecases

import com.solodev.fleet.modules.rentals.domain.model.RentalId
import com.solodev.fleet.modules.rentals.domain.repository.RentalRepository
import com.solodev.fleet.modules.rentals.domain.repository.RentalWithDetails

class GetRentalUseCase(
    private val repository: RentalRepository,
) {
    suspend fun execute(id: String): RentalWithDetails? = repository.findByIdWithDetails(RentalId(id))
}
