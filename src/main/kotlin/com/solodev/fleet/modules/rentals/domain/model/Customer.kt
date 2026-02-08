package com.solodev.fleet.modules.rentals.domain.model

import java.time.Instant
import java.util.UUID

/** Customer domain entity. */
data class Customer(
    val id: CustomerId,
    val userId: UUID? = null,
    val firstName: String,
    val lastName: String,
    val email: String,
    val phone: String,
    val driverLicenseNumber: String,
    val driverLicenseExpiry: Instant,
    val address: String? = null,
    val city: String? = null,
    val state: String? = null,
    val postalCode: String? = null,
    val country: String? = null,
    val isActive: Boolean = true
) {
    init {
        require(firstName.isNotBlank()) { "First name cannot be blank" }
        require(lastName.isNotBlank()) { "Last name cannot be blank" }
        require(email.isNotBlank()) { "Email cannot be blank" }
        require(phone.isNotBlank()) { "Phone cannot be blank" }
        require(driverLicenseNumber.isNotBlank()) { "Driver license number cannot be blank" }
    }

    val fullName: String
        get() = "$firstName $lastName"
}