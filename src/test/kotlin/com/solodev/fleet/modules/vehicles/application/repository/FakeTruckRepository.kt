package com.solodev.fleet.modules.vehicles.application.repository

import com.solodev.fleet.modules.vehicles.domain.model.Truck
import com.solodev.fleet.modules.vehicles.domain.repository.TruckRepository
import com.solodev.fleet.shared.models.PaginationParams

class FakeTruckRepository : TruckRepository {
    private val trucks = mutableMapOf<String, Truck>()

    override suspend fun findById(id: String): Truck? = trucks[id]

    override suspend fun findAll(params: PaginationParams): Pair<List<Truck>, Long> {
        val allTrucks = trucks.values.toList()
        val total = allTrucks.size.toLong()

        // Simple pagination logic for testing
        val items =
            allTrucks
                .sortedBy { it.vehicle.id.value } // Consistent ordering
                .drop(allTrucks.indexOfFirst { it.vehicle.id.value == params.cursor }.let { if (it == -1) 0 else it + 1 })
                .take(params.limit)

        return Pair(items, total)
    }

    override suspend fun save(truck: Truck): Truck {
        trucks[truck.vehicle.id.value] = truck
        return truck
    }

    override suspend fun deleteById(id: String): Boolean = trucks.remove(id) != null

    // Helper for tests to seed data
    fun seed(truck: Truck) {
        trucks[truck.vehicle.id.value] = truck
    }
}
