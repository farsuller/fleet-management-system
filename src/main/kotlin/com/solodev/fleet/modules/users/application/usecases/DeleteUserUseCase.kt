package com.solodev.fleet.modules.users.application.usecases

import com.solodev.fleet.modules.users.domain.model.UserId
import com.solodev.fleet.modules.users.domain.repository.UserRepository

class DeleteUserUseCase(private val repository: UserRepository) {
    suspend fun execute(userId: String): Boolean = repository.deleteById(UserId(userId))
}