package com.solodev.fleet.modules.rentals.application.dto

import com.solodev.fleet.modules.rentals.domain.model.Customer
import kotlinx.serialization.Serializable

/** Lightweight summary of the driver assigned to this customer's active rental. */
@Serializable
data class CustomerDriverSummary(
    val driverId: String,
    val driverName: String,
    val licenseNumber: String,
    val phone: String,
)

/** Lightweight summary of the vehicle used in this customer's active rental. */
@Serializable
data class CustomerVehicleSummary(
    val vehicleId: String,
    val licensePlate: String,
    val make: String,
    val model: String,
    val year: Int,
)

/** Extended response for GET /v1/customers/{id} — includes active rental driver+vehicle. */
@Serializable
data class CustomerDetailResponse(
    val id: String,
    val userId: String?,
    val firstName: String,
    val lastName: String,
    val email: String,
    val phone: String,
    val driverLicenseNumber: String,
    val licenseExpiryMs: Long,
    val address: String?,
    val city: String?,
    val state: String?,
    val postalCode: String?,
    val country: String?,
    val isActive: Boolean,
    val createdAt: Long,
    val assignedDriver: CustomerDriverSummary?,
    val activeVehicle: CustomerVehicleSummary?,
) {
    companion object {
        fun fromDomain(
            c: Customer,
            assignedDriver: CustomerDriverSummary? = null,
            activeVehicle: CustomerVehicleSummary? = null,
        ) = CustomerDetailResponse(
            id                  = c.id.value,
            userId              = c.userId?.toString(),
            firstName           = c.firstName,
            lastName            = c.lastName,
            email               = c.email,
            phone               = c.phone,
            driverLicenseNumber = c.driverLicenseNumber,
            licenseExpiryMs     = c.driverLicenseExpiry.toEpochMilli(),
            address             = c.address,
            city                = c.city,
            state               = c.state,
            postalCode          = c.postalCode,
            country             = c.country,
            isActive            = c.isActive,
            createdAt           = c.createdAt.toEpochMilli(),
            assignedDriver      = assignedDriver,
            activeVehicle       = activeVehicle,
        )
    }
}

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
