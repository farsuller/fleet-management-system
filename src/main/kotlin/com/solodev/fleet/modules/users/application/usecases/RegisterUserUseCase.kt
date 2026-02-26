package com.solodev.fleet.modules.users.application.usecases

import com.solodev.fleet.modules.users.application.dto.UserRegistrationRequest
import com.solodev.fleet.modules.users.domain.model.User
import com.solodev.fleet.modules.users.domain.model.UserId
import com.solodev.fleet.modules.users.domain.repository.UserRepository
import java.util.*

class RegisterUserUseCase(
        private val repository: UserRepository,
        private val tokenRepository:
                com.solodev.fleet.modules.users.domain.repository.VerificationTokenRepository
) {
        suspend fun execute(request: UserRegistrationRequest): User {
                // Business Rule: Email must be unique
                repository.findByEmail(request.email)?.let {
                        throw IllegalStateException(
                                "User with email ${request.email} already exists"
                        )
                }

                val customerRole =
                        repository.findRoleByName("CUSTOMER")
                                ?: throw IllegalStateException(
                                        "Default CUSTOMER role not found in database"
                                )

                val user =
                        User(
                                id = UserId(UUID.randomUUID().toString()),
                                email = request.email,
                                passwordHash =
                                        com.solodev.fleet.shared.utils.PasswordHasher.hash(
                                                request.passwordRaw
                                        ),
                                firstName = request.firstName,
                                lastName = request.lastName,
                                phone = request.phone,
                                isVerified = false,
                                roles = listOf(customerRole)
                        )
                val savedUser = repository.save(user)

                // Generate Verification Token
                val token = UUID.randomUUID().toString()
                val verificationToken =
                        com.solodev.fleet.modules.users.domain.model.VerificationToken(
                                userId = savedUser.id,
                                token = token,
                                type =
                                        com.solodev.fleet.modules.users.domain.model.TokenType
                                                .EMAIL_VERIFICATION,
                                expiresAt =
                                        java.time.Instant.now()
                                                .plus(24, java.time.temporal.ChronoUnit.HOURS)
                        )
                tokenRepository.save(verificationToken)

                // Simulate sending email (TODO: Replace with real EmailService)
                println("----------------------------------------------------------------")
                println("VERIFICATION LINK: http://localhost:8080/v1/auth/verify?token=$token")
                println("----------------------------------------------------------------")

                return savedUser
        }
}
