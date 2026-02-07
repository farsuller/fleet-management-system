package com.solodev.fleet.modules.rentals.application.usecases

import com.solodev.fleet.modules.domain.models.Customer
import com.solodev.fleet.modules.domain.models.CustomerId
import com.solodev.fleet.modules.domain.ports.CustomerRepository
import com.solodev.fleet.modules.rentals.application.dto.CustomerRequest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

/**
 * Creates a new customer in the system.
 *
 * Business Rules:
 * - Email must be unique
 * - Driver's license must be unique
 * - Driver's license must not be expired
 */
class CreateCustomerUseCase(private val customerRepository: CustomerRepository) {
    suspend fun execute(request: CustomerRequest): Customer {
        // Check if email already exists
        val existingByEmail = customerRepository.findByEmail(request.email)
        require(existingByEmail == null) { "Customer with email ${request.email} already exists" }

        // Check if driver's license already exists
        val existingByLicense = customerRepository.findByDriverLicense(request.driversLicense)
        require(existingByLicense == null) {
            "Customer with driver's license ${request.driversLicense} already exists"
        }

        // Parse and validate license expiry (assume format YYYY-MM-DD)
        val licenseExpiry =
                try {
                    LocalDate.parse(request.driverLicenseExpiry)
                            .atStartOfDay()
                            .toInstant(ZoneOffset.UTC)
                } catch (e: Exception) {
                    throw IllegalArgumentException(
                            "Invalid driver license expiry date format. Expected YYYY-MM-DD"
                    )
                }

        // Validate license is not expired
        require(licenseExpiry.isAfter(Instant.now())) {
            "Driver's license is expired (expiry: ${request.driverLicenseExpiry})"
        }

        val customer =
                Customer(
                        id = CustomerId(UUID.randomUUID().toString()),
                        userId = null, // No user account by default
                        firstName = request.firstName,
                        lastName = request.lastName,
                        email = request.email,
                        phone = request.phone,
                        driverLicenseNumber = request.driversLicense,
                        driverLicenseExpiry = licenseExpiry,
                        address = request.address,
                        city = request.city,
                        state = request.state,
                        postalCode = request.postalCode,
                        country = request.country,
                        isActive = true
                )

        return customerRepository.save(customer)
    }
}
