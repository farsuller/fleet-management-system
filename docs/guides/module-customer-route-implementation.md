# Customer Management Module - Complete Implementation Guide

**Module**: Customer Management  
**Version**: 1.0  
**Created**: 2026-02-07  
**Status**: ✅ Production-Ready  
**Compliance**: Follows IMPLEMENTATION-STANDARDS.md  

---

## Overview

The Customer Management module handles customer profiles and driver information for the Fleet Management System. Customers are **required** before creating rentals, as they represent the individuals who will be renting and driving vehicles.

### Key Concepts

- **Customer**: A person who rents vehicles (the driver)
- **Driver's License**: Required for legal operation of vehicles
- **User Link**: Optional connection to system user account for self-service portal

---

## 1. Directory Structure

```
src/main/kotlin/com/solodev/fleet/modules/
├── domain/
│   ├── models/
│   │   └── Customer.kt                    # Customer domain entity
│   └── ports/
│       └── CustomerRepository.kt          # Repository interface (in RentalRepository.kt)
├── infrastructure/
│   └── persistence/
│       └── CustomerRepositoryImpl.kt      # PostgreSQL implementation
└── rentals/                               # Customer module lives under rentals
    ├── application/
    │   ├── dto/
    │   │   ├── CustomerRequest.kt         # Create customer DTO
    │   │   └── CustomerResponse.kt        # Customer response DTO
    │   └── usecases/
    │       ├── CreateCustomerUseCase.kt   # Create customer
    │       ├── GetCustomerUseCase.kt      # Retrieve customer
    │       └── ListCustomersUseCase.kt    # List all customers
    └── infrastructure/
        └── http/
            └── CustomerRoutes.kt          # HTTP endpoints
```

---

## 2. Domain Model

### Customer.kt
`src/main/kotlin/com/solodev/fleet/modules/domain/models/Customer.kt`

```kotlin
package com.solodev.fleet.modules.domain.models

import java.time.Instant
import java.util.UUID

/** Value object representing a unique customer identifier. */
@JvmInline
value class CustomerId(val value: String) {
    init {
        require(value.isNotBlank()) { "Customer ID cannot be blank" }
    }
}

/** Customer domain entity representing a person who rents vehicles. */
data class Customer(
    val id: CustomerId,
    val userId: UUID? = null,              // Optional link to User account
    val firstName: String,
    val lastName: String,
    val email: String,
    val phone: String,
    val driverLicenseNumber: String,       // Required for vehicle operation
    val driverLicenseExpiry: Instant,      // Must be valid/not expired
    val address: String? = null,
    val city: String? = null,
    val state: String? = null,
    val postalCode: String? = null,
    val country: String? = null,
    val isActive: Boolean = true
) {
    init {
        require(firstName.isNotBlank()) { "First name cannot be blank" }
        require(lastName.isNotBlank()) { "Last name cannot be blank" }
        require(email.isNotBlank()) { "Email cannot be blank" }
        require(phone.isNotBlank()) { "Phone cannot be blank" }
        require(driverLicenseNumber.isNotBlank()) { "Driver license number cannot be blank" }
    }

    val fullName: String
        get() = "$firstName $lastName"
}
```

---

## 3. Data Transfer Objects (DTOs)

### CustomerRequest.kt
```kotlin
package com.solodev.fleet.modules.rentals.application.dto

import kotlinx.serialization.Serializable

@Serializable
data class CustomerRequest(
    val email: String,
    val firstName: String,
    val lastName: String,
    val phone: String,
    val driversLicense: String,
    val driverLicenseExpiry: String,       // ISO date format: YYYY-MM-DD
    val address: String? = null,
    val city: String? = null,
    val state: String? = null,
    val postalCode: String? = null,
    val country: String? = null
) {
    init {
        require(email.isNotBlank() && email.contains("@")) { "Valid email required" }
        require(firstName.isNotBlank()) { "First name cannot be blank" }
        require(lastName.isNotBlank()) { "Last name cannot be blank" }
        require(phone.isNotBlank()) { "Phone cannot be blank" }
        require(driversLicense.isNotBlank()) { "Driver's license cannot be blank" }
        require(driverLicenseExpiry.isNotBlank()) { "Driver's license expiry cannot be blank" }
    }
}
```

### CustomerResponse.kt
```kotlin
package com.solodev.fleet.modules.rentals.application.dto

import com.solodev.fleet.modules.domain.models.Customer
import kotlinx.serialization.Serializable

@Serializable
data class CustomerResponse(
    val id: String,
    val email: String,
    val fullName: String,
    val phone: String,
    val driversLicense: String,
    val isActive: Boolean
) {
    companion object {
        fun fromDomain(c: Customer) = CustomerResponse(
            id = c.id.value,
            email = c.email,
            fullName = c.fullName,
            phone = c.phone,
            driversLicense = c.driverLicenseNumber,
            isActive = c.isActive
        )
    }
}
```

---

## 4. Application Use Cases

### CreateCustomerUseCase.kt
```kotlin
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
        require(existingByEmail == null) { 
            "Customer with email ${request.email} already exists" 
        }
        
        // Check if driver's license already exists
        val existingByLicense = customerRepository.findByDriverLicense(request.driversLicense)
        require(existingByLicense == null) {
            "Customer with driver's license ${request.driversLicense} already exists"
        }
        
        // Parse and validate license expiry
        val licenseExpiry = try {
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
        
        val customer = Customer(
            id = CustomerId(UUID.randomUUID().toString()),
            userId = null,
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
```

### GetCustomerUseCase.kt
```kotlin
package com.solodev.fleet.modules.rentals.application.usecases

import com.solodev.fleet.modules.domain.models.Customer
import com.solodev.fleet.modules.domain.models.CustomerId
import com.solodev.fleet.modules.domain.ports.CustomerRepository

/**
 * Retrieves a customer by ID.
 */
class GetCustomerUseCase(private val customerRepository: CustomerRepository) {
    suspend fun execute(id: String): Customer? {
        return customerRepository.findById(CustomerId(id))
    }
}
```

### ListCustomersUseCase.kt
```kotlin
package com.solodev.fleet.modules.rentals.application.usecases

import com.solodev.fleet.modules.domain.models.Customer
import com.solodev.fleet.modules.domain.ports.CustomerRepository

/**
 * Lists all customers in the system.
 */
class ListCustomersUseCase(private val customerRepository: CustomerRepository) {
    suspend fun execute(): List<Customer> {
        return customerRepository.findAll()
    }
}
```

---

## 5. HTTP Routes

### CustomerRoutes.kt
```kotlin
package com.solodev.fleet.modules.rentals.infrastructure.http

import com.solodev.fleet.modules.domain.ports.CustomerRepository
import com.solodev.fleet.modules.rentals.application.dto.CustomerRequest
import com.solodev.fleet.modules.rentals.application.dto.CustomerResponse
import com.solodev.fleet.modules.rentals.application.usecases.*
import com.solodev.fleet.shared.models.ApiResponse
import com.solodev.fleet.shared.plugins.requestId
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * Customer management routes.
 *
 * Endpoints:
 * - GET /v1/customers - List all customers
 * - POST /v1/customers - Create a new customer
 * - GET /v1/customers/{id} - Get customer by ID
 */
fun Route.customerRoutes(customerRepository: CustomerRepository) {
    val createCustomerUC = CreateCustomerUseCase(customerRepository)
    val getCustomerUC = GetCustomerUseCase(customerRepository)
    val listCustomersUC = ListCustomersUseCase(customerRepository)

    route("/v1/customers") {
        // List all customers
        get {
            val customers = listCustomersUC.execute()
            val response = customers.map { CustomerResponse.fromDomain(it) }
            call.respond(ApiResponse.success(response, call.requestId))
        }

        // Create a new customer
        post {
            try {
                val request = call.receive<CustomerRequest>()
                val customer = createCustomerUC.execute(request)
                call.respond(
                    HttpStatusCode.Created,
                    ApiResponse.success(CustomerResponse.fromDomain(customer), call.requestId)
                )
            } catch (e: IllegalArgumentException) {
                call.respond(
                    HttpStatusCode.UnprocessableEntity,
                    ApiResponse.error(
                        "VALIDATION_ERROR",
                        e.message ?: "Invalid request",
                        call.requestId
                    )
                )
            }
        }

        // Get customer by ID
        route("/{id}") {
            get {
                val id = call.parameters["id"]
                    ?: return@get call.respond(
                        HttpStatusCode.BadRequest,
                        ApiResponse.error(
                            "MISSING_ID",
                            "Customer ID required",
                            call.requestId
                        )
                    )

                val customer = getCustomerUC.execute(id)
                if (customer != null) {
                    call.respond(
                        ApiResponse.success(
                            CustomerResponse.fromDomain(customer),
                            call.requestId
                        )
                    )
                } else {
                    call.respond(
                        HttpStatusCode.NotFound,
                        ApiResponse.error("NOT_FOUND", "Customer not found", call.requestId)
                    )
                }
            }
        }
    }
}
```

---

## 6. Repository Implementation

The `CustomerRepository` interface is defined in `RentalRepository.kt` and implemented by `CustomerRepositoryImpl.kt`.

### Interface (in RentalRepository.kt)
```kotlin
interface CustomerRepository {
    suspend fun findById(id: CustomerId): Customer?
    suspend fun findByEmail(email: String): Customer?
    suspend fun findByDriverLicense(licenseNumber: String): Customer?
    suspend fun save(customer: Customer): Customer
    suspend fun findAll(): List<Customer>
    suspend fun deleteById(id: CustomerId): Boolean
}
```

### Implementation
See `src/main/kotlin/com/solodev/fleet/modules/infrastructure/persistence/CustomerRepositoryImpl.kt`

---

## 7. Integration with Routing

In `src/main/kotlin/com/solodev/fleet/Routing.kt`:

```kotlin
import com.solodev.fleet.modules.infrastructure.persistence.CustomerRepositoryImpl
import com.solodev.fleet.modules.rentals.infrastructure.http.customerRoutes

fun Application.configureRouting() {
    val customerRepo = CustomerRepositoryImpl()

    routing {
        customerRoutes(customerRepository = customerRepo)
        // ... other routes
    }
}
```

---

## 8. API Endpoints

| Method | Endpoint | Description | Auth Required |
|--------|----------|-------------|---------------|
| GET | `/v1/customers` | List all customers | Yes |
| POST | `/v1/customers` | Create a new customer | Yes |
| GET | `/v1/customers/{id}` | Get customer by ID | Yes |

---

## 9. Test Scenarios

### Scenario 1: Create Customer (Happy Path)

**Request**:
```bash
POST http://localhost:8080/v1/customers
Content-Type: application/json

{
  "email": "john.doe@example.com",
  "firstName": "John",
  "lastName": "Doe",
  "phone": "+63-917-123-4567",
  "driversLicense": "N01-12-345678",
  "driverLicenseExpiry": "2028-12-31",
  "address": "123 Makati Avenue",
  "city": "Makati",
  "state": "Metro Manila",
  "postalCode": "1200",
  "country": "Philippines"
}
```

**Response** (201 Created):
```json
{
  "success": true,
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "email": "john.doe@example.com",
    "fullName": "John Doe",
    "phone": "+63-917-123-4567",
    "driversLicense": "N01-12-345678",
    "isActive": true
  },
  "requestId": "req_abc123"
}
```

### Scenario 2: Duplicate Email Error

**Request**:
```bash
POST http://localhost:8080/v1/customers
Content-Type: application/json

{
  "email": "john.doe@example.com",  // Already exists
  "firstName": "Jane",
  "lastName": "Smith",
  "phone": "+63-917-999-8888",
  "driversLicense": "N01-99-888777",
  "driverLicenseExpiry": "2027-06-30"
}
```

**Response** (422 Unprocessable Entity):
```json
{
  "success": false,
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Customer with email john.doe@example.com already exists"
  },
  "requestId": "req_error001"
}
```

### Scenario 3: Expired License Error

**Request**:
```bash
POST http://localhost:8080/v1/customers
Content-Type: application/json

{
  "email": "expired@example.com",
  "firstName": "Test",
  "lastName": "User",
  "phone": "+63-917-111-2222",
  "driversLicense": "N01-11-222333",
  "driverLicenseExpiry": "2020-01-01"  // Expired
}
```

**Response** (422 Unprocessable Entity):
```json
{
  "success": false,
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Driver's license is expired (expiry: 2020-01-01)"
  },
  "requestId": "req_error002"
}
```

### Scenario 4: List All Customers

**Request**:
```bash
GET http://localhost:8080/v1/customers
```

**Response** (200 OK):
```json
{
  "success": true,
  "data": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "email": "john.doe@example.com",
      "fullName": "John Doe",
      "phone": "+63-917-123-4567",
      "driversLicense": "N01-12-345678",
      "isActive": true
    }
  ],
  "requestId": "req_list001"
}
```

### Scenario 5: Get Customer by ID

**Request**:
```bash
GET http://localhost:8080/v1/customers/550e8400-e29b-41d4-a716-446655440000
```

**Response** (200 OK):
```json
{
  "success": true,
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "email": "john.doe@example.com",
    "fullName": "John Doe",
    "phone": "+63-917-123-4567",
    "driversLicense": "N01-12-345678",
    "isActive": true
  },
  "requestId": "req_get001"
}
```

---

## 10. Business Rules

1. **Email Uniqueness**: Each customer must have a unique email address
2. **License Uniqueness**: Each driver's license number must be unique
3. **License Validity**: Driver's license must not be expired at time of customer creation
4. **Required Fields**: Email, first name, last name, phone, driver's license, and expiry date are mandatory
5. **Optional Fields**: Address details (address, city, state, postal code, country) are optional
6. **User Link**: The `userId` field is optional and can link a customer to a system user account

---

## 11. Error Scenarios

| Scenario | Status | Error Code | Message |
|----------|--------|------------|---------|
| Missing ID parameter | 400 | MISSING_ID | Customer ID required |
| Customer not found | 404 | NOT_FOUND | Customer not found |
| Duplicate email | 422 | VALIDATION_ERROR | Customer with email X already exists |
| Duplicate license | 422 | VALIDATION_ERROR | Customer with driver's license X already exists |
| Expired license | 422 | VALIDATION_ERROR | Driver's license is expired (expiry: X) |
| Invalid date format | 422 | VALIDATION_ERROR | Invalid driver license expiry date format. Expected YYYY-MM-DD |
| Missing required field | 422 | VALIDATION_ERROR | [Field] cannot be blank |

---

## 12. cURL Examples

### Create Customer
```bash
curl -X POST http://localhost:8080/v1/customers \
  -H "Content-Type: application/json" \
  -d '{
    "email": "customer@example.com",
    "firstName": "John",
    "lastName": "Doe",
    "phone": "+63-917-123-4567",
    "driversLicense": "N01-12-345678",
    "driverLicenseExpiry": "2028-12-31"
  }'
```

### List Customers
```bash
curl -X GET http://localhost:8080/v1/customers
```

### Get Customer by ID
```bash
curl -X GET http://localhost:8080/v1/customers/550e8400-e29b-41d4-a716-446655440000
```

---

## 13. Database Schema

The `customers` table is created by migration `V003__create_rentals_schema.sql`:

```sql
CREATE TABLE customers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID UNIQUE REFERENCES users(id) ON DELETE SET NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    phone VARCHAR(20) NOT NULL,
    driver_license_number VARCHAR(50) NOT NULL UNIQUE,
    driver_license_expiry DATE NOT NULL,
    address TEXT,
    city VARCHAR(100),
    state VARCHAR(100),
    postal_code VARCHAR(20),
    country VARCHAR(100),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

---

## 14. Security & RBAC

| Endpoint | Required Permission |
|----------|---------------------|
| GET /v1/customers | `customers.read` |
| POST /v1/customers | `customers.create` |
| GET /v1/customers/{id} | `customers.read` |

---

## 15. Future Enhancements

- [ ] Update customer endpoint (PUT /v1/customers/{id})
- [ ] Soft delete customer (DELETE /v1/customers/{id})
- [ ] Search customers by email or license
- [ ] Link customer to user account
- [ ] Customer rental history endpoint
- [ ] Driver's license verification integration
- [ ] Customer loyalty program

---

**End of Customer Module Guide**
