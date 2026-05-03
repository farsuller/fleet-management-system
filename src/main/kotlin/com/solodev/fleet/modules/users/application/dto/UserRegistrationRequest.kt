package com.solodev.fleet.modules.users.application.dto

import com.solodev.fleet.shared.utils.ValidationUtils
import kotlinx.serialization.Serializable

@Serializable
data class UserRegistrationRequest(
    val email: String,
    val passwordRaw: String,
    val firstName: String,
    val lastName: String,
    val phone: String? = null,
) {
    init {
        ValidationUtils.validateEmail(email)
        ValidationUtils.validatePassword(passwordRaw)
        ValidationUtils.validateName(firstName, "First name")
        ValidationUtils.validateName(lastName, "Last name")
        ValidationUtils.validatePhone(phone)
    }
}
