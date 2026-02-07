package com.solodev.fleet.modules.rentals.application.dto

import com.solodev.fleet.modules.domain.models.Customer
import kotlinx.serialization.Serializable

@Serializable
data class CustomerResponse(
        val id: String,
        val email: String,
        val fullName: String,
        val phone: String,
        val driversLicense: String,
        val isActive: Boolean
) {
        companion object {
                fun fromDomain(c: Customer) =
                        CustomerResponse(
                                id = c.id.value,
                                email = c.email,
                                fullName = c.fullName,
                                phone = c.phone,
                                driversLicense = c.driverLicenseNumber,
                                isActive = c.isActive
                        )
        }
}
