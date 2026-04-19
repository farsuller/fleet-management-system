package com.solodev.fleet.modules.vehicles.domain.repository

import com.solodev.fleet.modules.vehicles.domain.model.Truck
import com.solodev.fleet.shared.models.PaginationParams

interface TruckRepository {
    suspend fun findById(id: String): Truck?

    suspend fun findAll(params: PaginationParams): Pair<List<Truck>, Long>

    suspend fun save(truck: Truck): Truck

    suspend fun deleteById(id: String): Boolean
}
