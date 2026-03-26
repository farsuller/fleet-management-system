package com.solodev.fleet.modules.rentals.application.usecases

import com.solodev.fleet.modules.rentals.domain.model.RentalId
import com.solodev.fleet.modules.rentals.domain.model.RentalStatus
import com.solodev.fleet.modules.rentals.domain.repository.RentalRepository
import com.solodev.fleet.modules.rentals.domain.repository.RentalWithDetails

class CancelRentalUseCase(private val repository: RentalRepository) {
    suspend fun execute(id: String): RentalWithDetails {
        val rentalId = RentalId(id)
        val rental =
                repository.findById(rentalId)
                        ?: throw IllegalArgumentException("Rental not found")

        require(rental.status in listOf(RentalStatus.RESERVED, RentalStatus.ACTIVE)) {
            "Can only cancel reserved or active rentals"
        }

        val cancelled = rental.cancel()
        repository.save(cancelled)
        
        return repository.findByIdWithDetails(rentalId) ?: throw IllegalStateException("Failed to reload rental with details")
    }
}
