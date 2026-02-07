package com.solodev.fleet.modules.rentals.application.dto

import kotlinx.serialization.Serializable

@Serializable
data class CustomerRequest(
        val email: String,
        val firstName: String,
        val lastName: String,
        val phone: String,
        val driversLicense: String,
        val driverLicenseExpiry: String, // ISO date format: YYYY-MM-DD
        val address: String? = null,
        val city: String? = null,
        val state: String? = null,
        val postalCode: String? = null,
        val country: String? = null
) {
        init {
                require(email.isNotBlank() && email.contains("@")) { "Valid email required" }
                require(firstName.isNotBlank()) { "First name cannot be blank" }
                require(lastName.isNotBlank()) { "Last name cannot be blank" }
                require(phone.isNotBlank()) { "Phone cannot be blank" }
                require(driversLicense.isNotBlank()) { "Driver's license cannot be blank" }
                require(driverLicenseExpiry.isNotBlank()) {
                        "Driver's license expiry cannot be blank"
                }
        }
}
