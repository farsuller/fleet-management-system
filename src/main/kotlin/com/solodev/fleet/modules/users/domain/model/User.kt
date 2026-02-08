package com.solodev.fleet.modules.users.domain.model

import java.time.LocalDate
import java.util.*

@JvmInline
value class UserId(val value: String) {
    init {
        require(value.isNotBlank()) { "UserId cannot be blank" }
    }
}

@JvmInline
value class RoleId(val value: String) {
    init {
        require(value.isNotBlank()) { "RoleId cannot be blank" }
    }
}

data class User(
        val id: UserId,
        val email: String,
        val passwordHash: String,
        val firstName: String,
        val lastName: String,
        val phone: String? = null,
        val isActive: Boolean = true,
        val isVerified: Boolean = false,
        val roles: List<Role> = emptyList(),
        val staffProfile: StaffProfile? = null
) {
    val fullName: String
        get() = "$firstName $lastName"
}

data class Role(val id: RoleId, val name: String, val description: String? = null) {
    init {
        require(name.isNotBlank()) { "Role name cannot be blank" }
    }
}

data class StaffProfile(
        val id: UUID,
        val userId: UserId,
        val employeeId: String,
        val department: String? = null,
        val position: String? = null,
        val hireDate: LocalDate
) {
    init {
        require(employeeId.isNotBlank()) { "EmployeeId cannot be blank" }
    }
}
