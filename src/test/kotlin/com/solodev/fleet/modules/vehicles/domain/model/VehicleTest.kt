package com.solodev.fleet.modules.vehicles.domain.model

import kotlin.test.Test
import kotlin.test.assertFailsWith

class VehicleTest {

    private val validVehicleId = VehicleId("v-123")
    private val validVin = "1HGBH41JXMN109186" // Valid 17-char VIN
    private val validPlate = "ABC-1234"
    private val validMake = "Toyota"
    private val validModel = "Camry"
    private val validYear = 2024

    @Test
    fun `should create vehicle with valid 17 character VIN`() {
        Vehicle(
                id = validVehicleId,
                vin = validVin,
                licensePlate = validPlate,
                make = validMake,
                model = validModel,
                year = validYear
        )
        // No exception thrown = Success
    }

    @Test
    fun `should fail if VIN is less than 17 characters`() {
        val shortVin = "ABC-123"
        assertFailsWith<IllegalArgumentException> {
            Vehicle(
                    id = validVehicleId,
                    vin = shortVin,
                    licensePlate = validPlate,
                    make = validMake,
                    model = validModel,
                    year = validYear
            )
        }
                .also { assert(it.message?.contains("17 characters") == true) }
    }

    @Test
    fun `should fail if VIN is more than 17 characters`() {
        val longVin = "1HGBH41JXMN109186-EXTRA"
        assertFailsWith<IllegalArgumentException> {
            Vehicle(
                    id = validVehicleId,
                    vin = longVin,
                    licensePlate = validPlate,
                    make = validMake,
                    model = validModel,
                    year = validYear
            )
        }
                .also { assert(it.message?.contains("17 characters") == true) }
    }

    @Test
    fun `should fail if VIN is blank`() {
        assertFailsWith<IllegalArgumentException> {
            Vehicle(
                    id = validVehicleId,
                    vin = "                 ",
                    licensePlate = validPlate,
                    make = validMake,
                    model = validModel,
                    year = validYear
            )
        }
    }
}
