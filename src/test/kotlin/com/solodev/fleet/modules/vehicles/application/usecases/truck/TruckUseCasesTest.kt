package com.solodev.fleet.modules.vehicles.application.usecases.truck

import com.solodev.fleet.modules.vehicles.application.dto.TruckRequest
import com.solodev.fleet.modules.vehicles.application.dto.TruckUpdateRequest
import com.solodev.fleet.modules.vehicles.application.repository.FakeTruckRepository
import com.solodev.fleet.modules.vehicles.domain.model.Truck
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

class TruckUseCasesTest {
    private lateinit var repository: FakeTruckRepository
    private lateinit var createUseCase: CreateTruckUseCase
    private lateinit var getUseCase: GetTruckUseCase
    private lateinit var updateUseCase: UpdateTruckUseCase
    private lateinit var deleteUseCase: DeleteTruckUseCase
    private lateinit var listUseCase: ListTrucksUseCase

    @BeforeEach
    fun setup() {
        repository = FakeTruckRepository()
        createUseCase = CreateTruckUseCase(repository)
        getUseCase = GetTruckUseCase(repository)
        updateUseCase = UpdateTruckUseCase(repository)
        deleteUseCase = DeleteTruckUseCase(repository)
        listUseCase = ListTrucksUseCase(repository)
    }

    @Test
    fun `CreateTruckUseCase should save a new truck`() =
        runTest {
            val request =
                TruckRequest(
                    licensePlate = "TRK-123",
                    make = "Scania",
                    model = "R500",
                    year = 2022,
                    payloadCapacityTons = 25.0,
                    axleCount = 3,
                )

            val result = createUseCase.execute(request)

            assertNotNull(result)
            assertEquals("TRK-123", result.vehicle.licensePlate)
            assertEquals(25.0, result.payloadCapacityTons)

            val saved = repository.findById(result.vehicle.id.value)
            assertNotNull(saved)
            assertEquals("Scania", saved!!.vehicle.make)
        }

    @Test
    fun `GetTruckUseCase should return truck if exists`() =
        runTest {
            val id = "truck-1"
            val truck = createSampleTruck(id)
            repository.seed(truck)

            val result = getUseCase.execute(id)

            assertNotNull(result)
            assertEquals(id, result!!.vehicle.id.value)
        }

    @Test
    fun `GetTruckUseCase should return null if not exists`() =
        runTest {
            val result = getUseCase.execute("none")
            assertNull(result)
        }

    @Test
    fun `UpdateTruckUseCase should modify existing truck`() =
        runTest {
            val id = "truck-u1"
            val truck = createSampleTruck(id)
            repository.seed(truck)

            val request =
                TruckUpdateRequest(
                    licensePlate = "UPD-456",
                    payloadCapacityTons = 30.0,
                )

            val result = updateUseCase.execute(id, request)

            assertNotNull(result)
            assertEquals("UPD-456", result!!.vehicle.licensePlate)
            assertEquals(30.0, result.payloadCapacityTons)

            val saved = repository.findById(id)
            assertEquals("UPD-456", saved!!.vehicle.licensePlate)
        }

    @Test
    fun `DeleteTruckUseCase should remove truck from repository`() =
        runTest {
            val id = "truck-d1"
            val truck = createSampleTruck(id)
            repository.seed(truck)

            val result = deleteUseCase.execute(id)

            assertTrue(result)
            assertNull(repository.findById(id))
        }

    @Test
    fun `ListTrucksUseCase should return paginated trucks`() =
        runTest {
            repeat(5) { i ->
                repository.seed(createSampleTruck("t-$i"))
            }

            val params = PaginationParams(limit = 2, cursor = null)
            val result = listUseCase.execute(params)

            assertEquals(2, result.items.size)
            assertEquals(5, result.total)
            assertNotNull(result.nextCursor)
        }

    private fun createSampleTruck(id: String): Truck =
        Truck(
            vehicle =
                Vehicle(
                    id = VehicleId(id),
                    licensePlate = "PLATE-$id",
                    make = "Volvo",
                    model = "FH",
                    year = 2021,
                    vehicleType = VehicleType.TRUCK,
                ),
            payloadCapacityTons = 20.0,
            axleCount = 2,
            hasTrailerHitch = true,
        )
}
