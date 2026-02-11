package com.solodev.fleet.modules.vehicles.application.usecases

import com.solodev.fleet.modules.vehicles.application.dto.VehicleResponse
import com.solodev.fleet.modules.vehicles.domain.model.Vehicle
import com.solodev.fleet.modules.vehicles.domain.repository.VehicleRepository
import com.solodev.fleet.shared.models.PaginatedResponse
import com.solodev.fleet.shared.models.PaginationParams

class ListVehiclesUseCase(
    private val repository: VehicleRepository
) {
    suspend fun execute(params: PaginationParams): PaginatedResponse<VehicleResponse> {
        val (vehicles, total) = repository.findAll(params)
        val items = vehicles.map { VehicleResponse.fromDomain(it) }

        // Cursor is the ID of the last item in the list
        val nextCursor = items.lastOrNull()?.id

        return PaginatedResponse(
            items = items,
            nextCursor = if (items.size >= params.limit) nextCursor else null,
            limit = params.limit,
            total = total
        )
    }
}