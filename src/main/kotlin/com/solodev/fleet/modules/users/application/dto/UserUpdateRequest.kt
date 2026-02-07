package com.solodev.fleet.modules.users.application.dto

import kotlinx.serialization.Serializable

@Serializable
data class UserUpdateRequest(
    val firstName: String? = null,
    val lastName: String? = null,
    val phone: String? = null,
    val isActive: Boolean? = null,
    val staffProfile: StaffProfileUpdateRequest? = null
) {
    init {
        firstName?.let { require(it.isNotBlank()) { "First name cannot be blank" } }
        lastName?.let { require(it.isNotBlank()) { "Last name cannot be blank" } }
    }
}