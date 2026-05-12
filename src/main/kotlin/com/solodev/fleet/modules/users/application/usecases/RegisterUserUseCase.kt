package com.solodev.fleet.modules.users.application.usecases

import com.solodev.fleet.modules.users.application.dto.UserRegistrationRequest
import com.solodev.fleet.modules.users.domain.model.User
import com.solodev.fleet.modules.users.domain.model.UserId
import com.solodev.fleet.modules.users.domain.repository.UserRepository
import com.solodev.fleet.shared.utils.RsaDecryptor
import org.slf4j.LoggerFactory
import java.security.PrivateKey
import java.util.UUID

class RegisterUserUseCase(
    private val repository: UserRepository,
    private val tokenRepository: com.solodev.fleet.modules.users.domain.repository.VerificationTokenRepository,
    private val emailService: com.solodev.fleet.shared.infrastructure.email.EmailService,
    private val privateKey: PrivateKey? = null,
) {
    private val logger = LoggerFactory.getLogger(RegisterUserUseCase::class.java)

    suspend fun execute(request: UserRegistrationRequest): User {
        val email =
            if (request.isEncrypted && privateKey != null) {
                RsaDecryptor.decrypt(request.email, privateKey)
            } else {
                request.email
            }

        // Business Rule: Email must be unique
        repository.findByEmail(email)?.let {
            throw IllegalStateException(
                "User with email $email already exists",
            )
        }

        val customerRole =
            repository.findRoleByName("CUSTOMER_SUPPORT")
                ?: throw IllegalStateException(
                    "Default CUSTOMER_SUPPORT role not found in database",
                )

        val password =
            if (request.isEncrypted && privateKey != null) {
                RsaDecryptor.decrypt(request.passwordRaw, privateKey)
            } else {
                request.passwordRaw
            }

        val user =
            User(
                id = UserId(UUID.randomUUID().toString()),
                email = email,
                passwordHash =
                    com.solodev.fleet.shared.utils.PasswordHasher.hash(
                        password,
                    ),
                firstName = request.firstName,
                lastName = request.lastName,
                phone = request.phone,
                isVerified = false,
                roles = listOf(customerRole),
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
                    java.time.Instant
                        .now()
                        .plus(24, java.time.temporal.ChronoUnit.HOURS),
            )
        tokenRepository.save(verificationToken)
        logger.info("[AUTH] Verification token generated for {}: {}", savedUser.email, token)

        // Send real verification email via Nuntly
        emailService.sendVerificationEmail(savedUser.email, token)

        return savedUser
    }
}
