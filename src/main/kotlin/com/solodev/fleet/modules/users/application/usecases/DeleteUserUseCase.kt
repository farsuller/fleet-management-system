package com.solodev.fleet.modules.users.application.usecases

import com.solodev.fleet.modules.domain.models.UserId
import com.solodev.fleet.modules.domain.ports.UserRepository

class DeleteUserUseCase(private val repository: UserRepository) {
    suspend fun execute(userId: String): Boolean = repository.deleteById(UserId(userId))
}