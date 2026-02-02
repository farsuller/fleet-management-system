package com.solodev.fleet.modules.rentals.application.usecases

import com.solodev.fleet.modules.domain.models.CustomerId
import com.solodev.fleet.modules.domain.models.Rental
import com.solodev.fleet.modules.domain.models.RentalId
import com.solodev.fleet.modules.domain.models.RentalStatus
import com.solodev.fleet.modules.domain.models.VehicleId
import com.solodev.fleet.modules.domain.ports.RentalRepository
import com.solodev.fleet.modules.rentals.application.dto.RentalRequest
import java.time.Instant
import java.util.*

class CreateRentalUseCase(private val repository: RentalRepository) {
    suspend fun execute(request: RentalRequest): Rental {
        // Simple logic for creation - version 1
        val rental =
                Rental(
                        id = RentalId(UUID.randomUUID().toString()),
                        rentalNumber = "RN-${System.currentTimeMillis()}",
                        customerId = CustomerId(request.customerId),
                        vehicleId = VehicleId(request.vehicleId),
                        status = RentalStatus.RESERVED,
                        startDate = Instant.parse(request.startDate),
                        endDate = Instant.parse(request.endDate),
                        dailyRateCents = request.dailyRateCents,
                        totalAmountCents = request.dailyRateCents // Calculation simplified for now
                )
        return repository.save(rental)
    }
}
