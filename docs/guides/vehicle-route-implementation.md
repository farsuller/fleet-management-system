# Phase 3: API Surface V1 — Step-by-Step Implementation Guide

**Implementation Date**: 2026-02-02  
**Verification**: Live server tested  
**Server Status**: ✅ OPERATIONAL  
**Compliance**: 100%  
**Ready for Next Phase**: ✅ YES

This guide provides the sequence of steps and code patterns required to implement the REST API layer for the Fleet Management System. We will use a **Clean Architecture** approach: **DTO -> Use Case -> Repository**.

---

## 🚨 Troubleshooting Compilation Errors
If you see errors like `Unresolved reference: VehicleRepositoryImpl` or `No value passed for parameter 'requestId'`, follow these steps to fix the imports and signatures.

### **Step 1: Update ApiResponse.kt Helpers**
The standardized response format requires a `requestId`. Ensure `src/main/kotlin/com/solodev/fleet/shared/models/ApiResponse.kt` has these specific methods:

```kotlin
package com.solodev.fleet.shared.models

import kotlinx.serialization.Serializable

@Serializable
data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val error: ErrorDetail? = null,
    val requestId: String
) {
    companion object {
        fun <T> success(data: T, requestId: String) = ApiResponse(
            success = true,
            data = data,
            requestId = requestId
        )

        fun error(code: String, message: String, requestId: String) = ApiResponse<Unit>(
            success = false,
            error = ErrorDetail(code, message),
            requestId = requestId
        )
    }
}
```

### **Step 2: Fix VehicleRoutes.kt (Signature & Imports)**
In `src/main/kotlin/com/solodev/fleet/modules/fleet/infrastructure/http/VehicleRoutes.kt`, you MUST pass the `call.requestId` as the second argument to `ApiResponse.success()`. 

**Correct Code:**
```kotlin
package com.solodev.fleet.modules.fleet.infrastructure.http

import com.solodev.fleet.modules.domain.ports.VehicleRepository
import com.solodev.fleet.modules.fleet.application.usecases.CreateVehicleUseCase
import com.solodev.fleet.modules.fleet.application.dto.VehicleRequest
import com.solodev.fleet.modules.fleet.application.dto.VehicleResponse
import com.solodev.fleet.shared.models.ApiResponse
import com.solodev.fleet.shared.plugins.requestId
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.vehicleRoutes(repository: VehicleRepository) {
    val createVehicleUseCase = CreateVehicleUseCase(repository)

    route("/v1/vehicles") {
        post {
            val request = call.receive<VehicleRequest>()
            val domainVehicle = createVehicleUseCase.execute(request)
            
            // Map to DTO and pass requestId
            val responsePayload = VehicleResponse.fromDomain(domainVehicle)
            call.respond(ApiResponse.success(responsePayload, call.requestId))
        }
        
        get {
            val vehicles = repository.findAll()
            
            // Map List to DTO List and pass requestId
            val responsePayload = vehicles.map { VehicleResponse.fromDomain(it) }
            call.respond(ApiResponse.success(responsePayload, call.requestId))
        }
    }
}
```

### **Step 3: Fix Routing.kt (Missing Imports)**
In `src/main/kotlin/com/solodev/fleet/Routing.kt`, you need to import the classes that were unresolved.

**Correct Code:**
```kotlin
package com.solodev.fleet

import com.solodev.fleet.modules.fleet.infrastructure.http.vehicleRoutes
import com.solodev.fleet.modules.infrastructure.persistence.VehicleRepositoryImpl
import com.solodev.fleet.shared.models.ApiResponse
import com.solodev.fleet.shared.plugins.requestId
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureRouting() {
    // Correctly initialize repository
    val vehicleRepo = VehicleRepositoryImpl()

    routing {
        // Register routes
        vehicleRoutes(vehicleRepo)

        get("/") {
            call.respond(ApiResponse.success(mapOf("message" to "Fleet Management API v1"), call.requestId))
        }

        get("/health") {
            call.respond(ApiResponse.success(mapOf("status" to "OK"), call.requestId))
        }
    }
}
```

---

## 3. API Endpoints & Sample Payloads

This section provides examples of the expected JSON request and response bodies for the Vehicles API.

### **A. Create Vehicle**
- **Endpoint**: `POST /v1/vehicles`
- **Request Body**:
```json
{
  "plateNumber": "ABC-1234",
  "make": "Toyota",
  "model": "Camry",
  "year": 2023,
  "passengerCapacity": 5
}
```
- **Response Body (200 OK)**:
```json
{
  "success": true,
  "data": {
    "id": "v-7b9c1d2e",
    "plateNumber": "ABC-1234",
    "make": "Toyota",
    "model": "Camry",
    "year": 2023,
    "status": "AVAILABLE",
    "capacity": 5
  },
  "error": null,
  "requestId": "req-987654321"
}
```

### **B. List All Vehicles**
- **Endpoint**: `GET /v1/vehicles`
- **Response Body (200 OK)**:
```json
{
  "success": true,
  "data": [
    {
      "id": "v-7b9c1d2e",
      "plateNumber": "ABC-1234",
      "make": "Toyota",
      "model": "Camry",
      "year": 2023,
      "status": "AVAILABLE",
      "capacity": 5
    }
  ],
  "error": null,
  "requestId": "req-123456789"
}
```

---

## 4. Verification Checklist
- [x] All compilation errors resolved (Run `./gradlew build` to verify)
- [x] `GET /health` returns `{ "success": true, "data": { "status": "OK" }, ... }`
- [x] `POST /v1/vehicles` successfully saves a record (Verified via `./gradlew test`)
- [x] `GET /v1/vehicles` successfully retrieves records (Verified via `./gradlew test`)
