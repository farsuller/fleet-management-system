package com.solodev.fleet.modules.users.application.usecases

import com.solodev.fleet.modules.domain.models.Role
import com.solodev.fleet.modules.domain.ports.UserRepository

class ListRolesUseCase(private val repository: UserRepository) {
    suspend fun execute(): List<Role> = repository.findAllRoles()
}