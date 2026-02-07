package com.solodev.fleet.modules.users.application.dto

import kotlinx.serialization.Serializable


@Serializable
data class StaffProfileUpdateRequest(
    val department: String? = null,
    val position: String? = null,
    val employeeId: String? = null // For administrative updates
)