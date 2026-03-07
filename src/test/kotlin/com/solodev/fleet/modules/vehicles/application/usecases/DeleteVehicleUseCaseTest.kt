package com.solodev.fleet.modules.vehicles.application.usecases

import com.solodev.fleet.modules.vehicles.domain.model.VehicleId
import com.solodev.fleet.modules.vehicles.domain.repository.VehicleRepository
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.*

class DeleteVehicleUseCaseTest {

    private val repository = mockk<VehicleRepository>()
    private val useCase = DeleteVehicleUseCase(repository)

    @Test
    fun `deletes vehicle and returns true when found`() = runBlocking {
        coEvery { repository.deleteById(any()) } returns true

        val result = useCase.execute("veh-001")

        assertTrue(result)
        coVerify { repository.deleteById(VehicleId("veh-001")) }
    }

    @Test
    fun `returns false when vehicle not found`() = runBlocking {
        coEvery { repository.deleteById(any()) } returns false

        val result = useCase.execute("unknown")

        assertFalse(result)
    }
}
