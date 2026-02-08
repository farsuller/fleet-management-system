package com.solodev.fleet.modules.users.application.usecases

import com.solodev.fleet.modules.users.domain.model.User
import com.solodev.fleet.modules.users.domain.repository.UserRepository

class ListUsersUseCase(private val repository: UserRepository) {
    suspend fun execute(): List<User> = repository.findAll()
}