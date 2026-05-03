package com.solodev.fleet.modules.vehicles.domain.repository

import com.solodev.fleet.modules.vehicles.domain.model.Bus
import com.solodev.fleet.shared.models.PaginationParams

interface BusRepository {
    suspend fun findById(id: String): Bus?

    suspend fun findAll(params: PaginationParams): Pair<List<Bus>, Long>

    suspend fun save(bus: Bus): Bus

    suspend fun deleteById(id: String): Boolean
}
