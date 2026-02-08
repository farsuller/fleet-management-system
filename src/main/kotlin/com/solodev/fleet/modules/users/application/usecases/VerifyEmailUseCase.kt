package com.solodev.fleet.modules.users.application.usecases

import com.solodev.fleet.modules.users.domain.model.TokenType
import com.solodev.fleet.modules.users.domain.repository.UserRepository
import com.solodev.fleet.modules.users.domain.repository.VerificationTokenRepository
import java.time.Instant

class VerifyEmailUseCase(
        private val userRepository: UserRepository,
        private val tokenRepository: VerificationTokenRepository
) {
    suspend fun execute(token: String) {
        val verificationToken =
                tokenRepository.findByToken(token, TokenType.EMAIL_VERIFICATION)
                        ?: throw IllegalArgumentException(
                                "Invalid or non-existent verification token"
                        )

        if (verificationToken.expiresAt.isBefore(Instant.now())) {
            throw IllegalArgumentException("Verification token has expired")
        }

        // Find user by ID from the token
        val user =
                userRepository.findById(verificationToken.userId)
                        ?: throw IllegalStateException("User associated with token not found")

        // Update user status
        val verifiedUser = user.copy(isVerified = true)
        userRepository.save(verifiedUser)

        // Clean up token
        tokenRepository.deleteByToken(token)
    }
}
