package com.solodev.fleet.modules.vehicles.application.usecases.bus

import com.solodev.fleet.modules.vehicles.application.dto.BusRequest
import com.solodev.fleet.modules.vehicles.application.dto.BusUpdateRequest
import com.solodev.fleet.modules.vehicles.application.repository.FakeBusRepository
import com.solodev.fleet.modules.vehicles.domain.model.Bus
import com.solodev.fleet.modules.vehicles.domain.model.Vehicle
import com.solodev.fleet.modules.vehicles.domain.model.VehicleId
import com.solodev.fleet.modules.vehicles.domain.model.VehicleType
import com.solodev.fleet.shared.models.PaginationParams
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class BusUseCasesTest {
    private lateinit var repository: FakeBusRepository
    private lateinit var createUseCase: CreateBusUseCase
    private lateinit var getUseCase: GetBusUseCase
    private lateinit var updateUseCase: UpdateBusUseCase
    private lateinit var deleteUseCase: DeleteBusUseCase
    private lateinit var listUseCase: ListBusesUseCase

    @BeforeEach
    fun setup() {
        repository = FakeBusRepository()
        createUseCase = CreateBusUseCase(repository)
        getUseCase = GetBusUseCase(repository)
        updateUseCase = UpdateBusUseCase(repository)
        deleteUseCase = DeleteBusUseCase(repository)
        listUseCase = ListBusesUseCase(repository)
    }

    @Test
    fun `CreateBusUseCase should save a new bus`() =
        runTest {
            val request =
                BusRequest(
                    licensePlate = "BUS-111",
                    make = "Mercedes-Benz",
                    model = "Citaro",
                    year = 2023,
                    routeNumber = "Line 10",
                    standingCapacity = 50,
                )

            val result = createUseCase.execute(request)

            assertNotNull(result)
            assertEquals("BUS-111", result.vehicle.licensePlate)
            assertEquals("Line 10", result.routeNumber)

            val saved = repository.findById(result.vehicle.id.value)
            assertNotNull(saved)
            assertEquals("Mercedes-Benz", saved!!.vehicle.make)
        }

    @Test
    fun `GetBusUseCase should return bus if exists`() =
        runTest {
            val id = "bus-1"
            val bus = createSampleBus(id)
            repository.seed(bus)

            val result = getUseCase.execute(id)

            assertNotNull(result)
            assertEquals(id, result!!.vehicle.id.value)
        }

    @Test
    fun `GetBusUseCase should return null if not exists`() =
        runTest {
            val result = getUseCase.execute("ghost")
            assertNull(result)
        }

    @Test
    fun `UpdateBusUseCase should modify existing bus`() =
        runTest {
            val id = "bus-u1"
            val bus = createSampleBus(id)
            repository.seed(bus)

            val request =
                BusUpdateRequest(
                    licensePlate = "NEW-BUS",
                    standingCapacity = 60,
                )

            val result = updateUseCase.execute(id, request)

            assertNotNull(result)
            assertEquals("NEW-BUS", result!!.vehicle.licensePlate)
            assertEquals(60, result.standingCapacity)

            val saved = repository.findById(id)
            assertEquals("NEW-BUS", saved!!.vehicle.licensePlate)
        }

    @Test
    fun `DeleteBusUseCase should remove bus from repository`() =
        runTest {
            val id = "bus-d1"
            val bus = createSampleBus(id)
            repository.seed(bus)

            val result = deleteUseCase.execute(id)

            assertTrue(result)
            assertNull(repository.findById(id))
        }

    @Test
    fun `ListBusesUseCase should return paginated buses`() =
        runTest {
            repeat(3) { i ->
                repository.seed(createSampleBus("b-$i"))
            }

            val params = PaginationParams(limit = 2, cursor = null)
            val result = listUseCase.execute(params)

            assertEquals(2, result.items.size)
            assertEquals(3, result.total)
        }

    private fun createSampleBus(id: String): Bus =
        Bus(
            vehicle =
                Vehicle(
                    id = VehicleId(id),
                    licensePlate = "BPLATE-$id",
                    make = "MAN",
                    model = "Lion's City",
                    year = 2022,
                    vehicleType = VehicleType.BUS,
                ),
            routeNumber = "B1",
            doorCount = 3,
            standingCapacity = 40,
            hasAccessibilityRamp = true,
            hasAirConditioning = true,
        )
}
