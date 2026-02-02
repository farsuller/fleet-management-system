package com.solodev.fleet.modules.infrastructure.persistence

import com.solodev.fleet.modules.domain.models.Vehicle
import com.solodev.fleet.modules.domain.models.VehicleId
import com.solodev.fleet.modules.domain.models.VehicleStatus
import java.util.*
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.BeforeClass

class VehicleRepositoryTest {

    companion object {
        @BeforeClass
        @JvmStatic
        fun setup() {
            Database.connect(
                    "jdbc:h2:mem:test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;",
                    driver = "org.h2.Driver",
                    user = "sa",
                    password = ""
            )
        }
    }

    private val repository = VehicleRepositoryImpl()

    @BeforeTest
    fun prepareDb() {
        transaction {
            SchemaUtils.drop(VehiclesTable, OdometerReadingsTable)
            SchemaUtils.create(VehiclesTable, OdometerReadingsTable)
        }
    }

    @Test
    fun `should save and find vehicle`() = runBlocking {
        val vehicleId = VehicleId(UUID.randomUUID().toString())
        val vehicle =
                Vehicle(
                        id = vehicleId,
                        plateNumber = "ABC-123",
                        make = "Toyota",
                        model = "Corolla",
                        year = 2022,
                        status = VehicleStatus.ACTIVE,
                        passengerCapacity = 5,
                        currentOdometerKm = 1000
                )

        repository.save(vehicle)

        val found = repository.findById(vehicleId)
        assertNotNull(found)
        assertEquals(vehicle.plateNumber, found.plateNumber)
        assertEquals(vehicle.make, found.make)
        assertEquals(vehicle.model, found.model)
        assertEquals(vehicle.year, found.year)
    }

    @Test
    fun `should update existing vehicle`() = runBlocking {
        val vehicleId = VehicleId(UUID.randomUUID().toString())
        val vehicle =
                Vehicle(
                        id = vehicleId,
                        plateNumber = "XYZ-789",
                        make = "Ford",
                        model = "F-150",
                        year = 2021,
                        status = VehicleStatus.ACTIVE,
                        passengerCapacity = 3,
                        currentOdometerKm = 5000
                )

        repository.save(vehicle)

        val updatedVehicle = vehicle.copy(currentOdometerKm = 5500, status = VehicleStatus.RENTED)
        repository.save(updatedVehicle)

        val found = repository.findById(vehicleId)
        assertNotNull(found)
        assertEquals(5500, found.currentOdometerKm)
        assertEquals(VehicleStatus.RENTED, found.status)
    }
}
