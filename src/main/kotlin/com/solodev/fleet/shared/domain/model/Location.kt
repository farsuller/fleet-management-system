package com.solodev.fleet.shared.domain.model

import kotlinx.serialization.Serializable

/**
 * Represents a GPS coordinate (WGS 84).
 */
@Serializable
data class Location(
    val latitude: Double,
    val longitude: Double
) {
    init {
        require(latitude in -90.0..90.0) { "Latitude must be between -90 and 90" }
        require(longitude in -180.0..180.0) { "Longitude must be between -180 and 180" }
    }

    override fun toString(): String = "$latitude,$longitude"

    companion object {
        fun fromString(str: String): Location {
            val parts = str.split(",")
            return Location(parts[0].toDouble(), parts[1].toDouble())
        }
    }
}
