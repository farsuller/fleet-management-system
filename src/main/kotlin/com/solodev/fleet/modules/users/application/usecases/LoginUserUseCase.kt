package com.solodev.fleet.modules.users.application.usecases

import com.solodev.fleet.modules.domain.models.User
import com.solodev.fleet.modules.domain.ports.UserRepository
import com.solodev.fleet.modules.users.application.dto.LoginRequest

class LoginUserUseCase(private val repository: UserRepository) {
    suspend fun execute(request: LoginRequest): User {
        val user = repository.findByEmail(request.email)
            ?: throw IllegalArgumentException("Invalid email or password")

        // In production, use BCrypt.checkpw(request.passwordRaw, user.passwordHash)
        val isValid = user.passwordHash == "hashed_${request.passwordRaw}"

        if (!isValid) {
            throw IllegalArgumentException("Invalid email or password")
        }

        return user
    }
}