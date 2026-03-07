package com.solodev.fleet.modules.vehicles.application.usecases

import com.solodev.fleet.modules.vehicles.application.dto.VehicleRequest
import com.solodev.fleet.modules.vehicles.domain.model.*
import com.solodev.fleet.modules.vehicles.domain.repository.VehicleRepository
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.*

class CreateVehicleUseCaseTest {

    private val repository = mockk<VehicleRepository>()
    private val useCase = CreateVehicleUseCase(repository)

    @Test
    fun `creates vehicle with valid data`() = runBlocking {
        coEvery { repository.save(any()) } returnsArgument 0

        val request = VehicleRequest(
            vin = "1HGBH41JXMN109186",
            licensePlate = "ABC-1234",
            make = "Toyota",
            model = "Corolla",
            year = 2023
        )

        val result = useCase.execute(request)

        assertEquals("1HGBH41JXMN109186", result.vin)
        assertEquals("ABC-1234", result.licensePlate)
        assertEquals(VehicleState.AVAILABLE, result.state)
        coVerify { repository.save(any()) }
    }

    @Test
    fun `new vehicle state defaults to AVAILABLE`() = runBlocking {
        coEvery { repository.save(any()) } returnsArgument 0

        val request = VehicleRequest(
            vin = "1HGBH41JXMN109186",
            licensePlate = "ABC-1234",
            make = "Toyota",
            model = "Corolla",
            year = 2023
        )

        val result = useCase.execute(request)

        assertEquals(VehicleState.AVAILABLE, result.state)
    }
}
