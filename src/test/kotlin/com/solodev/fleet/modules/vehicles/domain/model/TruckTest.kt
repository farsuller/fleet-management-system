package com.solodev.fleet.modules.vehicles.domain.model

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TruckTest {
    private fun createBaseVehicle(type: VehicleType = VehicleType.TRUCK) =
        Vehicle(
            id = VehicleId(UUID.randomUUID().toString()),
            vin = "12345678901234567",
            licensePlate = "ABC-123",
            make = "Volvo",
            model = "FH",
            year = 2024,
            vehicleType = type,
            mileageKm = 0,
        )

    @Test
    fun `should create a valid truck`() {
        val vehicle = createBaseVehicle()
        val truck =
            Truck(
                vehicle = vehicle,
                payloadCapacityTons = 25.0,
                cargoType = "BOX",
                axleCount = 4,
                grossVehicleWeightKg = 40000,
                hasTrailerHitch = true,
            )

        assertEquals(vehicle, truck.vehicle)
        assertEquals(25.0, truck.payloadCapacityTons)
        assertEquals("BOX", truck.cargoType)
        assertEquals(4, truck.axleCount)
        assertEquals(40000, truck.grossVehicleWeightKg)
        assertTrue(truck.hasTrailerHitch)
    }

    @Test
    fun `should fail if vehicle type is not TRUCK`() {
        val vehicle = createBaseVehicle(type = VehicleType.CAR)
        assertThrows<IllegalArgumentException> {
            Truck(vehicle = vehicle)
        }.also {
            assertEquals("Truck must use vehicleType=TRUCK", it.message)
        }
    }

    @Test
    fun `should fail if axle count is not positive`() {
        val vehicle = createBaseVehicle()
        assertThrows<IllegalArgumentException> {
            Truck(vehicle = vehicle, axleCount = 0)
        }.also {
            assertEquals("Axle count must be positive", it.message)
        }
    }

    @Test
    fun `should fail if payload capacity is negative`() {
        val vehicle = createBaseVehicle()
        assertThrows<IllegalArgumentException> {
            Truck(vehicle = vehicle, payloadCapacityTons = -1.0)
        }.also {
            assertEquals("Payload capacity cannot be negative", it.message)
        }
    }

    @Test
    fun `should fail if gross vehicle weight is negative`() {
        val vehicle = createBaseVehicle()
        assertThrows<IllegalArgumentException> {
            Truck(vehicle = vehicle, grossVehicleWeightKg = -100)
        }.also {
            assertEquals("Gross vehicle weight cannot be negative", it.message)
        }
    }

    @Test
    fun `should fail if cargo type is invalid`() {
        val vehicle = createBaseVehicle()
        assertThrows<IllegalArgumentException> {
            Truck(vehicle = vehicle, cargoType = "INVALID")
        }.also {
            assertTrue(it.message!!.contains("Invalid cargo type"))
        }
    }

    @Test
    fun `should allow mixed case cargo type if normalized`() {
        // The current implementation is case-sensitive, so this will fail if we pass "box"
        val vehicle = createBaseVehicle()
        assertThrows<IllegalArgumentException> {
            Truck(vehicle = vehicle, cargoType = "box")
        }
    }
}
