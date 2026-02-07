package com.solodev.fleet.modules.users.application.dto

import com.solodev.fleet.modules.domain.models.Role
import kotlinx.serialization.Serializable


@Serializable
data class RoleResponse(
    val id: String,
    val name: String,
    val description: String? = null
) {
    companion object {
        fun fromDomain(role: Role) = RoleResponse(
            id = role.id.value,
            name = role.name,
            description = role.description
        )
    }
}