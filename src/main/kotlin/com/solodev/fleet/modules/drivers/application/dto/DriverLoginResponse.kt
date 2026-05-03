package com.solodev.fleet.modules.drivers.application.dto

import com.solodev.fleet.shared.utils.ValidationUtils
import kotlinx.serialization.Serializable

@Serializable
data class DriverLoginResponse(
    val accessToken: String,
    val refreshToken: String,
    val driverId: String,
)

@Serializable
data class DriverLoginRequest(
    val email: String,
    val password: String,
) {
    init {
        ValidationUtils.validateEmail(email)
        require(password.isNotBlank()) { "Password cannot be blank" }
    }
}

@Serializable
data class RefreshTokenRequest(
    val refreshToken: String,
) {
    init {
        require(refreshToken.isNotBlank()) { "Refresh token cannot be blank" }
    }
}
