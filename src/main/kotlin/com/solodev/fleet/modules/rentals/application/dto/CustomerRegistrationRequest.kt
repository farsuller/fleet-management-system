package com.solodev.fleet.modules.rentals.application.dto

import com.solodev.fleet.shared.utils.ValidationUtils
import kotlinx.serialization.Serializable

/** Public registration endpoint â€” creates a user (CUSTOMER role) + customer record atomically. */
@Serializable
data class CustomerRegistrationRequest(
    val email: String,
    val passwordRaw: String,
    val firstName: String? = null,
    val lastName: String? = null,
    val phone: String,
    val driversLicense: String? = null,
    val driverLicenseExpiry: String? = null, // YYYY-MM-DD
    val address: String? = null,
    val city: String? = null,
    val state: String? = null,
    val postalCode: String? = null,
    val country: String? = null,
) {
    init {
        ValidationUtils.validateEmail(email)
        ValidationUtils.validatePassword(passwordRaw)
        firstName?.let { ValidationUtils.validateName(it, "First name") }
        lastName?.let { ValidationUtils.validateName(it, "Last name") }
        ValidationUtils.validatePhone(phone)
        require(driversLicense == null || driversLicense.isNotBlank()) { "Driver's license cannot be blank" }
        require(driverLicenseExpiry == null || driverLicenseExpiry.isNotBlank()) { "License expiry cannot be blank" }
    }
}
