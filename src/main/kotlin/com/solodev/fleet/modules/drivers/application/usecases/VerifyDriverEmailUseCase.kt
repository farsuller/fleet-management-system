package com.solodev.fleet.modules.drivers.application.usecases

import com.solodev.fleet.modules.drivers.domain.model.DriverId
import com.solodev.fleet.modules.drivers.domain.repository.DriverRepository
import com.solodev.fleet.modules.users.domain.model.UserId
import com.solodev.fleet.modules.users.domain.repository.UserRepository
import com.solodev.fleet.shared.exceptions.NotFoundException

class VerifyDriverEmailUseCase(
    private val driverRepository: DriverRepository,
    private val userRepository: UserRepository,
) {
    suspend fun execute(driverId: String) {
        val driver =
            driverRepository.findById(DriverId(driverId))
                ?: throw NotFoundException("Driver not found: $driverId")

        val userId = driver.userId ?: throw IllegalStateException("Driver has no linked user account")

        val user =
            userRepository.findById(UserId(userId.toString()))
                ?: throw NotFoundException("Linked user not found: $userId")

        val verifiedUser = user.copy(isVerified = true)
        userRepository.save(verifiedUser)
    }
}
