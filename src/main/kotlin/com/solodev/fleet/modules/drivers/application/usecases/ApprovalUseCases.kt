package com.solodev.fleet.modules.drivers.application.usecases

import com.solodev.fleet.modules.drivers.domain.model.Driver
import com.solodev.fleet.modules.drivers.domain.model.DriverId
import com.solodev.fleet.modules.drivers.domain.model.DriverStatus
import com.solodev.fleet.modules.drivers.domain.repository.DriverRepository
import com.solodev.fleet.shared.exceptions.NotFoundException

class ApproveDriverUseCase(
    private val driverRepository: DriverRepository,
) {
    suspend fun execute(id: String): Driver {
        val driver = driverRepository.findById(DriverId(id)) ?: throw NotFoundException("Driver not found: $id")
        return driverRepository.save(driver.copy(status = DriverStatus.APPROVED, availabilityStatus = true))
    }
}

class RejectDriverUseCase(
    private val driverRepository: DriverRepository,
) {
    suspend fun execute(id: String): Driver {
        val driver = driverRepository.findById(DriverId(id)) ?: throw NotFoundException("Driver not found: $id")
        return driverRepository.save(driver.copy(status = DriverStatus.REJECTED, availabilityStatus = false))
    }
}

class ListPendingDriversUseCase(
    private val driverRepository: DriverRepository,
) {
    suspend fun execute(): List<Driver> = driverRepository.findPendingDrivers()
}
