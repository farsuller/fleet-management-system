package com.solodev.fleet.modules.users.application.dto

import kotlinx.serialization.Serializable

@Serializable
data class UserRegistrationRequest(
        val email: String,
        val passwordRaw: String,
        val firstName: String,
        val lastName: String,
        val phone: String? = null
)
