package com.solodev.fleet.modules.users.application.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(
    val email: String,
    @SerialName("password") val passwordRaw: String,
) {
    init {
        require(email.isNotBlank() && email.contains("@")) { "Valid email required" }
        require(passwordRaw.isNotBlank()) { "Password cannot be blank" }
    }
}