package com.solodev.fleet.modules.rentals.application.dto

import com.solodev.fleet.shared.utils.ValidationUtils
import kotlinx.serialization.Serializable

/** Public registration endpoint — creates a user (CUSTOMER role) + customer record atomically. */
@Serializable
data class CustomerRegistrationRequest(
    val email: String,
    val passwordRaw: String,
    val firstName: String,
    val lastName: String,
    val phone: String,
    val driversLicense: String,
    val driverLicenseExpiry: String, // YYYY-MM-DD
    val address: String? = null,
    val city: String? = null,
    val state: String? = null,
    val postalCode: String? = null,
    val country: String? = null,
) {
    init {
        ValidationUtils.validateEmail(email)
        ValidationUtils.validatePassword(passwordRaw)
        ValidationUtils.validateName(firstName, "First name")
        ValidationUtils.validateName(lastName, "Last name")
        ValidationUtils.validatePhone(phone)
        require(driversLicense.isNotBlank()) { "Driver's license cannot be blank" }
        require(driverLicenseExpiry.isNotBlank()) { "License expiry cannot be blank" }
    }
}
