package com.solodev.fleet.modules.vehicles.application.usecases

import com.solodev.fleet.modules.vehicles.domain.model.*
import com.solodev.fleet.modules.vehicles.domain.repository.VehicleRepository
import com.solodev.fleet.shared.models.PaginationParams
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.*

class ListVehiclesUseCaseTest {

    private val repository = mockk<VehicleRepository>()
    private val useCase = ListVehiclesUseCase(repository)

    @Test
    fun `returns vehicles in paginated response`() = runBlocking {
        val vehicle1 = sampleVehicle("veh-001", "1HGBH41JXMN109186", "ABC-1234")
        val vehicle2 = sampleVehicle("veh-002", "2HGBH41JXMN109187", "XYZ-5678")
        val params = PaginationParams(limit = 10, cursor = null)

        coEvery { repository.findAll(params) } returns Pair(listOf(vehicle1, vehicle2), 2L)

        val result = useCase.execute(params)

        assertEquals(2, result.items.size)
        assertEquals(2L, result.total)
    }

    @Test
    fun `returns empty list when no vehicles`() = runBlocking {
        val params = PaginationParams(limit = 10, cursor = null)
        coEvery { repository.findAll(params) } returns Pair(emptyList(), 0L)

        val result = useCase.execute(params)

        assertEquals(0, result.items.size)
    }

    private fun sampleVehicle(id: String, vin: String, plate: String) = Vehicle(
        id = VehicleId(id),
        vin = vin,
        licensePlate = plate,
        make = "Toyota",
        model = "Corolla",
        year = 2023,
        mileageKm = 0
    )
}
