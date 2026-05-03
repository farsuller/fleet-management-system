package com.solodev.fleet.modules.vehicles.domain.model

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BusTest {
    private fun createBaseVehicle(type: VehicleType = VehicleType.BUS) =
        Vehicle(
            id = VehicleId(UUID.randomUUID().toString()),
            vin = "12345678901234567",
            licensePlate = "BUS-123",
            make = "Mercedes",
            model = "Citaro",
            year = 2024,
            vehicleType = type,
            mileageKm = 0,
        )

    @Test
    fun `should create a valid bus`() {
        val vehicle = createBaseVehicle()
        val bus =
            Bus(
                vehicle = vehicle,
                routeNumber = "RT-101",
                doorCount = 3,
                standingCapacity = 50,
                hasAccessibilityRamp = true,
                hasAirConditioning = true,
            )

        assertEquals(vehicle, bus.vehicle)
        assertEquals("RT-101", bus.routeNumber)
        assertEquals(3, bus.doorCount)
        assertEquals(50, bus.standingCapacity)
        assertTrue(bus.hasAccessibilityRamp)
        assertTrue(bus.hasAirConditioning)
    }

    @Test
    fun `should fail if vehicle type is not BUS`() {
        val vehicle = createBaseVehicle(type = VehicleType.CAR)
        assertThrows<IllegalArgumentException> {
            Bus(vehicle = vehicle)
        }.also {
            assertEquals("Bus must use vehicleType=BUS", it.message)
        }
    }

    @Test
    fun `should fail if door count is not positive`() {
        val vehicle = createBaseVehicle()
        assertThrows<IllegalArgumentException> {
            Bus(vehicle = vehicle, doorCount = 0)
        }.also {
            assertEquals("Door count must be positive", it.message)
        }
    }

    @Test
    fun `should fail if standing capacity is negative`() {
        val vehicle = createBaseVehicle()
        assertThrows<IllegalArgumentException> {
            Bus(vehicle = vehicle, standingCapacity = -5)
        }.also {
            assertEquals("Standing capacity cannot be negative", it.message)
        }
    }
}
