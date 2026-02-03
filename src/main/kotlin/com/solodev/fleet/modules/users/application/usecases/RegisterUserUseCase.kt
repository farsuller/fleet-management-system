package com.solodev.fleet.modules.users.application.usecases

import com.solodev.fleet.modules.domain.models.*
import com.solodev.fleet.modules.domain.ports.UserRepository
import com.solodev.fleet.modules.users.application.dto.UserRegistrationRequest
import java.util.*

class RegisterUserUseCase(private val repository: UserRepository) {
    suspend fun execute(request: UserRegistrationRequest): User {
        // In a real app, hash the password here
        val user =
                User(
                        id = UserId(UUID.randomUUID().toString()),
                        email = request.email,
                        passwordHash = "hashed_${request.passwordRaw}",
                        firstName = request.firstName,
                        lastName = request.lastName,
                        phone = request.phone
                )
        return repository.save(user)
    }
}
