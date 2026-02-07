package com.solodev.fleet.modules.users.application.usecases

import com.solodev.fleet.modules.domain.models.*
import com.solodev.fleet.modules.domain.ports.UserRepository
import com.solodev.fleet.modules.users.application.dto.UserUpdateRequest
import java.util.UUID

class UpdateUserUseCase(private val repository: UserRepository) {
    suspend fun execute(userId: String, request: UserUpdateRequest): User? {
        val existing = repository.findById(UserId(userId)) ?: return null

        var updatedStaffProfile = existing.staffProfile

        // Handle nested Staff Profile update
        if (request.staffProfile != null) {
            val profile = updatedStaffProfile ?: StaffProfile(
                id = UUID.randomUUID(),
                userId = UserId(userId),
                employeeId = request.staffProfile.employeeId ?: "EMP-TEMP", // Placeholder if new
                hireDate = java.time.LocalDate.now()
            )

            updatedStaffProfile = profile.copy(
                department = request.staffProfile.department ?: profile.department,
                position = request.staffProfile.position ?: profile.position,
                employeeId = request.staffProfile.employeeId ?: profile.employeeId
            )
        }

        val updated = existing.copy(
            firstName = request.firstName ?: existing.firstName,
            lastName = request.lastName ?: existing.lastName,
            phone = request.phone ?: existing.phone,
            isActive = request.isActive ?: existing.isActive,
            staffProfile = updatedStaffProfile
        )
        return repository.save(updated)
    }
}