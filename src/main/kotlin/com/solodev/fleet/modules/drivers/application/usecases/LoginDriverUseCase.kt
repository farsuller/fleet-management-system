package com.solodev.fleet.modules.drivers.application.usecases

import com.solodev.fleet.modules.drivers.application.dto.DriverLoginResponse
import com.solodev.fleet.modules.drivers.domain.repository.DriverRepository
import com.solodev.fleet.modules.users.domain.repository.UserRepository
import com.solodev.fleet.shared.utils.JwtService
import com.solodev.fleet.shared.utils.PasswordHasher

class LoginDriverUseCase(
    private val userRepository: UserRepository,
    private val driverRepository: DriverRepository,
    private val jwtService: JwtService,
) {
    suspend fun execute(
        email: String,
        password: String,
    ): DriverLoginResponse {
        val user =
            userRepository.findByEmail(email)
                ?: throw IllegalArgumentException("Invalid email or password")

        val hasDriverRole = user.roles.any { it.name == "DRIVER" }
        if (!hasDriverRole) {
            throw IllegalArgumentException("Invalid email or password")
        }

        if (!user.isVerified) {
            throw IllegalStateException("Email not verified. Please check your inbox.")
        }

        if (!PasswordHasher.verify(password, user.passwordHash)) {
            throw IllegalArgumentException("Invalid email or password")
        }

        val driver =
            driverRepository.findByEmail(email)
                ?: throw IllegalStateException("Driver profile not found for this account")

        if (!driver.availabilityStatus) {
            throw IllegalStateException("Your driver account has been deactivated. Contact support.")
        }

        when (driver.status) {
            com.solodev.fleet.modules.drivers.domain.model.DriverStatus.PENDING ->
                throw IllegalStateException("ACCOUNT_PENDING_APPROVAL")
            com.solodev.fleet.modules.drivers.domain.model.DriverStatus.REJECTED ->
                throw IllegalStateException("ACCOUNT_REGISTRATION_REJECTED")
            else -> Unit // Approved
        }

        val roles = user.roles.map { it.name }
        val accessToken =
            jwtService.generateToken(
                id = user.id.value,
                email = user.email,
                roles = roles,
            )
        val refreshToken =
            jwtService.generateRefreshToken(
                id = user.id.value,
                email = user.email,
                roles = roles,
            )

        return DriverLoginResponse(
            accessToken = accessToken,
            refreshToken = refreshToken,
            driverId = driver.id.value,
        )
    }
}
