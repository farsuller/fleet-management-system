package com.solodev.fleet.modules.vehicles.application.usecases.truck

import com.solodev.fleet.modules.vehicles.application.dto.TruckRequest
import com.solodev.fleet.modules.vehicles.application.dto.TruckResponse
import com.solodev.fleet.modules.vehicles.application.dto.TruckUpdateRequest
import com.solodev.fleet.modules.vehicles.domain.model.Truck
import com.solodev.fleet.modules.vehicles.domain.model.VehicleState
import com.solodev.fleet.modules.vehicles.domain.repository.TruckRepository
import com.solodev.fleet.shared.models.PaginatedResponse
import com.solodev.fleet.shared.models.PaginationParams
import java.util.UUID

class ListTrucksUseCase(
    private val repository: TruckRepository,
) {
    suspend fun execute(params: PaginationParams): PaginatedResponse<TruckResponse> {
        val (items, total) = repository.findAll(params)
        val responses = items.map { TruckResponse.fromDomain(it) }
        val nextCursor = responses.lastOrNull()?.id
        return PaginatedResponse(
            items = responses,
            nextCursor = if (responses.size >= params.limit) nextCursor else null,
            limit = params.limit,
            total = total,
        )
    }
}

class GetTruckUseCase(
    private val repository: TruckRepository,
) {
    suspend fun execute(id: String): Truck? = repository.findById(id)
}

class CreateTruckUseCase(
    private val repository: TruckRepository,
) {
    suspend fun execute(request: TruckRequest): Truck {
        val id = UUID.randomUUID().toString()
        val vehicle = TruckResponse.toVehicle(request, id)
        return repository.save(
            Truck(
                vehicle = vehicle,
                payloadCapacityTons = request.payloadCapacityTons,
                cargoType = request.cargoType,
                axleCount = request.axleCount,
                grossVehicleWeightKg = request.grossVehicleWeightKg,
                hasTrailerHitch = request.hasTrailerHitch,
            ),
        )
    }
}

class UpdateTruckUseCase(
    private val repository: TruckRepository,
) {
    suspend fun execute(
        id: String,
        request: TruckUpdateRequest,
    ): Truck? {
        val existing = repository.findById(id) ?: return null
        val updated =
            existing.copy(
                vehicle =
                    existing.vehicle.copy(
                        licensePlate = request.licensePlate ?: existing.vehicle.licensePlate,
                        make = request.make ?: existing.vehicle.make,
                        model = request.model ?: existing.vehicle.model,
                        year = request.year ?: existing.vehicle.year,
                        color = request.color ?: existing.vehicle.color,
                        mileageKm = request.mileageKm ?: existing.vehicle.mileageKm,
                        dailyRateAmount = request.dailyRate?.let { (it * 100).toInt() } ?: existing.vehicle.dailyRateAmount,
                        passengerCapacity = request.passengerCapacity ?: existing.vehicle.passengerCapacity,
                        lastServiceMileage = request.lastServiceMileage ?: existing.vehicle.lastServiceMileage,
                        nextServiceMileage = request.nextServiceMileage ?: existing.vehicle.nextServiceMileage,
                    ),
                payloadCapacityTons = request.payloadCapacityTons ?: existing.payloadCapacityTons,
                cargoType = request.cargoType ?: existing.cargoType,
                axleCount = request.axleCount ?: existing.axleCount,
                grossVehicleWeightKg = request.grossVehicleWeightKg ?: existing.grossVehicleWeightKg,
                hasTrailerHitch = request.hasTrailerHitch ?: existing.hasTrailerHitch,
            )
        return repository.save(updated)
    }
}

class DeleteTruckUseCase(
    private val repository: TruckRepository,
) {
    suspend fun execute(id: String): Boolean {
        val truck = repository.findById(id) ?: return false
        if (truck.vehicle.state == VehicleState.RENTED || truck.vehicle.state == VehicleState.MAINTENANCE) {
            throw IllegalStateException("Cannot delete truck while it is in ${truck.vehicle.state} status")
        }
        return repository.deleteById(id)
    }
}
