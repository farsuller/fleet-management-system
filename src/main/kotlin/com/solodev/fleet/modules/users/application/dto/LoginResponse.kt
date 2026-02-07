package com.solodev.fleet.modules.users.application.dto

import kotlinx.serialization.Serializable

@Serializable
data class LoginResponse(
    val token: String,
    val user: UserResponse
)