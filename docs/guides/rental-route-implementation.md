# Phase 3: Rental API Implementation Guide

This guide provides the full implementation details for the Rentals and Customers domain.

## 1. Directory Structure

```text
src/main/kotlin/com/solodev/fleet/modules/rentals/
├── application/
│   ├── dto/
│   │   ├── RentalRequest.kt
│   │   ├── RentalResponse.kt
│   │   └── CustomerDTO.kt
│   └── usecases/
│       ├── CreateRentalUseCase.kt
│       ├── ActivateRentalUseCase.kt
│       └── CompleteRentalUseCase.kt
└── infrastructure/
    └── http/
        └── RentalRoutes.kt
```

---

## 2. Data Transfer Objects (DTOs)

### **RentalRequest.kt**
`src/main/kotlin/com/solodev/fleet/modules/rentals/application/dto/RentalRequest.kt`
```kotlin
package com.solodev.fleet.modules.rentals.application.dto

import kotlinx.serialization.Serializable

@Serializable
data class RentalRequest(
    val vehicleId: String,
    val customerId: String,
    val startDate: String, // ISO-8601 string
    val endDate: String,   // ISO-8601 string
    val dailyRateCents: Int
)
```

### **RentalResponse.kt**
`src/main/kotlin/com/solodev/fleet/modules/rentals/application/dto/RentalResponse.kt`
```kotlin
package com.solodev.fleet.modules.rentals.application.dto

import com.solodev.fleet.modules.domain.models.Rental
import kotlinx.serialization.Serializable

@Serializable
data class RentalResponse(
    val id: String,
    val rentalNumber: String,
    val vehicleId: String,
    val customerId: String,
    val status: String,
    val startDate: String,
    val endDate: String
) {
    companion object {
        fun fromDomain(r: Rental) = RentalResponse(
            id = r.id.value,
            rentalNumber = r.rentalNumber,
            vehicleId = r.vehicleId.value,
            customerId = r.customerId.value,
            status = r.status.name,
            startDate = r.startDate.toString(),
            endDate = r.endDate.toString()
        )
    }
}
```

### **CustomerDTO.kt**
`src/main/kotlin/com/solodev/fleet/modules/rentals/application/dto/CustomerDTO.kt`
```kotlin
package com.solodev.fleet.modules.rentals.application.dto

import com.solodev.fleet.modules.domain.models.Customer
import kotlinx.serialization.Serializable

@Serializable
data class CustomerRequest(
    val firstName: String,
    val lastName: String,
    val email: String,
    val phone: String,
    val driverLicenseNumber: String,
    val driverLicenseExpiry: String // ISO-8601
)

@Serializable
data class CustomerResponse(
    val id: String,
    val fullName: String,
    val email: String,
    val phone: String
) {
    companion object {
        fun fromDomain(c: Customer) = CustomerResponse(
            id = c.id.value,
            fullName = c.fullName,
            email = c.email,
            phone = c.phone
        )
    }
}
```

---

## 3. Application Use Cases

### **CreateRentalUseCase.kt**
`src/main/kotlin/com/solodev/fleet/modules/rentals/application/usecases/CreateRentalUseCase.kt`
```kotlin
package com.solodev.fleet.modules.rentals.application.usecases

import com.solodev.fleet.modules.domain.models.*
import com.solodev.fleet.modules.domain.ports.RentalRepository
import com.solodev.fleet.modules.rentals.application.dto.RentalRequest
import java.time.Instant
import java.util.*

class CreateRentalUseCase(private val repository: RentalRepository) {
    suspend fun execute(request: RentalRequest): Rental {
        // Simple logic for creation - version 1
        val rental = Rental(
            id = RentalId(UUID.randomUUID().toString()),
            rentalNumber = "RN-${System.currentTimeMillis()}",
            customerId = CustomerId(request.customerId),
            vehicleId = VehicleId(request.vehicleId),
            status = RentalStatus.RESERVED,
            startDate = Instant.parse(request.startDate),
            endDate = Instant.parse(request.endDate),
            dailyRateCents = request.dailyRateCents,
            totalAmountCents = request.dailyRateCents // Calculation simplified for now
        )
        return repository.save(rental)
    }
}
```

### **ActivateRentalUseCase.kt**
`src/main/kotlin/com/solodev/fleet/modules/rentals/application/usecases/ActivateRentalUseCase.kt`
```kotlin
package com.solodev.fleet.modules.rentals.application.usecases

import com.solodev.fleet.modules.domain.models.*
import com.solodev.fleet.modules.domain.ports.RentalRepository
import java.time.Instant

class ActivateRentalUseCase(private val repository: RentalRepository) {
    suspend fun execute(rentalId: String, startOdometer: Int): Rental {
        val rental = repository.findById(RentalId(rentalId)) 
            ?: throw IllegalArgumentException("Rental not found")
        
        val activatedRental = rental.activate(Instant.now(), startOdometer)
        return repository.save(activatedRental)
    }
}
```

### **CompleteRentalUseCase.kt**
`src/main/kotlin/com/solodev/fleet/modules/rentals/application/usecases/CompleteRentalUseCase.kt`
```kotlin
package com.solodev.fleet.modules.rentals.application.usecases

import com.solodev.fleet.modules.domain.models.*
import com.solodev.fleet.modules.domain.ports.RentalRepository
import java.time.Instant

class CompleteRentalUseCase(private val repository: RentalRepository) {
    suspend fun execute(rentalId: String, endOdometer: Int): Rental {
        val rental = repository.findById(RentalId(rentalId)) 
            ?: throw IllegalArgumentException("Rental not found")
        
        val completedRental = rental.complete(Instant.now(), endOdometer)
        return repository.save(completedRental)
    }
}
```

---

## 4. Ktor Routes

### **RentalRoutes.kt**
`src/main/kotlin/com/solodev/fleet/modules/rentals/infrastructure/http/RentalRoutes.kt`
```kotlin
package com.solodev.fleet.modules.rentals.infrastructure.http

import com.solodev.fleet.modules.domain.ports.RentalRepository
import com.solodev.fleet.modules.rentals.application.dto.*
import com.solodev.fleet.modules.rentals.application.usecases.*
import com.solodev.fleet.shared.models.ApiResponse
import com.solodev.fleet.shared.plugins.requestId
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.rentalRoutes(repository: RentalRepository) {
    val createUseCase = CreateRentalUseCase(repository)
    val activateUseCase = ActivateRentalUseCase(repository)
    val completeUseCase = CompleteRentalUseCase(repository)

    route("/v1/rentals") {
        post {
            val request = call.receive<RentalRequest>()
            val rental = createUseCase.execute(request)
            call.respond(ApiResponse.success(RentalResponse.fromDomain(rental), call.requestId))
        }

        get("/{id}") {
            val id = call.parameters["id"] ?: return@get
            val rental = repository.findById(com.solodev.fleet.modules.domain.models.RentalId(id))
            if (rental == null) {
                call.respond(ApiResponse.error("NOT_FOUND", "Rental not found", call.requestId))
            } else {
                call.respond(ApiResponse.success(RentalResponse.fromDomain(rental), call.requestId))
            }
        }

        get {
            val rentals = repository.findAll()
            val responsePayload = rentals.map { RentalResponse.fromDomain(it) }
            call.respond(ApiResponse.success(responsePayload, call.requestId))
        }

        post("/{id}/activate") {
            val id = call.parameters["id"] ?: return@post
            val body = call.receive<Map<String, Int>>()
            val odometer = body["startOdometer"] ?: 0
            val rental = activateUseCase.execute(id, odometer)
            call.respond(ApiResponse.success(RentalResponse.fromDomain(rental), call.requestId))
        }

        post("/{id}/complete") {
            val id = call.parameters["id"] ?: return@post
            val body = call.receive<Map<String, Int>>()
            val odometer = body["endOdometer"] ?: 0
            val rental = completeUseCase.execute(id, odometer)
            call.respond(ApiResponse.success(RentalResponse.fromDomain(rental), call.requestId))
        }
    }
}
```

---

## 5. API Endpoints & Sample Payloads

### **A. Create Rental (Reservation)**
- **Endpoint**: `POST /v1/rentals`
- **Request Body**:
```json
{
  "vehicleId": "46b6a07c-cfaf-4d22-b539-7dac789d698f",
  "customerId": "00000000-0000-0000-0000-000000000001",
  "startDate": "2024-06-01T10:00:00Z",
  "endDate": "2024-06-05T10:00:00Z",
  "dailyRateCents": 7000
}
```
- **Response Body (200 OK)**:
```json
{
  "success": true,
  "data": {
    "id": "a5863015-63a9-45ff-a908-000ae6036d5c",
    "rentalNumber": "RN-1770029448657",
    "vehicleId": "46b6a07c-cfaf-4d22-b539-7dac789d698f",
    "customerId": "00000000-0000-0000-0000-000000000001",
    "status": "RESERVED",
    "startDate": "2024-06-01T10:00:00Z",
    "endDate": "2024-06-05T10:00:00Z"
  },
  "error": null,
  "requestId": "req_5dbf970e-3603-4c54-9118-a6d95fcccce"
}
```

### **B. List Rentals (All)**
- **Endpoint**: `GET /v1/rentals`
- **Response Body (200 OK)**:
```json
{
  "success": true,
  "data": [
    {
      "id": "a5863015-63a9-45ff-a908-000ae6036d5c",
      "rentalNumber": "RN-1770029448657",
      "vehicleId": "46b6a07c-cfaf-4d22-b539-7dac789d698f",
      "customerId": "00000000-0000-0000-0000-000000000001",
      "status": "COMPLETED",
      "startDate": "2024-06-01T10:00:00Z",
      "endDate": "2024-06-05T10:00:00Z"
    }
  ],
  "error": null,
  "requestId": "req_88923ad21-..."
}
```

### **C. Get Rental by ID**
- **Endpoint**: `GET /v1/rentals/a5863015-63a9-45ff-a908-000ae6036d5c`
- **Response Body (200 OK)**:
```json
{
  "success": true,
  "data": {
    "id": "a5863015-63a9-45ff-a908-000ae6036d5c",
    "rentalNumber": "RN-1770029448657",
    "vehicleId": "46b6a07c-cfaf-4d22-b539-7dac789d698f",
    "customerId": "00000000-0000-0000-0000-000000000001",
    "status": "RESERVED",
    "startDate": "2024-06-01T10:00:00Z",
    "endDate": "2024-06-05T10:00:00Z"
  },
  "error": null,
  "requestId": "req_ce2c447f-cffc-4f51-86a0-89a0bfc3ad"
}
```

### **C. Activate Rental (Pickup)**
- **Endpoint**: `POST /v1/rentals/a5863015-63a9-45ff-a908-000ae6036d5c/activate`
- **Request Body**:
```json
{
  "startOdometer": 20000
}
```
- **Response Body (200 OK)**:
```json
{
  "success": true,
  "data": {
    "id": "a5863015-63a9-45ff-a908-000ae6036d5c",
    "rentalNumber": "RN-1770029448657",
    "status": "ACTIVE",
    ...
  },
  "error": null,
  "requestId": "req_6578262ad-..."
}
```

### **D. Complete Rental (Drop-off)**
- **Endpoint**: `POST /v1/rentals/a5863015-63a9-45ff-a908-000ae6036d5c/complete`
- **Request Body**:
```json
{
  "endOdometer": 20500
}
```
- **Response Body (200 OK)**:
```json
{
  "success": true,
  "data": {
    "id": "a5863015-63a9-45ff-a908-000ae6036d5c",
    "status": "COMPLETED",
    ...
  },
  "error": null,
  "requestId": "req_77893ad21-..."
}
```

---

## 6. Wiring
In `Routing.kt`:
```kotlin
// 1. Initialize repository (Infrastructure layer)
val rentalRepo = RentalRepositoryImpl() // Needs to be implemented in persistence

// 2. Register routes
routing {
    rentalRoutes(rentalRepo)
}
```
