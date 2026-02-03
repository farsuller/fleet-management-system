package com.solodev.fleet.modules.users.application.usecases

import com.solodev.fleet.modules.domain.models.*
import com.solodev.fleet.modules.domain.ports.UserRepository

class GetUserProfileUseCase(private val repository: UserRepository) {
    suspend fun execute(userId: String): User? {
        return repository.findById(UserId(userId))
    }
}
