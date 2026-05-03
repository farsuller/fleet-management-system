package com.solodev.fleet.modules.vehicles.application.repository

import com.solodev.fleet.modules.vehicles.domain.model.Bus
import com.solodev.fleet.modules.vehicles.domain.repository.BusRepository
import com.solodev.fleet.shared.models.PaginationParams

class FakeBusRepository : BusRepository {
    private val buses = mutableMapOf<String, Bus>()

    override suspend fun findById(id: String): Bus? = buses[id]

    override suspend fun findAll(params: PaginationParams): Pair<List<Bus>, Long> {
        val allBuses = buses.values.toList()
        val total = allBuses.size.toLong()

        val items =
            allBuses
                .sortedBy { it.vehicle.id.value }
                .drop(allBuses.indexOfFirst { it.vehicle.id.value == params.cursor }.let { if (it == -1) 0 else it + 1 })
                .take(params.limit)

        return Pair(items, total)
    }

    override suspend fun save(bus: Bus): Bus {
        buses[bus.vehicle.id.value] = bus
        return bus
    }

    override suspend fun deleteById(id: String): Boolean = buses.remove(id) != null

    fun seed(bus: Bus) {
        buses[bus.vehicle.id.value] = bus
    }
}
