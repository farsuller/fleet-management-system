package com.solodev.fleet.modules.users.application.usecases

import com.solodev.fleet.modules.users.application.dto.LoginRequest
import com.solodev.fleet.modules.users.domain.model.User
import com.solodev.fleet.modules.users.domain.repository.UserRepository
import com.solodev.fleet.shared.utils.JwtService
import com.solodev.fleet.shared.utils.PasswordHasher
import com.solodev.fleet.shared.utils.RsaDecryptor
import java.security.PrivateKey

class LoginUserUseCase(
    private val repository: UserRepository,
    private val jwtService: JwtService,
    private val privateKey: PrivateKey? = null,
) {
    suspend fun execute(request: LoginRequest): Pair<User, String> {
        val email =
            if (request.isEncrypted && privateKey != null) {
                RsaDecryptor.decrypt(request.email, privateKey)
            } else {
                request.email
            }

        val password =
            if (request.isEncrypted && privateKey != null) {
                RsaDecryptor.decrypt(request.passwordRaw, privateKey)
            } else {
                request.passwordRaw
            }

        val user =
            repository.findByEmail(email)
                ?: throw IllegalArgumentException("Invalid email or password")

        if (!user.isVerified) {
            throw IllegalArgumentException("Email not verified. Please check your inbox.")
        }

        if (!PasswordHasher.verify(password, user.passwordHash)) {
            throw IllegalArgumentException("Invalid email or password")
        }

        val token =
            jwtService.generateToken(
                id = user.id.value,
                email = user.email,
                roles = user.roles.map { it.name },
            )

        return Pair(user, token)
    }
}
