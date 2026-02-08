package com.solodev.fleet.modules.users.application.usecases

import com.solodev.fleet.modules.users.application.dto.LoginRequest
import com.solodev.fleet.modules.users.domain.model.User
import com.solodev.fleet.modules.users.domain.repository.UserRepository
import com.solodev.fleet.shared.utils.JwtService
import com.solodev.fleet.shared.utils.PasswordHasher

class LoginUserUseCase(private val repository: UserRepository, private val jwtService: JwtService) {
    suspend fun execute(request: LoginRequest): Pair<User, String> {
        val user =
                repository.findByEmail(request.email)
                        ?: throw IllegalArgumentException("Invalid email or password")

        if (!user.isVerified) {
            throw IllegalArgumentException("Email not verified. Please check your inbox.")
        }

        if (!PasswordHasher.verify(request.passwordRaw, user.passwordHash)) {
            throw IllegalArgumentException("Invalid email or password")
        }

        val token =
                jwtService.generateToken(
                        id = user.id.value,
                        email = user.email,
                        roles = user.roles.map { it.name }
                )

        return Pair(user, token)
    }
}
