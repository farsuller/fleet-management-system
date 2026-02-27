package com.solodev.fleet.modules.tracking.application.usecases

import com.solodev.fleet.modules.tracking.infrastructure.persistence.PostGISAdapter
import com.solodev.fleet.modules.vehicles.domain.model.Vehicle
import com.solodev.fleet.modules.vehicles.domain.model.VehicleId
import com.solodev.fleet.modules.vehicles.domain.repository.VehicleRepository
import com.solodev.fleet.shared.domain.model.Location
import com.solodev.fleet.shared.models.PaginationParams
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.runBlocking

class UpdateVehicleLocationUseCaseTest {

    // Manual Mock Repository
    class MockVehicleRepository : VehicleRepository {
        var vehicle: Vehicle? = null
        var lastSavedVehicle: Vehicle? = null

        override suspend fun findById(id: VehicleId): Vehicle? = vehicle
        override suspend fun save(vehicle: Vehicle): Vehicle {
            lastSavedVehicle = vehicle
            return vehicle
        }
        override suspend fun findByPlateNumber(plateNumber: String): Vehicle? = null
        override suspend fun findAll(params: PaginationParams): Pair<List<Vehicle>, Long> =
                Pair(emptyList(), 0)
        override suspend fun deleteById(id: VehicleId): Boolean = true
    }

    // Manual Mock Spatial Adapter
    class MockSpatialAdapter : PostGISAdapter() {
        var snapResult: Pair<Location, Double>? = null
        override fun snapToRoute(location: Location, routeId: UUID): Pair<Location, Double>? =
                snapResult
    }

    @Test
    fun `should update vehicle location without snapping if no route provided`() {
        runBlocking {
            val repo = MockVehicleRepository()
            val adapter = MockSpatialAdapter()
            val useCase = UpdateVehicleLocationUseCase(repo, adapter)

            val vehicleId = "v-123"
            val initialLocation = Location(10.0, 10.0)
            repo.vehicle =
                    Vehicle(
                            id = VehicleId(vehicleId),
                            vin = "1HGBH41JXMN109186",
                            licensePlate = "ABC-1234",
                            make = "Toyota",
                            model = "Camry",
                            year = 2024,
                            lastLocation = initialLocation
                    )

            val newLocation = Location(14.5, 121.5)
            val result = useCase.execute(vehicleId, newLocation, null)

            assertEquals(newLocation, result)
            assertEquals(newLocation, repo.lastSavedVehicle?.lastLocation)
        }
    }

    @Test
    fun `should snap location if route is provided`() {
        runBlocking {
            val repo = MockVehicleRepository()
            val adapter = MockSpatialAdapter()
            val useCase = UpdateVehicleLocationUseCase(repo, adapter)

            val vehicleId = "v-123"
            repo.vehicle =
                    Vehicle(
                            id = VehicleId(vehicleId),
                            vin = "1HGBH41JXMN109186",
                            licensePlate = "ABC-1234",
                            make = "Toyota",
                            model = "Camry",
                            year = 2024
                    )

            val rawLocation = Location(14.5, 121.5)
            val snappedLocation = Location(14.5001, 121.5001)
            val progress = 0.45
            adapter.snapResult = Pair(snappedLocation, progress)

            val routeId = UUID.randomUUID()
            val result = useCase.execute(vehicleId, rawLocation, routeId)

            assertEquals(snappedLocation, result)
            assertEquals(snappedLocation, repo.lastSavedVehicle?.lastLocation)
            assertEquals(progress, repo.lastSavedVehicle?.routeProgress)
        }
    }

    @Test
    fun `should fail if vehicle does not exist`() {
        runBlocking {
            val repo = MockVehicleRepository()
            val adapter = MockSpatialAdapter()
            val useCase = UpdateVehicleLocationUseCase(repo, adapter)

            repo.vehicle = null

            assertFailsWith<com.solodev.fleet.shared.exceptions.NotFoundException> {
                useCase.execute("non-existent", Location(10.0, 10.0))
            }
        }
    }
}
