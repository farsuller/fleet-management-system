package com.solodev.fleet.modules.users.application.usecases

import com.solodev.fleet.modules.domain.models.User
import com.solodev.fleet.modules.domain.models.UserId
import com.solodev.fleet.modules.domain.ports.UserRepository

class AssignRoleUseCase(private val repository: UserRepository) {
    suspend fun execute(userId: String, roleName: String): User? {
        val user = repository.findById(UserId(userId)) ?: return null
        val role = repository.findRoleByName(roleName) ?: throw IllegalArgumentException("Role $roleName not found")

        if (user.roles.any { it.name == roleName }) return user

        val updatedUser = user.copy(roles = user.roles + role)
        return repository.save(updatedUser)
    }
}