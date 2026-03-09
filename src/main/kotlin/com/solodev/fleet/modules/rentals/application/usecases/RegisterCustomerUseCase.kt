package com.solodev.fleet.modules.rentals.application.usecases

import com.solodev.fleet.modules.rentals.application.dto.CustomerRegistrationRequest
import com.solodev.fleet.modules.rentals.domain.model.Customer
import com.solodev.fleet.modules.rentals.domain.model.CustomerId
import com.solodev.fleet.modules.rentals.domain.repository.CustomerRepository
import com.solodev.fleet.modules.users.domain.model.User
import com.solodev.fleet.modules.users.domain.model.UserId
import com.solodev.fleet.modules.users.domain.model.VerificationToken
import com.solodev.fleet.modules.users.domain.model.TokenType
import com.solodev.fleet.modules.users.domain.repository.UserRepository
import com.solodev.fleet.modules.users.domain.repository.VerificationTokenRepository
import com.solodev.fleet.shared.utils.PasswordHasher
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Public endpoint for customer self-registration:
 * 1. Validates uniqueness across both users and customers tables.
 * 2. Creates a user account with CUSTOMER role.
 * 3. Creates the customer record linked to that user.
 * 4. Issues an email verification token.
 */
class RegisterCustomerUseCase(
    private val customerRepository: CustomerRepository,
    private val userRepository: UserRepository,
    private val tokenRepository: VerificationTokenRepository,
) {
    suspend fun execute(request: CustomerRegistrationRequest): Customer {
        require(customerRepository.findByEmail(request.email) == null) {
            "Customer with email ${request.email} already exists"
        }
        require(customerRepository.findByDriverLicense(request.driversLicense) == null) {
            "Customer with license ${request.driversLicense} already exists"
        }
        require(userRepository.findByEmail(request.email) == null) {
            "An account with email ${request.email} already exists"
        }

        val licenseExpiry = try {
            LocalDate.parse(request.driverLicenseExpiry).atStartOfDay().toInstant(ZoneOffset.UTC)
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid license expiry date format. Expected YYYY-MM-DD")
        }
        require(licenseExpiry.isAfter(Instant.now())) { "Driver license is expired" }

        val customerRole = userRepository.findRoleByName("CUSTOMER")
            ?: throw IllegalStateException("CUSTOMER role not found in database — check V022 migration")

        val userId = UserId(UUID.randomUUID().toString())
        val user = User(
            id           = userId,
            email        = request.email,
            passwordHash = PasswordHasher.hash(request.passwordRaw),
            firstName    = request.firstName,
            lastName     = request.lastName,
            phone        = request.phone,
            isVerified   = false,
            roles        = listOf(customerRole),
        )
        val savedUser = userRepository.save(user)

        val token = UUID.randomUUID().toString()
        tokenRepository.save(
            VerificationToken(
                userId    = savedUser.id,
                token     = token,
                type      = TokenType.EMAIL_VERIFICATION,
                expiresAt = Instant.now().plus(24, ChronoUnit.HOURS),
            )
        )
        println("----------------------------------------------------------------")
        println("CUSTOMER VERIFICATION LINK: http://localhost:8080/v1/auth/verify?token=$token")
        println("----------------------------------------------------------------")

        val customer = Customer(
            id                  = CustomerId(UUID.randomUUID().toString()),
            userId              = UUID.fromString(savedUser.id.value),
            firstName           = request.firstName,
            lastName            = request.lastName,
            email               = request.email,
            phone               = request.phone,
            driverLicenseNumber = request.driversLicense,
            driverLicenseExpiry = licenseExpiry,
            address             = request.address,
            city                = request.city,
            state               = request.state,
            postalCode          = request.postalCode,
            country             = request.country,
        )
        return customerRepository.save(customer)
    }
}
