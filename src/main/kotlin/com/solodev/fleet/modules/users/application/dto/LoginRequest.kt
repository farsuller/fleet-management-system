package com.solodev.fleet.modules.users.application.dto

import com.solodev.fleet.shared.utils.ValidationUtils
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(
    val email: String,
    @SerialName("password") val passwordRaw: String,
    val isEncrypted: Boolean = false,
) {
    init {
        if (!isEncrypted) {
            ValidationUtils.validateEmail(email)
            require(passwordRaw.isNotBlank()) { "Password cannot be blank" }
        }
    }
}
