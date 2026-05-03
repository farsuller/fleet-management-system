package com.solodev.fleet.modules.users.application.dto

import com.solodev.fleet.shared.utils.ValidationUtils
import kotlinx.serialization.Serializable

@Serializable
data class UserUpdateRequest(
    val firstName: String? = null,
    val lastName: String? = null,
    val phone: String? = null,
    val isActive: Boolean? = null,
    val staffProfile: StaffProfileUpdateRequest? = null,
) {
    init {
        firstName?.let { ValidationUtils.validateName(it, "First name") }
        lastName?.let { ValidationUtils.validateName(it, "Last name") }
        phone?.let { ValidationUtils.validatePhone(it) }
    }
}
