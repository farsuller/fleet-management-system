package com.solodev.fleet.modules.rentals.application.usecases

import com.solodev.fleet.modules.rentals.domain.model.CustomerId
import com.solodev.fleet.modules.rentals.domain.model.RentalStatus
import com.solodev.fleet.modules.rentals.domain.repository.RentalRepository
import com.solodev.fleet.modules.rentals.domain.repository.RentalWithDetails
import com.solodev.fleet.modules.vehicles.domain.model.VehicleId

class ListRentalsUseCase(private val repository: RentalRepository) {
    suspend fun execute(
        page: Int = 1,
        limit: Int = 10,
        status: RentalStatus? = null,
        vehicleId: String? = null,
        customerId: String? = null
    ): Pair<List<RentalWithDetails>, Long> {
        val vId = vehicleId?.let { VehicleId(it) }
        val cId = customerId?.let { CustomerId(it) }
        
        val rentals = repository.findAllPaged(page, limit, status, vId, cId)
        val total = repository.count(status, vId, cId)
        
        return rentals to total
    }
}
