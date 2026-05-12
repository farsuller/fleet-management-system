package com.solodev.fleet.modules.drivers.application.dto

import com.solodev.fleet.modules.drivers.domain.model.Driver
import com.solodev.fleet.modules.drivers.domain.model.VehicleDriverAssignment
import com.solodev.fleet.shared.utils.ValidationUtils
import kotlinx.serialization.Serializable

/** Used by back-office CRUD (no password — driver account created separately via /register). */
@Serializable
data class DriverRequest(
    val email: String,
    val firstName: String? = null,
    val lastName: String? = null,
    val phone: String? = null,
    val licenseNumber: String? = null,
    val licenseExpiry: String? = null, // YYYY-MM-DD
    val licenseClass: String? = null,
    val address: String? = null,
    val city: String? = null,
    val province: String? = null,
    val postalCode: String? = null,
    val country: String? = null,
) {
    init {
        ValidationUtils.validateEmail(email)
        firstName?.let { ValidationUtils.validateName(it, "First name") }
        lastName?.let { ValidationUtils.validateName(it, "Last name") }
        ValidationUtils.validatePhone(phone)
    }
}

/** Used by the mobile-app public registration endpoint. */
@Serializable
data class DriverRegistrationRequest(
    val email: String,
    val passwordRaw: String,
    val firstName: String? = null,
    val lastName: String? = null,
    val phone: String? = null,
    val licenseNumber: String? = null,
    val licenseExpiry: String? = null, // YYYY-MM-DD
    val licenseClass: String? = null,
    val address: String? = null,
    val city: String? = null,
    val province: String? = null,
    val postalCode: String? = null,
    val country: String? = null,
    val isEncrypted: Boolean = false,
) {
    init {
        if (!isEncrypted) {
            ValidationUtils.validateEmail(email)
            ValidationUtils.validatePassword(passwordRaw)
            firstName?.let { ValidationUtils.validateName(it, "First name") }
            lastName?.let { ValidationUtils.validateName(it, "Last name") }
            ValidationUtils.validatePhone(phone)
        }
    }
}

@Serializable
data class AssignDriverRequest(
    val vehicleId: String,
    val notes: String? = null,
)

@Serializable
data class AssignmentResponse(
    val id: String,
    val vehicleId: String,
    val driverId: String,
    val assignedAt: Long,
    val releasedAt: Long?,
    val isActive: Boolean,
    val notes: String?,
) {
    companion object {
        fun fromDomain(a: VehicleDriverAssignment) =
            AssignmentResponse(
                id = a.id,
                vehicleId = a.vehicleId,
                driverId = a.driverId,
                assignedAt = a.assignedAt.toEpochMilli(),
                releasedAt = a.releasedAt?.toEpochMilli(),
                isActive = a.isActive,
                notes = a.notes,
            )
    }
}

@Serializable
data class DriverResponse(
    val id: String,
    val userId: String?,
    val firstName: String? = null,
    val lastName: String? = null,
    val email: String,
    val phone: String?,
    val licenseNumber: String? = null,
    val licenseExpiryMs: Long?,
    val licenseClass: String?,
    val address: String?,
    val city: String?,
    val province: String?,
    val postalCode: String?,
    val country: String?,
    val status: String,
    val availabilityStatus: Boolean,
    val isEmailVerified: Boolean = false,
    val createdAt: Long,
    // Join detail: current vehicle assignment (null if not assigned)
    val currentAssignment: AssignmentResponse?,
    // Vehicle info from current assignment (null if not assigned)
    val vehicleType: String? = null,
    val vehiclePlate: String? = null,
) {
    companion object {
        fun fromDomain(
            d: Driver,
            currentAssignment: VehicleDriverAssignment? = null,
            vehicleType: String? = null,
            vehiclePlate: String? = null,
            isEmailVerified: Boolean = false,
        ) = DriverResponse(
            id = d.id.value,
            userId = d.userId?.toString(),
            firstName = d.firstName,
            lastName = d.lastName,
            email = d.email,
            phone = d.phone,
            licenseNumber = d.licenseNumber,
            licenseExpiryMs = d.licenseExpiry?.toEpochMilli(),
            licenseClass = d.licenseClass,
            address = d.address,
            city = d.city,
            province = d.province,
            postalCode = d.postalCode,
            country = d.country,
            status = d.status.name,
            availabilityStatus = d.availabilityStatus,
            isEmailVerified = isEmailVerified,
            createdAt = d.createdAt.toEpochMilli(),
            currentAssignment = currentAssignment?.let { AssignmentResponse.fromDomain(it) },
            vehicleType = vehicleType,
            vehiclePlate = vehiclePlate,
        )
    }
}

@Serializable
data class UpdateDriverRequest(
    val email: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    val phone: String? = null,
    val licenseNumber: String? = null,
    val licenseExpiry: String? = null, // YYYY-MM-DD
    val licenseClass: String? = null,
    val address: String? = null,
    val city: String? = null,
    val province: String? = null,
    val postalCode: String? = null,
    val country: String? = null,
    val availabilityStatus: Boolean? = null,
) {
    init {
        email?.let { ValidationUtils.validateEmail(it) }
        firstName?.let { ValidationUtils.validateName(it, "First name") }
        lastName?.let { ValidationUtils.validateName(it, "Last name") }
        phone?.let { ValidationUtils.validatePhone(it) }
    }
}
