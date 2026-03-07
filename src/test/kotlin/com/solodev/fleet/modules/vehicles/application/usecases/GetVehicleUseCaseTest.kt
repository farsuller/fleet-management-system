package com.solodev.fleet.modules.vehicles.application.usecases

import com.solodev.fleet.modules.vehicles.domain.model.*
import com.solodev.fleet.modules.vehicles.domain.repository.VehicleRepository
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.*

class GetVehicleUseCaseTest {

    private val repository = mockk<VehicleRepository>()
    private val useCase = GetVehicleUseCase(repository)

    @Test
    fun `returns vehicle when found`() = runBlocking {
        val vehicle = sampleVehicle()
        coEvery { repository.findById(any()) } returns vehicle

        val result = useCase.execute("veh-001")

        assertNotNull(result)
        assertEquals("1HGBH41JXMN109186", result!!.vin)
    }

    @Test
    fun `returns null when vehicle not found`() = runBlocking {
        coEvery { repository.findById(any()) } returns null

        val result = useCase.execute("unknown")

        assertNull(result)
    }

    private fun sampleVehicle() = Vehicle(
        id = VehicleId("veh-001"),
        vin = "1HGBH41JXMN109186",
        licensePlate = "ABC-1234",
        make = "Toyota",
        model = "Corolla",
        year = 2023,
        mileageKm = 5000,
        state = VehicleState.AVAILABLE
    )
}
