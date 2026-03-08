package com.solodev.fleet.modules.vehicles.application.usecases

import com.solodev.fleet.modules.vehicles.domain.model.*
import com.solodev.fleet.modules.vehicles.domain.repository.VehicleRepository
import com.solodev.fleet.shared.models.PaginationParams
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ListVehiclesUseCaseTest {

    private val repository = mockk<VehicleRepository>()
    private val useCase = ListVehiclesUseCase(repository)

    @Test
    fun shouldReturnPaginatedVehicles_WhenVehiclesExist() = runBlocking {
        // Arrange
        val vehicle1 = sampleVehicle("veh-001", "1HGBH41JXMN109186", "ABC-1234")
        val vehicle2 = sampleVehicle("veh-002", "2HGBH41JXMN109187", "XYZ-5678")
        val params = PaginationParams(limit = 10, cursor = null)
        coEvery { repository.findAll(params) } returns Pair(listOf(vehicle1, vehicle2), 2L)

        // Act
        val result = useCase.execute(params)

        // Assert
        assertThat(result.items).hasSize(2)
        assertThat(result.total).isEqualTo(2L)
    }

    @Test
    fun shouldReturnEmptyList_WhenNoVehiclesExist() = runBlocking {
        // Arrange
        val params = PaginationParams(limit = 10, cursor = null)
        coEvery { repository.findAll(params) } returns Pair(emptyList(), 0L)

        // Act
        val result = useCase.execute(params)

        // Assert
        assertThat(result.items).isEmpty()
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
