package com.solodev.fleet.modules.users.application.usecases

import com.solodev.fleet.modules.users.domain.model.Role
import com.solodev.fleet.modules.users.domain.repository.UserRepository

class ListRolesUseCase(private val repository: UserRepository) {
    suspend fun execute(): List<Role> = repository.findAllRoles()
}