package com.solodev.fleet.modules.users.application.dto

import com.solodev.fleet.modules.users.domain.model.User
import kotlinx.serialization.Serializable

@Serializable
data class UserResponse(
        val id: String,
        val email: String,
        val firstName: String,
        val lastName: String,
        val fullName: String,
        val phone: String?,
        val isActive: Boolean,
        val isVerified: Boolean,
        val roles: List<String>,
        val staffProfile: StaffProfileDTO? = null
) {
        companion object {
                fun fromDomain(u: User) =
                        UserResponse(
                                id = u.id.value,
                                email = u.email,
                                firstName = u.firstName,
                                lastName = u.lastName,
                                fullName = u.fullName,
                                phone = u.phone,
                                isActive = u.isActive,
                                isVerified = u.isVerified,
                                roles = u.roles.map { it.name },
                                staffProfile =
                                        u.staffProfile?.let { StaffProfileDTO.fromDomain(it) }
                        )
        }
}
