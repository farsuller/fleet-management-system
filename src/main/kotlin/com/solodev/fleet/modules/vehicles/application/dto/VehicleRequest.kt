package com.solodev.fleet.modules.vehicles.application.dto

import kotlinx.serialization.Serializable

@Serializable
data class VehicleRequest(
        val plateNumber: String,
        val make: String,
        val model: String,
        val year: Int,
        val capacity: Int? = null
)
