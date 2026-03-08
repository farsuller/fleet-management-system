package com.solodev.fleet.modules.rentals.application.dto

import com.solodev.fleet.modules.rentals.domain.model.Customer
import kotlinx.serialization.Serializable

@Serializable
data class CustomerResponse(
        val id: String,
        val userId: String?,
        val firstName: String,
        val lastName: String,
        val email: String,
        val phone: String,
        val driverLicenseNumber: String,
        val licenseExpiryMs: Long,
        val isActive: Boolean,
        val createdAt: Long,
) {
        companion object {
                fun fromDomain(c: Customer) =
                        CustomerResponse(
                                id = c.id.value,
                                userId = c.userId?.toString(),
                                firstName = c.firstName,
                                lastName = c.lastName,
                                email = c.email,
                                phone = c.phone,
                                driverLicenseNumber = c.driverLicenseNumber,
                                licenseExpiryMs = c.driverLicenseExpiry.toEpochMilli(),
                                isActive = c.isActive,
                                createdAt = c.createdAt.toEpochMilli(),
                        )
        }
}
