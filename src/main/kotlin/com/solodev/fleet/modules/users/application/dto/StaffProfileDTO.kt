package com.solodev.fleet.modules.users.application.dto

import com.solodev.fleet.modules.users.domain.model.StaffProfile
import kotlinx.serialization.Serializable

@Serializable
data class StaffProfileDTO(
    val id: String,
    val employeeId: String,
    val department: String?,
    val position: String?,
    val hireDate: String // ISO-8601
) {
    init {
        require(employeeId.isNotBlank()) { "Employee ID cannot be blank" }
    }
    companion object {
        fun fromDomain(p: StaffProfile) = StaffProfileDTO(
            id = p.id.toString(),
            employeeId = p.employeeId,
            department = p.department,
            position = p.position,
            hireDate = p.hireDate.toString()
        )
    }
}