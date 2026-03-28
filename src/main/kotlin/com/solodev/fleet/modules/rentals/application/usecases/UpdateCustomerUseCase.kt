package com.solodev.fleet.modules.rentals.application.usecases

import com.solodev.fleet.modules.rentals.application.dto.UpdateCustomerRequest
import com.solodev.fleet.modules.rentals.domain.model.Customer
import com.solodev.fleet.modules.rentals.domain.model.CustomerId
import com.solodev.fleet.modules.rentals.domain.repository.CustomerRepository
import com.solodev.fleet.shared.helpers.dbQuery
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class UpdateCustomerUseCase(private val customerRepository: CustomerRepository) {
    suspend fun execute(id: String, request: UpdateCustomerRequest): Customer? = dbQuery {
        val customerId = CustomerId(id)
        val existing = customerRepository.findById(customerId) ?: return@dbQuery null

        // Validate uniqueness if email/license changes
        request.email?.let { email ->
            if (email != existing.email) {
                require(customerRepository.findByEmail(email) == null) { "Customer with email $email already exists" }
            }
        }

        request.driversLicense?.let { license ->
            if (license != existing.driverLicenseNumber) {
                require(customerRepository.findByDriverLicense(license) == null) {
                    "Customer with driver's license $license already exists"
                }
            }
        }

        // Parse and validate license expiry if changed
        val licenseExpiry = request.driverLicenseExpiry?.let {
            try {
                LocalDate.parse(it).atStartOfDay().toInstant(ZoneOffset.UTC)
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid driver license expiry date format. Expected YYYY-MM-DD")
            }
        }

        licenseExpiry?.let {
            require(it.isAfter(Instant.now())) { "Driver's license is expired" }
        }

        val updated = existing.copy(
            firstName = request.firstName ?: existing.firstName,
            lastName = request.lastName ?: existing.lastName,
            email = request.email ?: existing.email,
            phone = request.phone ?: existing.phone,
            driverLicenseNumber = request.driversLicense ?: existing.driverLicenseNumber,
            driverLicenseExpiry = licenseExpiry ?: existing.driverLicenseExpiry,
            address = request.address ?: existing.address,
            city = request.city ?: existing.city,
            state = request.state ?: existing.state,
            postalCode = request.postalCode ?: existing.postalCode,
            country = request.country ?: existing.country
        )

        customerRepository.save(updated)
    }
}
