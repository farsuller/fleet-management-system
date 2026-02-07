package com.solodev.fleet.modules.users.application.usecases

import com.solodev.fleet.modules.domain.models.User
import com.solodev.fleet.modules.domain.ports.UserRepository

class ListUsersUseCase(private val repository: UserRepository) {
    suspend fun execute(): List<User> = repository.findAll()
}