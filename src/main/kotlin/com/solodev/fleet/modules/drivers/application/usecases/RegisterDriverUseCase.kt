package com.solodev.fleet.modules.drivers.application.usecases

import com.solodev.fleet.modules.drivers.application.dto.DriverRegistrationRequest
import com.solodev.fleet.modules.drivers.domain.model.Driver
import com.solodev.fleet.modules.drivers.domain.model.DriverId
import com.solodev.fleet.modules.drivers.domain.repository.DriverRepository
import com.solodev.fleet.modules.users.domain.model.TokenType
import com.solodev.fleet.modules.users.domain.model.User
import com.solodev.fleet.modules.users.domain.model.UserId
import com.solodev.fleet.modules.users.domain.model.VerificationToken
import com.solodev.fleet.modules.users.domain.repository.UserRepository
import com.solodev.fleet.modules.users.domain.repository.VerificationTokenRepository
import com.solodev.fleet.shared.utils.PasswordHasher
import com.solodev.fleet.shared.utils.RsaDecryptor
import org.slf4j.LoggerFactory
import java.security.PrivateKey
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Registers a new driver:
 * 1. Validates uniqueness of email and license number.
 * 2. Creates a user account with the DRIVER role.
 * 3. Creates the driver record linked to that user.
 * 4. Emits an email verification token.
 */
class RegisterDriverUseCase(
    private val driverRepository: DriverRepository,
    private val userRepository: UserRepository,
    private val tokenRepository: VerificationTokenRepository,
    private val emailService: com.solodev.fleet.shared.infrastructure.email.EmailService,
    private val privateKey: PrivateKey? = null,
) {
    private val logger = LoggerFactory.getLogger(RegisterDriverUseCase::class.java)

    suspend fun execute(request: DriverRegistrationRequest): Driver {
        val email =
            if (request.isEncrypted && privateKey != null) {
                RsaDecryptor.decrypt(request.email, privateKey)
            } else {
                request.email
            }

        require(driverRepository.findByEmail(email) == null) {
            "Driver with email $email already exists"
        }
        request.licenseNumber?.let { ln ->
            require(driverRepository.findByLicenseNumber(ln) == null) {
                "Driver with license number $ln already exists"
            }
        }
        require(userRepository.findByEmail(email) == null) {
            "An account with email $email already exists"
        }

        val licenseExpiry =
            request.licenseExpiry?.let {
                try {
                    LocalDate.parse(it).atStartOfDay().toInstant(ZoneOffset.UTC)
                } catch (e: Exception) {
                    throw IllegalArgumentException("Invalid license expiry date format. Expected YYYY-MM-DD")
                }
            }
        require(licenseExpiry == null || licenseExpiry.isAfter(Instant.now())) { "Driver license is expired" }

        val driverRole =
            userRepository.findRoleByName("DRIVER")
                ?: throw IllegalStateException("DRIVER role not found in database")

        val password =
            if (request.isEncrypted && privateKey != null) {
                RsaDecryptor.decrypt(request.passwordRaw, privateKey)
            } else {
                request.passwordRaw
            }

        val userId = UserId(UUID.randomUUID().toString())
        val user =
            User(
                id = userId,
                email = email,
                passwordHash = PasswordHasher.hash(password),
                firstName = request.firstName,
                lastName = request.lastName,
                phone = request.phone,
                isVerified = false,
                roles = listOf(driverRole),
            )
        val savedUser = userRepository.save(user)

        val token = UUID.randomUUID().toString()
        tokenRepository.save(
            VerificationToken(
                userId = savedUser.id,
                token = token,
                type = TokenType.EMAIL_VERIFICATION,
                expiresAt = Instant.now().plus(24, ChronoUnit.HOURS),
            ),
        )
        logger.info("[AUTH] Verification token generated for {}: {}", savedUser.email, token)
        // Send real verification email via Nuntly
        emailService.sendVerificationEmail(savedUser.email, token)

        val driver =
            Driver(
                id = DriverId(UUID.randomUUID().toString()),
                userId = UUID.fromString(savedUser.id.value),
                firstName = request.firstName,
                lastName = request.lastName,
                email = email,
                phone = request.phone,
                licenseNumber = request.licenseNumber,
                licenseExpiry = licenseExpiry,
                licenseClass = request.licenseClass,
                address = request.address,
                city = request.city,
                province = request.province,
                postalCode = request.postalCode,
                country = request.country,
            )
        return driverRepository.save(driver)
    }
}
