# Fleet Management API - Implementation Standards

**Version**: 1.0  
**Last Updated**: 2026-02-03  
**Applies To**: All Phase 3 API implementations

---

## Overview

This document defines the **mandatory standards** for all API route implementations in the Fleet Management System. These standards are derived from:

- **Backend Development Skill** - Production-ready Kotlin/Ktor patterns
- **Clean Code Skill** - Pragmatic, maintainable code principles
- **API Patterns Skill** - REST best practices and conventions
- **Lint & Validate Skill** - Quality control procedures

---

## Core Principles

### 1. **Single Responsibility Principle (SRP)**
- Each Use Case does ONE business operation
- Each Route handler does ONE HTTP operation
- Each DTO represents ONE data contract

### 2. **Fail-Fast Validation**
- Validate at API boundaries (DTOs)
- Validate domain rules in Use Cases
- Return 400/422 immediately for invalid input

### 3. **Explicit Error Handling**
- Use try-catch blocks for expected failures
- Map domain exceptions to HTTP status codes
- Never leak internal errors to clients

### 4. **Idiomatic Kotlin**
- Use data classes for DTOs
- Use sealed classes for domain results
- Leverage coroutines for async operations
- Prefer immutability

---

## REST API Conventions

### Resource Naming
```
✅ CORRECT:
- /v1/vehicles
- /v1/rentals
- /v1/maintenance/jobs
- /v1/users/{id}/profile

❌ WRONG:
- /v1/vehicle (singular)
- /v1/getRentals (verb in path)
- /v1/maintenance_jobs (underscore)
- /v1/users/123/profile/details/full (too deep)
```

### HTTP Method Usage
| Method | Use Case | Idempotent | Request Body |
|--------|----------|------------|--------------|
| GET | Read resource(s) | ✅ Yes | ❌ No |
| POST | Create resource | ❌ No | ✅ Yes |
| PUT | Replace entire resource | ✅ Yes | ✅ Yes |
| PATCH | Partial update | ⚠️ No* | ✅ Yes |
| DELETE | Remove resource | ✅ Yes | ❌ No |

*PATCH can be made idempotent with proper design

### Status Code Standards
| Code | Situation | Example |
|------|-----------|---------|
| 200 | Success (read/update) | GET /v1/vehicles/123 |
| 201 | Resource created | POST /v1/vehicles |
| 204 | Success, no content | DELETE /v1/vehicles/123 |
| 400 | Malformed request | Invalid JSON |
| 401 | Missing/invalid auth | No JWT token |
| 403 | Insufficient permissions | User can't delete vehicle |
| 404 | Resource not found | Vehicle ID doesn't exist |
| 409 | State conflict | Vehicle already rented |
| 422 | Validation error | VIN format invalid |
| 429 | Rate limit exceeded | Too many requests |
| 500 | Server error | Database connection failed |

---

## Code Structure Standards

### Directory Layout (Per Module)
```
modules/{domain}/
├── application/
│   ├── dto/
│   │   ├── {Resource}Request.kt      # Input DTOs
│   │   ├── {Resource}Response.kt     # Output DTOs
│   │   └── {Resource}UpdateRequest.kt # Partial update DTOs
│   └── usecases/
│       ├── Create{Resource}UseCase.kt
│       ├── Get{Resource}UseCase.kt
│       ├── Update{Resource}UseCase.kt
│       ├── Delete{Resource}UseCase.kt
│       └── List{Resources}UseCase.kt
└── infrastructure/
    └── http/
        └── {Resource}Routes.kt
```

### File Naming Conventions
- **DTOs**: `{Entity}Request`, `{Entity}Response`, `{Entity}UpdateRequest`
- **Use Cases**: `{Verb}{Entity}UseCase` (e.g., `CreateVehicleUseCase`)
- **Routes**: `{Entity}Routes.kt` (e.g., `VehicleRoutes.kt`)

---

## DTO Standards

### Request DTO Template
```kotlin
package com.solodev.fleet.modules.{domain}.application.dto

import kotlinx.serialization.Serializable

@Serializable
data class {Entity}Request(
    val field1: String,
    val field2: Int,
    val optionalField: String? = null
) {
    init {
        require(field1.isNotBlank()) { "field1 cannot be blank" }
        require(field2 > 0) { "field2 must be positive" }
    }
}
```

### Response DTO Template
```kotlin
@Serializable
data class {Entity}Response(
    val id: String,
    val field1: String,
    val field2: Int,
    val createdAt: String
) {
    companion object {
        fun fromDomain(entity: {Entity}): {Entity}Response = {Entity}Response(
            id = entity.id.value,
            field1 = entity.field1,
            field2 = entity.field2,
            createdAt = entity.createdAt.toString()
        )
    }
}
```

### Update Request DTO Template
```kotlin
@Serializable
data class {Entity}UpdateRequest(
    val field1: String? = null,
    val field2: Int? = null
) {
    fun hasUpdates(): Boolean = field1 != null || field2 != null
}
```

---

## Use Case Standards

### Use Case Template
```kotlin
package com.solodev.fleet.modules.{domain}.application.usecases

import com.solodev.fleet.modules.domain.models.*
import com.solodev.fleet.modules.domain.ports.{Entity}Repository
import com.solodev.fleet.modules.{domain}.application.dto.{Entity}Request
import java.util.UUID

class Create{Entity}UseCase(
    private val repository: {Entity}Repository
) {
    suspend fun execute(request: {Entity}Request): {Entity} {
        // 1. Map DTO to Domain
        val entity = {Entity}(
            id = {Entity}Id(UUID.randomUUID().toString()),
            field1 = request.field1,
            field2 = request.field2
        )
        
        // 2. Persist
        return repository.save(entity)
    }
}
```

### Use Case Rules
1. **Single Responsibility**: One use case = one business operation
2. **No HTTP Logic**: Use cases don't know about HTTP
3. **Domain-Centric**: Work with domain models, not DTOs
4. **Transaction Boundaries**: Define clear transaction scope
5. **Error Handling**: Throw domain exceptions, not HTTP exceptions

---

## Route Handler Standards

### Route Template
```kotlin
package com.solodev.fleet.modules.{domain}.infrastructure.http

import com.solodev.fleet.modules.domain.ports.{Entity}Repository
import com.solodev.fleet.modules.{domain}.application.dto.*
import com.solodev.fleet.modules.{domain}.application.usecases.*
import com.solodev.fleet.shared.models.ApiResponse
import com.solodev.fleet.shared.plugins.requestId
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.{entity}Routes(repository: {Entity}Repository) {
    // Initialize use cases
    val createUC = Create{Entity}UseCase(repository)
    val getUC = Get{Entity}UseCase(repository)
    val updateUC = Update{Entity}UseCase(repository)
    val deleteUC = Delete{Entity}UseCase(repository)
    val listUC = List{Entities}UseCase(repository)

    route("/v1/{entities}") {
        // List all
        get {
            val entities = listUC.execute()
            val response = entities.map { {Entity}Response.fromDomain(it) }
            call.respond(ApiResponse.success(response, call.requestId))
        }

        // Create
        post {
            val request = call.receive<{Entity}Request>()
            val entity = createUC.execute(request)
            call.respond(
                HttpStatusCode.Created,
                ApiResponse.success({Entity}Response.fromDomain(entity), call.requestId)
            )
        }

        // Single resource operations
        route("/{id}") {
            // Get by ID
            get {
                val id = call.parameters["id"] ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    ApiResponse.error("MISSING_ID", "ID parameter required", call.requestId)
                )
                
                val entity = getUC.execute(id)
                if (entity != null) {
                    call.respond(ApiResponse.success({Entity}Response.fromDomain(entity), call.requestId))
                } else {
                    call.respond(
                        HttpStatusCode.NotFound,
                        ApiResponse.error("NOT_FOUND", "{Entity} not found", call.requestId)
                    )
                }
            }

            // Update
            patch {
                val id = call.parameters["id"] ?: return@patch call.respond(
                    HttpStatusCode.BadRequest,
                    ApiResponse.error("MISSING_ID", "ID parameter required", call.requestId)
                )
                
                val request = call.receive<{Entity}UpdateRequest>()
                val updated = updateUC.execute(id, request)
                
                if (updated != null) {
                    call.respond(ApiResponse.success({Entity}Response.fromDomain(updated), call.requestId))
                } else {
                    call.respond(
                        HttpStatusCode.NotFound,
                        ApiResponse.error("NOT_FOUND", "{Entity} not found", call.requestId)
                    )
                }
            }

            // Delete
            delete {
                val id = call.parameters["id"] ?: return@delete call.respond(
                    HttpStatusCode.BadRequest,
                    ApiResponse.error("MISSING_ID", "ID parameter required", call.requestId)
                )
                
                val deleted = deleteUC.execute(id)
                if (deleted) {
                    call.respond(HttpStatusCode.NoContent)
                } else {
                    call.respond(
                        HttpStatusCode.NotFound,
                        ApiResponse.error("NOT_FOUND", "{Entity} not found", call.requestId)
                    )
                }
            }
        }
    }
}
```

### Route Handler Rules
1. **Guard Clauses**: Early returns for missing parameters
2. **Explicit Status Codes**: Use HttpStatusCode enum
3. **Consistent Responses**: Always use ApiResponse wrapper
4. **Error Mapping**: Map domain exceptions to HTTP codes
5. **No Business Logic**: Delegate to use cases

---

## Error Handling Standards

### Domain Exception Mapping
```kotlin
try {
    val result = useCase.execute(request)
    call.respond(ApiResponse.success(result, call.requestId))
} catch (e: EntityNotFoundException) {
    call.respond(
        HttpStatusCode.NotFound,
        ApiResponse.error("NOT_FOUND", e.message ?: "Resource not found", call.requestId)
    )
} catch (e: ValidationException) {
    call.respond(
        HttpStatusCode.UnprocessableEntity,
        ApiResponse.error("VALIDATION_ERROR", e.message ?: "Invalid data", call.requestId)
    )
} catch (e: ConflictException) {
    call.respond(
        HttpStatusCode.Conflict,
        ApiResponse.error("CONFLICT", e.message ?: "Resource conflict", call.requestId)
    )
} catch (e: Exception) {
    call.respond(
        HttpStatusCode.InternalServerError,
        ApiResponse.error("INTERNAL_ERROR", "An unexpected error occurred", call.requestId)
    )
}
```

---

## Testing Standards

### Unit Test Template
```kotlin
class Create{Entity}UseCaseTest {
    private lateinit var repository: {Entity}Repository
    private lateinit var useCase: Create{Entity}UseCase

    @BeforeEach
    fun setup() {
        repository = mockk()
        useCase = Create{Entity}UseCase(repository)
    }

    @Test
    fun `should create entity successfully`() = runBlocking {
        // Given
        val request = {Entity}Request(field1 = "value", field2 = 123)
        val expected = {Entity}(/* ... */)
        coEvery { repository.save(any()) } returns expected

        // When
        val result = useCase.execute(request)

        // Then
        assertEquals(expected, result)
        coVerify { repository.save(any()) }
    }
}
```

---

## Documentation Requirements

Each implementation guide MUST include:

1. **Status Header** - Implementation date, verification status, compliance %
2. **Directory Structure** - Complete file tree
3. **DTO Definitions** - All request/response classes with validation
4. **Use Case Implementations** - Complete business logic
5. **Route Handlers** - Full HTTP endpoint definitions
6. **API Reference Table** - Method, Path, Description, Auth requirements
7. **Sample Payloads** - Request/response examples with actual data
8. **Error Scenarios** - Common error cases and responses
9. **Wiring Instructions** - How to register routes
10. **Security Notes** - RBAC requirements

---

## Quality Checklist

Before marking any implementation as complete:

- [ ] All DTOs have validation in `init` blocks
- [ ] All Use Cases follow single responsibility
- [ ] All Routes use guard clauses for parameters
- [ ] All Responses use ApiResponse wrapper
- [ ] All Errors mapped to appropriate HTTP codes
- [ ] All Functions are < 20 lines
- [ ] All Names are self-documenting
- [ ] No magic numbers or strings
- [ ] No commented-out code
- [ ] Kotlin compilation passes
- [ ] No lint warnings

---

## References

- **Backend Development**: `skills/backend-development/SKILL.md`
- **Clean Code**: `skills/clean-code/SKILL.md`
- **API Patterns**: `skills/api-patterns/rest.md`
- **Lint & Validate**: `skills/lint-and-validate/SKILL.md`
