package com.solodev.fleet.modules.drivers.application.usecases

import com.solodev.fleet.modules.drivers.application.dto.DriverRequest
import com.solodev.fleet.modules.drivers.application.dto.UpdateDriverRequest
import com.solodev.fleet.modules.drivers.domain.model.Driver
import com.solodev.fleet.modules.drivers.domain.model.DriverId
import com.solodev.fleet.modules.drivers.domain.model.DriverShift
import com.solodev.fleet.modules.drivers.domain.repository.DriverRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

class CreateDriverUseCase(private val driverRepository: DriverRepository) {
    suspend fun execute(request: DriverRequest): Driver {
        require(driverRepository.findByEmail(request.email) == null) {
            "Driver with email ${request.email} already exists"
        }
        require(driverRepository.findByLicenseNumber(request.licenseNumber) == null) {
            "Driver with license ${request.licenseNumber} already exists"
        }
        val licenseExpiry = try {
            LocalDate.parse(request.licenseExpiry).atStartOfDay().toInstant(ZoneOffset.UTC)
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid license expiry date format. Expected YYYY-MM-DD")
        }
        require(licenseExpiry.isAfter(Instant.now())) { "Driver license is expired" }

        return driverRepository.save(
            Driver(
                id            = DriverId(UUID.randomUUID().toString()),
                firstName     = request.firstName,
                lastName      = request.lastName,
                email         = request.email,
                phone         = request.phone,
                licenseNumber = request.licenseNumber,
                licenseExpiry = licenseExpiry,
                licenseClass  = request.licenseClass,
                address       = request.address,
                city          = request.city,
                state         = request.state,
                postalCode    = request.postalCode,
                country       = request.country,
            )
        )
    }
}

class GetDriverUseCase(private val driverRepository: DriverRepository) {
    suspend fun execute(id: String) = driverRepository.findById(DriverId(id))
}

class ListDriversUseCase(private val driverRepository: DriverRepository) {
    suspend fun execute() = driverRepository.findAll()
}

class DeactivateDriverUseCase(private val driverRepository: DriverRepository) {
    suspend fun execute(id: String): Driver? {
        val driver = driverRepository.findById(DriverId(id)) ?: return null
        return driverRepository.save(driver.copy(isActive = !driver.isActive))
    }
}

class StartShiftUseCase(private val driverRepository: DriverRepository) {
    suspend fun execute(driverId: String, vehicleId: String, notes: String?): DriverShift {
        // Validation: Verify driver exists
        driverRepository.findById(DriverId(driverId)) ?: throw IllegalArgumentException("Driver not found: $driverId")
        
        // Validation: Check if there's already an active shift
        val activeShift = driverRepository.findActiveShift(driverId)
        if (activeShift != null) {
            throw IllegalStateException("Driver already has an active shift (id=${activeShift.id})")
        }

        return driverRepository.startShift(driverId, vehicleId, notes)
    }
}

class EndShiftUseCase(private val driverRepository: DriverRepository) {
    suspend fun execute(driverId: String, notes: String?): DriverShift {
        return driverRepository.endShift(driverId, notes) 
            ?: throw IllegalStateException("No active shift found for driver: $driverId")
    }
}

class GetActiveShiftUseCase(private val driverRepository: DriverRepository) {
    suspend fun execute(driverId: String): DriverShift? {
        return driverRepository.findActiveShift(driverId)
    }
}

class UpdateDriverUseCase(private val driverRepository: DriverRepository) {
    suspend fun execute(id: String, request: UpdateDriverRequest): Driver {
        val existing = driverRepository.findById(DriverId(id))
            ?: throw IllegalArgumentException("Driver not found: $id")

        if (request.email != null && request.email != existing.email) {
            require(driverRepository.findByEmail(request.email) == null) {
                "Driver with email ${request.email} already exists"
            }
        }
        if (request.licenseNumber != null && request.licenseNumber != existing.licenseNumber) {
            require(driverRepository.findByLicenseNumber(request.licenseNumber) == null) {
                "Driver with license number ${request.licenseNumber} already exists"
            }
        }

        val licenseExpiry = request.licenseExpiry?.let {
            try {
                LocalDate.parse(it).atStartOfDay().toInstant(ZoneOffset.UTC)
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid license expiry date format. Expected YYYY-MM-DD")
            }
        }

        val updated = existing.copy(
            firstName = request.firstName ?: existing.firstName,
            lastName = request.lastName ?: existing.lastName,
            email = request.email ?: existing.email,
            phone = request.phone ?: existing.phone,
            licenseNumber = request.licenseNumber ?: existing.licenseNumber,
            licenseExpiry = licenseExpiry ?: existing.licenseExpiry,
            licenseClass = request.licenseClass ?: existing.licenseClass,
            address = request.address ?: existing.address,
            city = request.city ?: existing.city,
            state = request.state ?: existing.state,
            postalCode = request.postalCode ?: existing.postalCode,
            country = request.country ?: existing.country,
            isActive = request.isActive ?: existing.isActive
        )

        return driverRepository.save(updated)
    }
}
