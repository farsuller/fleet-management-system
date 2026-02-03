package com.solodev.fleet.modules.users.application.dto

import com.solodev.fleet.modules.domain.models.User
import kotlinx.serialization.Serializable

@Serializable
data class UserResponse(
        val id: String,
        val email: String,
        val fullName: String,
        val isActive: Boolean,
        val roles: List<String>
) {
        companion object {
                fun fromDomain(u: User) =
                        UserResponse(
                                id = u.id.value,
                                email = u.email,
                                fullName = u.fullName,
                                isActive = u.isActive,
                                roles = u.roles.map { it.name }
                        )
        }
}
