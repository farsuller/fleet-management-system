package com.solodev.fleet.modules.users.application.dto

import com.solodev.fleet.shared.utils.ValidationUtils
import kotlinx.serialization.Serializable

@Serializable
data class UserRegistrationRequest(
    val email: String,
    val passwordRaw: String,
    val firstName: String? = null,
    val lastName: String? = null,
    val phone: String? = null,
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
