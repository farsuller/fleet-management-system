package com.solodev.fleet.modules.vehicles.application.dto

import kotlinx.serialization.Serializable

@Serializable
data class VehicleRequest(
        val vin: String,
        val licensePlate: String,
        val make: String,
        val model: String,
        val year: Int,
        val color: String? = null,
        val mileageKm: Int = 0
) {
        init {
                require(vin.isNotBlank()) { "VIN cannot be blank" }
                require(vin.length == 17) { "VIN must be exactly 17 characters" }
                require(licensePlate.isNotBlank()) { "License plate cannot be blank" }
                require(make.isNotBlank()) { "Make cannot be blank" }
                require(model.isNotBlank()) { "Model cannot be blank" }
                require(year in 1900..2100) { "Year must be between 1900 and 2100" }
                require(mileageKm >= 0) { "Mileage cannot be negative" }
        }
}