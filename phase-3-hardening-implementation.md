# Phase 3 Hardening Implementation: RBAC, Idempotency, and Pagination

This document contains the complete code implementations for the remaining "Definition of Done" items in Phase 3. These components ensure the API is secure, reliable, and scalable.

---

## 🧠 Core Concepts & Definitions

### 1. RBAC (Role-Based Access Control)
**Definition**: A security mechanism that restricts system access to authorized users based on their specific role within the organization.
*   **Production Purpose**: To ensure **Least Privilege**. For example, a `RENTAL_AGENT` can create rentals but cannot delete a `VEHICLE` or access financial `ACCOUNTS`—only an `ADMIN` can perform those destructive or sensitive operations.

### 2. Idempotency
**Definition**: From the Latin *idem* (same) + *potens* (power). An idempotent operation is one that has no additional effect if it is called more than once with the same input parameters.
*   **Production Purpose**: **Safety in Retries**. In a distributed system, network timeouts are common. If a client sends a `POST /v1/rentals/{id}/pay` and the connection drops before they get the response, they will retry. Without idempotency, they might be **double-billed**. This code ensures that no matter how many times a client retries the same request (using the same `Idempotency-Key`), the server performs the action exactly once and returns the same successful result.

### 3. Cursor-Based Pagination
**Definition**: A technique for retrieving large datasets in small chunks (pages) using a unique, stable pointer (the "cursor") rather than a page number.
*   **Production Purpose**: **Performance & Stability**. Traditional `LIMIT/OFFSET` pagination becomes slow as the offset grows (the DB must skip thousands of rows). Cursor-based pagination is constant-time and prevents data from being skipped or duplicated if new records are added while a user is scrolling.

---

## 1. Role-Based Access Control (RBAC) Enforcement

### **Step 1: Define Roles and Principal Extensions**
File: `src/main/kotlin/com/solodev/fleet/shared/plugins/Security.kt` (Additions)

```kotlin
import io.ktor.server.auth.jwt.*
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.util.pipeline.*

/**
 * Enumeration of all valid staff and user roles in the system.
 */
enum class UserRole {
    ADMIN,            // Full system access
    FLEET_MANAGER,    // Manage vehicles and inventory
    CUSTOMER_SUPPORT, // View customers and handle basic issues
    RENTAL_AGENT,     // Manage active rental lifecycles
    CUSTOMER          // Basic self-service access
}

/**
 * Extension to extract and map roles from the JWT 'roles' claim into our UserRole enum.
 */
fun JWTPrincipal.getRoles(): List<UserRole> {
    return payload.getClaim("roles").asList(String::class.java)
        ?.mapNotNull { runCatching { UserRole.valueOf(it.uppercase()) }.getOrNull() } 
        ?: emptyList()
}

/**
 * Route-scoped authorization wrapper.
 * Intercepts requests and verifies that the authenticated user has at least one of the required roles.
 */
fun Route.withRoles(vararg roles: UserRole, build: Route.() -> Unit): Route {
    val authorizedRoute = createChild(object : RouteSelector() {
        override fun evaluate(context: RoutingResolveContext, segmentIndex: Int) = RouteSelectorEvaluation.Constant
    })
    
    authorizedRoute.intercept(ApplicationCallPipeline.Plugins) {
        val principal = call.principal<JWTPrincipal>()
        val userRoles = principal?.getRoles() ?: emptyList()
        
        // Admins automatically bypass specific role checks
        if (roles.none { it in userRoles } && UserRole.ADMIN !in userRoles) {
            call.respond(HttpStatusCode.Forbidden, ApiResponse.error(
                code = "FORBIDDEN",
                message = "You do not have the required permissions.",
                requestId = call.requestId
            ))
            finish() // Stop the pipeline to prevent executing the handler
        }
    }
    
    authorizedRoute.build()
    return authorizedRoute
}
```

---

## 2. Idempotency Middleware

### **Step 1: Idempotency Repository Implementation**
*Purpose: Persistently store request keys and their resulting responses.*
File: `src/main/kotlin/com/solodev/fleet/shared/infrastructure/persistence/IdempotencyRepositoryImpl.kt`

```kotlin
package com.solodev.fleet.shared.infrastructure.persistence

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.*

class IdempotencyRepositoryImpl {
    /** Map to the 'idempotency_keys' table created in V006 migration */
    object IdempotencyKeys : Table("idempotency_keys") {
        val id = uuid("id")
        val idempotencyKey = varchar("idempotency_key", 255).uniqueIndex()
        val responseStatus = integer("response_status").nullable()
        val responseBody = text("response_body").nullable()
        val expiresAt = timestamp("expires_at")
        override val primaryKey = PrimaryKey(id)
    }

    /** Lookup an existing key to see if this request was already processed */
    fun find(key: String) = transaction {
        IdempotencyKeys.select { IdempotencyKeys.idempotencyKey eq key }
            .map { 
                StoredResponse(
                    it[IdempotencyKeys.responseStatus],
                    it[IdempotencyKeys.responseBody]
                )
            }.singleOrNull()
    }

    /** Claim a key to prevent other concurrent requests with the same key */
    fun create(key: String, ttlMinutes: Long = 60) = transaction {
        IdempotencyKeys.insert {
            it[id] = UUID.randomUUID()
            it[idempotencyKey] = key
            it[expiresAt] = Instant.now().plusSeconds(ttlMinutes * 60)
        }
    }

    /** Store the final successful or failed response for future retries */
    fun updateResponse(key: String, status: Int, body: String) = transaction {
        IdempotencyKeys.update({ IdempotencyKeys.idempotencyKey eq key }) {
            it[responseStatus] = status
            it[responseBody] = body
        }
    }
}

data class StoredResponse(val status: Int?, val body: String?)
```

### **Step 2: Ktor Idempotency Plugin**
*Purpose: Automatically handle the 'Idempotency-Key' header on all POST/PATCH routes.*
File: `src/main/kotlin/com/solodev/fleet/shared/plugins/Idempotency.kt`

```kotlin
package com.solodev.fleet.shared.plugins

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.http.*
import io.ktor.util.*

val IdempotencyKey = AttributeKey<String>("IdempotencyKey")

/**
 * Plugin that enforces idempotency. 
 * If a key is seen again, it returns the cached response instead of re-running the logic.
 */
val Idempotency = createRouteScopedPlugin(name = "Idempotency", createConfiguration = { IdempotencyConfig() }) {
    val repository = IdempotencyRepositoryImpl()

    onCall { call ->
        val key = call.request.headers["Idempotency-Key"] ?: return@onCall
        call.attributes.put(IdempotencyKey, key)

        val existing = repository.find(key)
        if (existing != null) {
            if (existing.status != null && existing.body != null) {
                // RETURN CACHED: Request finished previously, send same response back.
                call.respondText(existing.body, ContentType.Application.Json, HttpStatusCode.fromValue(existing.status))
            } else {
                // CONFLICT: Request is currently being processed by another thread/instance.
                call.respond(HttpStatusCode.Conflict, "Request with this idempotency key is already in progress.")
            }
        } else {
            // NEW: Record that we are starting to process this key.
            repository.create(key)
        }
    }

    /** Capture the outgoing response to cache it for the next time this key is used */
    onRenderedContent { call, content ->
        val key = call.attributes.getOrNull(IdempotencyKey) ?: return@onRenderedContent
        val status = call.response.status()?.value ?: 200
        val body = content.toString() 
        repository.updateResponse(key, status, body)
    }
}

class IdempotencyConfig
```

---

## 3. Cursor-Based Pagination

### **Step 1: Pagination Models**
File: `src/main/kotlin/com/solodev/fleet/shared/models/Pagination.kt`

```kotlin
package com.solodev.fleet.shared.models

import kotlinx.serialization.Serializable

@Serializable
data class PaginatedResponse<T>(
    val items: List<T>,      // Core data
    val nextCursor: String?, // The ID of the last item to use for the NEXT request
    val limit: Int,          // Page size
    val total: Long? = null  // Optional grand total
)

data class PaginationParams(
    val limit: Int,
    val cursor: String?
)
```

### **Step 2: Pagination Utility**
File: `src/main/kotlin/com/solodev/fleet/shared/plugins/Pagination.kt`

```kotlin
package com.solodev.fleet.shared.plugins

import com.solodev.fleet.shared.models.PaginationParams
import io.ktor.server.application.*

/**
 * Helper to extract ?limit=X&cursor=Y from the URL.
 */
fun ApplicationCall.paginationParams(defaultLimit: Int = 20, maxLimit: Int = 100): PaginationParams {
    val limit = request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, maxLimit) ?: defaultLimit
    val cursor = request.queryParameters["cursor"]
    return PaginationParams(limit, cursor)
}
```

---

## 🏗️ How to Apply These Changes

1.  **RBAC**: Use `.withRoles(UserRole.ADMIN)` around sensitive routes in your module `Routes.kt` files.
2.  **Idempotency**: Apply the `install(Idempotency)` plugin to `POST` routes that modify state (Payments, Rental Start).
3.  **Pagination**: Update `GET` list endpoints to use `call.paginationParams()` and return the `PaginatedResponse` wrapper.

---

## 🧪 Expected Behavior (API Output Examples)

### 1. RBAC (Access Denied)
**Scenario**: A user with role `CUSTOMER` tries to access `DELETE /v1/vehicles`.

**Output**:
```json
{
  "success": false,
  "error": {
    "code": "FORBIDDEN",
    "message": "You do not have the required permissions."
  },
  "requestId": "req_12345"
}
```

### 2. Idempotency (Handing Retries)
**Scenario**: Client sends `POST` with `Idempotency-Key: pay_unique_789`.

**First Call (Standard Response)**:
```http
HTTP/1.1 201 Created
{
  "success": true,
  "data": { "paymentStatus": "CAPTURED" },
  "requestId": "req_abc"
}
```

**Second Call (Identical Retry - Handled by Plugin)**:
*The server returns the cached result without hitting the database/payment gateway again.*
```http
HTTP/1.1 201 Created
X-Idempotency-Cache: HIT
{
  "success": true,
  "data": { "paymentStatus": "CAPTURED" },
  "requestId": "req_abc"
}
```

### 3. Pagination (Large Lists)
**Scenario**: `GET /v1/vehicles?limit=2`

**Output**:
```json
{
  "success": true,
  "data": {
    "items": [
      { "id": "uuid-1", "plateNumber": "ABC-123" },
      { "id": "uuid-2", "plateNumber": "XYZ-789" }
    ],
    "nextCursor": "uuid-2",
    "limit": 2,
    "total": 150
  },
  "requestId": "req_999"
}
```
