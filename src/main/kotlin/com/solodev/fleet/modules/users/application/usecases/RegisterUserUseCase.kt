package com.solodev.fleet.modules.users.application.usecases

import com.solodev.fleet.modules.domain.models.*
import com.solodev.fleet.modules.domain.ports.UserRepository
import com.solodev.fleet.modules.users.application.dto.UserRegistrationRequest
import java.util.*

class RegisterUserUseCase(private val repository: UserRepository) {
    suspend fun execute(request: UserRegistrationRequest): User {
        // Business Rule: Email must be unique
        repository.findByEmail(request.email)?.let {
            throw IllegalStateException("User with email ${request.email} already exists")
        }

        val user = User(
            id = UserId(UUID.randomUUID().toString()),
            email = request.email,
            passwordHash = "hashed_${request.passwordRaw}", // Use actual hashing in dev/prod
            firstName = request.firstName,
            lastName = request.lastName,
            phone = request.phone
        )
        return repository.save(user)
    }
}
