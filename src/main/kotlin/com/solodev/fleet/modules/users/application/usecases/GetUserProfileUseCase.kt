package com.solodev.fleet.modules.users.application.usecases

import com.solodev.fleet.modules.users.domain.model.User
import com.solodev.fleet.modules.users.domain.model.UserId
import com.solodev.fleet.modules.users.domain.repository.UserRepository

class GetUserProfileUseCase(private val repository: UserRepository) {
    suspend fun execute(userId: String): User? {
        return repository.findById(UserId(userId))
    }
}
