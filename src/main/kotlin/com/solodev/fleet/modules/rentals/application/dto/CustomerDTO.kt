package com.solodev.fleet.modules.rentals.application.dto

import com.solodev.fleet.modules.domain.models.Customer
import kotlinx.serialization.Serializable

@Serializable
data class CustomerRequest(
        val firstName: String,
        val lastName: String,
        val email: String,
        val phone: String,
        val driverLicenseNumber: String,
        val driverLicenseExpiry: String // ISO-8601
)

@Serializable
data class CustomerResponse(
        val id: String,
        val fullName: String,
        val email: String,
        val phone: String
) {
    companion object {
        fun fromDomain(c: Customer) =
                CustomerResponse(
                        id = c.id.value,
                        fullName = c.fullName,
                        email = c.email,
                        phone = c.phone
                )
    }
}
