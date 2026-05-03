package com.solodev.fleet.modules.vehicles.application.usecases.bus

import com.solodev.fleet.modules.vehicles.application.dto.BusRequest
import com.solodev.fleet.modules.vehicles.application.dto.BusResponse
import com.solodev.fleet.modules.vehicles.application.dto.BusUpdateRequest
import com.solodev.fleet.modules.vehicles.domain.model.Bus
import com.solodev.fleet.modules.vehicles.domain.model.VehicleState
import com.solodev.fleet.modules.vehicles.domain.repository.BusRepository
import com.solodev.fleet.shared.models.PaginatedResponse
import com.solodev.fleet.shared.models.PaginationParams
import java.util.UUID

class ListBusesUseCase(
    private val repository: BusRepository,
) {
    suspend fun execute(params: PaginationParams): PaginatedResponse<BusResponse> {
        val (items, total) = repository.findAll(params)
        val responses = items.map { BusResponse.fromDomain(it) }
        val nextCursor = responses.lastOrNull()?.id
        return PaginatedResponse(
            items = responses,
            nextCursor = if (responses.size >= params.limit) nextCursor else null,
            limit = params.limit,
            total = total,
        )
    }
}

class GetBusUseCase(
    private val repository: BusRepository,
) {
    suspend fun execute(id: String): Bus? = repository.findById(id)
}

class CreateBusUseCase(
    private val repository: BusRepository,
) {
    suspend fun execute(request: BusRequest): Bus {
        val id = UUID.randomUUID().toString()
        val vehicle = BusResponse.toVehicle(request, id)
        return repository.save(
            Bus(
                vehicle = vehicle,
                routeNumber = request.routeNumber,
                doorCount = request.doorCount,
                standingCapacity = request.standingCapacity,
                hasAccessibilityRamp = request.hasAccessibilityRamp,
                hasAirConditioning = request.hasAirConditioning,
            ),
        )
    }
}

class UpdateBusUseCase(
    private val repository: BusRepository,
) {
    suspend fun execute(
        id: String,
        request: BusUpdateRequest,
    ): Bus? {
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
                routeNumber = request.routeNumber ?: existing.routeNumber,
                doorCount = request.doorCount ?: existing.doorCount,
                standingCapacity = request.standingCapacity ?: existing.standingCapacity,
                hasAccessibilityRamp = request.hasAccessibilityRamp ?: existing.hasAccessibilityRamp,
                hasAirConditioning = request.hasAirConditioning ?: existing.hasAirConditioning,
            )
        return repository.save(updated)
    }
}

class DeleteBusUseCase(
    private val repository: BusRepository,
) {
    suspend fun execute(id: String): Boolean {
        val bus = repository.findById(id) ?: return false
        if (bus.vehicle.state == VehicleState.RENTED || bus.vehicle.state == VehicleState.MAINTENANCE) {
            throw IllegalStateException("Cannot delete bus while it is in ${bus.vehicle.state} status")
        }
        return repository.deleteById(id)
    }
}
