package com.solodev.fleet.modules.users.application.dto

import kotlinx.serialization.Serializable

@Serializable
data class UserRegistrationRequest(
        val email: String,
        val passwordRaw: String,
        val firstName: String,
        val lastName: String,
        val phone: String? = null
) {
        init {
                require(email.isNotBlank() && email.contains("@")) { "Valid email required" }
                require(passwordRaw.length >= 8) { "Password must be at least 8 characters" }
                require(firstName.isNotBlank()) { "First name cannot be blank" }
                require(lastName.isNotBlank()) { "Last name cannot be blank" }
        }
}