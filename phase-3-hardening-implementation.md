# Phase 3 Hardening Implementation: RBAC, Idempotency, and Pagination

> **Status: ✅ APPLIED**
> All components described in this document have been implemented, verified, and integrated into the Fleet Management codebase.

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
 * Plugin configuration for Authorization.
 */
class AuthorizationConfig {
    var requiredRoles: List<UserRole> = emptyList()
}

/**
 * Route-scoped authorization plugin.
 * Verifies that the authenticated user has at least one of the required roles.
 */
val Authorization = createRouteScopedPlugin(name = "Authorization", createConfiguration = ::AuthorizationConfig) {
    val roles = pluginConfig.requiredRoles

    on(AuthenticationChecked) { call ->
        val principal = call.principal<JWTPrincipal>()
        val userRoles = principal?.getRoles() ?: emptyList()

        if (roles.isNotEmpty() && roles.none { it in userRoles } && UserRole.ADMIN !in userRoles) {
            call.respond(HttpStatusCode.Forbidden, ApiResponse.error(
                code = "FORBIDDEN",
                message = "You do not have the required permissions.",
                requestId = call.requestId
            ))
        }
    }
}

/**
 * Extension to apply authorization to a route.
 */
fun Route.withRoles(vararg roles: UserRole, build: Route.() -> Unit): Route {
    install(Authorization) {
        requiredRoles = roles.toList()
    }
    build()
    return this
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
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.json.jsonb
import org.jetbrains.exposed.sql.transactions.transaction
import kotlinx.serialization.json.Json
import java.time.Instant
import java.util.*

class IdempotencyRepositoryImpl {
    /** Map to the 'idempotency_keys' table created in V006 migration */
    object IdempotencyKeys : Table("idempotency_keys") {
        val id = uuid("id")
        val idempotencyKey = varchar("idempotency_key", 255).uniqueIndex()
        val requestPath = varchar("request_path", 500)
        val requestMethod = varchar("request_method", 10)
        val responseStatus = integer("response_status").nullable()
        // Use 'jsonb' to match the PostgreSQL column type
        val responseBody = jsonb<String>("response_body", Json { ignoreUnknownKeys = true }).nullable()
        val expiresAt = timestamp("expires_at")
        override val primaryKey = PrimaryKey(id)
    }

    /** Lookup an existing key */
    fun find(key: String) = transaction {
        IdempotencyKeys.select { IdempotencyKeys.idempotencyKey eq key }
            .map { 
                StoredResponse(
                    it[IdempotencyKeys.responseStatus],
                    it[IdempotencyKeys.responseBody]
                )
            }.singleOrNull()
    }

    /** Claim a key */
    fun create(key: String, path: String, method: String, ttlMinutes: Long = 60) = transaction {
        IdempotencyKeys.insert {
            it[id] = UUID.randomUUID()
            it[idempotencyKey] = key
            it[requestPath] = path
            it[requestMethod] = method
            it[expiresAt] = Instant.now().plusSeconds(ttlMinutes * 60)
        }
    }

    /** Update response */
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
import io.ktor.http.content.*
import io.ktor.util.*

val IdempotencyKey = AttributeKey<String>("IdempotencyKey")

/**
 * Plugin that enforces idempotency. 
 * If a key is seen again, it returns the cached response instead of re-running the logic.
 */
val Idempotency = createRouteScopedPlugin(name = "Idempotency", createConfiguration = ::IdempotencyConfig) {
    val repository = pluginConfig.repository
    val headerName = pluginConfig.headerName
    val expiresInMinutes = pluginConfig.expiresInMinutes

    onCall { call ->
        val key = call.request.headers[headerName] ?: return@onCall
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
            // Pass the path and method to satisfy the DB schema constraints
            repository.create(
                key = key,
                path = call.request.uri,
                method = call.request.httpMethod.value,
                ttlMinutes = expiresInMinutes
            )
        }
    }

    /** 
     * Capture the outgoing response to cache it for the next time this key is used.
     * Note: In production, you would use a custom transformation or interceptor to ensure 
     * the body is captured in its final JSON string format.
     */
    onCallRespond { call, message ->
        val key = call.attributes.getOrNull(IdempotencyKey) ?: return@onCallRespond
        val status = call.response.status()?.value ?: 200
        
        // Simplified body capture. 
        // For production, ensure your serialization pipeline allows string capture.
        val body = when (message) {
            is String -> message
            is TextContent -> message.text
            else -> message.toString() 
        }
        
        repository.updateResponse(key, status, body)
    }
}

/**
 * Configuration for the Idempotency plugin.
 */
class IdempotencyConfig {
    var repository: IdempotencyRepositoryImpl = IdempotencyRepositoryImpl()
    var headerName: String = "Idempotency-Key"
    var expiresInMinutes: Long = 60
}
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

This section provides a step-by-step guide on how to integrate these hardening features into your existing codebase.

### 1. Step-By-Step: Applying RBAC (Role-Based Access Control)

RBAC should be applied consistently across all domain modules. Use the `withRoles` extension to wrap groups of routes or individual endpoints.

#### **A. Vehicle Module (`VehicleRoutes.kt`)**
| Endpoint | Allowed Roles | Purpose |
| :--- | :--- | :--- |
| `GET /v1/vehicles` | All Staff | View inventory |
| `POST /v1/vehicles` | `ADMIN`, `FLEET_MANAGER` | Add new asset |
| `GET /v1/vehicles/{id}` | All Staff | View details |
| `PATCH /v1/vehicles/{id}` | `ADMIN`, `FLEET_MANAGER` | Update specs |
| `DELETE /v1/vehicles/{id}` | `ADMIN` | Decommission asset |
| `PATCH /v1/vehicles/{id}/state` | `ADMIN`, `FLEET_MANAGER`, `RENTAL_AGENT` | Change status (Ready, Maintenance) |
| `POST /v1/vehicles/{id}/odometer` | `ADMIN`, `FLEET_MANAGER`, `RENTAL_AGENT` | Log mileage |

#### **B. Rental Module (`RentalRoutes.kt`)**
| Endpoint | Allowed Roles | Purpose |
| :--- | :--- | :--- |
| `GET /v1/rentals` | All Staff | View trip history |
| `POST /v1/rentals` | `ADMIN`, `RENTAL_AGENT` | Create new reservation |
| `POST /v1/rentals/{id}/activate` | `ADMIN`, `RENTAL_AGENT` | Start the trip |
| `POST /v1/rentals/{id}/complete` | `ADMIN`, `RENTAL_AGENT` | Finish trip & return vehicle |
| `POST /v1/rentals/{id}/cancel` | `ADMIN`, `RENTAL_AGENT` | Void reservation |

#### **C. Maintenance Module (`MaintenanceRoutes.kt`)**
| Endpoint | Allowed Roles | Purpose |
| :--- | :--- | :--- |
| `GET /v1/maintenance` | All Staff | View service logs |
| `POST /v1/maintenance` | `ADMIN`, `FLEET_MANAGER` | Schedule service |
| `PATCH /v1/maintenance/{id}` | `ADMIN`, `FLEET_MANAGER` | Update job details |
| `DELETE /v1/maintenance/{id}` | `ADMIN` | Remove log entry |
| `POST /v1/maintenance/{id}/parts` | `ADMIN`, `FLEET_MANAGER` | Log parts/inventory used |
| `POST /v1/maintenance/{id}/complete` | `ADMIN`, `FLEET_MANAGER` | Finalize job |

#### **D. Accounting Module (`AccountingRoutes.kt`)**
| Endpoint | Allowed Roles | Purpose |
| :--- | :--- | :--- |
| `POST /v1/accounting/invoices` | `ADMIN`, `RENTAL_AGENT` | Issue billing |
| `POST /v1/accounting/invoices/{id}/pay` | `ADMIN`, `RENTAL_AGENT` | Record payment |
| `GET /v1/accounting/accounts/balance` | `ADMIN`, `FLEET_MANAGER` | Financial oversight |
| `GET /v1/accounting/payment-methods` | All Staff | View billing setup |
| `POST /v1/accounting/payment-methods` | `ADMIN` | Configure gateways |
| `DELETE /v1/accounting/payments/{id}` | `ADMIN` | Financial corrections |

**Code Example Implementation (e.g., `VehicleRoutes.kt`):**
```kotlin
route("/v1/vehicles") {
    get { /* logic for listing */ }
    
    // Protected administrative actions
    withRoles(UserRole.ADMIN, UserRole.FLEET_MANAGER) {
        post { /* register vehicle */ }
        route("/{id}") {
            patch { /* update vehicle */ }
            delete { /* admin only deletion */ }
        }
    }
}
```

### 2. Step-By-Step: Applying Idempotency
Use Idempotency for operations that must not happen twice even if retried (e.g., Payments, Vehicle Registrations).

**Action**: Install the `Idempotency` plugin at the route level.
*   **Target**: `POST` and `PATCH` routes that modify state.

#### **Example A: Accounting Module (`AccountingRoutes.kt`) - CRITICAL**
Invoices and Payments are the most important places to apply idempotency to prevent double-billing.

**CRITICAL**: `install(Idempotency)` must be outside the `post { ... }` block but inside the `route` block.
```kotlin
route("/v1/accounting/invoices") {
    
    route("/{id}/pay") {
        // Correct placement: Outside the post handler
        install(Idempotency)
        
        post {
            // ... payment recording logic ...
        }
    }
}
```

#### **Example B: Vehicle Module (`VehicleRoutes.kt`)**
```kotlin
route("/v1/vehicles") {
    withRoles(UserRole.ADMIN, UserRole.FLEET_MANAGER) {
        route("/register") { // Create a specific route if you want idempotency just for POST
            install(Idempotency)
            post {
                val request = call.receive<VehicleRequest>()
                // ... registration logic ...
            }
        }
    }
}
```
*   **Requirement**: Ensure the client sends a unique UUID in the `Idempotency-Key` header.

### 3. Step-By-Step: Applying Pagination
Updating list endpoints to handle large datasets safely.

**Action A**: Update the Repository and Use Case.
*   **Files**: `VehicleRepository.kt`, `VehicleRepositoryImpl.kt` & `ListVehiclesUseCase.kt`

```kotlin
// 1. Repository Interface (VehicleRepository.kt)
suspend fun findAll(params: PaginationParams): Pair<List<Vehicle>, Long>

// 2. Repository Implementation (VehicleRepositoryImpl.kt)
override suspend fun findAll(params: PaginationParams): Pair<List<Vehicle>, Long> = dbQuery {
    val totalCount = VehiclesTable.selectAll().count()
    
    var query = VehiclesTable.selectAll()
    
    // Applying Cursor Filter if provided
    params.cursor?.let {
        val lastId = UUID.fromString(it)
        query = query.where { VehiclesTable.id greater lastId }
    }
    
    val items = query
        .orderBy(VehiclesTable.id to SortOrder.ASC) // Stable Sort required
        .limit(params.limit)
        .map { it.toVehicle() }
        
    Pair(items, totalCount)
}

// 3. Use Case (ListVehiclesUseCase.kt)
suspend fun execute(params: PaginationParams): PaginatedResponse<VehicleResponse> {
    val (vehicles, total) = repository.findAll(params)
    val items = vehicles.map { VehicleResponse.fromDomain(it) }
    
    // Cursor is the ID of the last item in the list
    val nextCursor = items.lastOrNull()?.id 
    
    return PaginatedResponse(
        items = items,
        nextCursor = if (items.size >= params.limit) nextCursor else null,
        limit = params.limit,
        total = total
    )
}
```

**Action B**: Update the Route handler.
*   **File**: `VehicleRoutes.kt`
    ```kotlin
    get {
        val params = call.paginationParams() // Extracts limit and cursor
        val result = listVehiclesUseCase.execute(params)
        call.respond(ApiResponse.success(result, call.requestId))
    }
    ```

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

---

## 🛠️ How to Test Roles in Postman

To test RBAC (Role-Based Access Control) in Postman, you don't add the "role" as a separate header. Instead, roles are **embedded inside the JWT Token** itself.

### **Step 1: Obtain a Token**
Login using the login endpoint (e.g., `POST /v1/users/login`). The server will generate a token for you based on your user profile in the database.

### **Step 2: Inspect your Token (Optional)**
You can verify if your token contains the correct roles by pasting it into [jwt.io](https://jwt.io/). Look for the payload section:
```json
{
  "id": "user-uuid",
  "email": "admin@solodev.com",
  "roles": ["ADMIN", "FLEET_MANAGER"],  // <--- These are the roles used by 'withRoles'
  "exp": 1707684000
}
```

### **Step 3: Configure Postman Authorization**
For any protected endpoint (like `POST /v1/vehicles`):
1.  Open the request in Postman.
2.  Click on the **Authorization** tab.
3.  Select **Type**: `Bearer Token`.
4.  Paste your token into the **Token** field.
5.  Click **Send**.

### **Step 4: Verify Scenarios**

To understand the "Sense" of Idempotency, test these three specific scenarios:

#### **Scenario A: The "Invisible" Success (Network Glitch Simulation)**
*   **Goal**: Prove the client gets a success even if they "think" it failed.
1.  Send a `POST` to `/invoices/{id}/pay` with `Idempotency-Key: pay_001`.
2.  Receive `200 OK`.
3.  Send the **exact same** request again with the **same** key.
4.  **Verification**: You get `200 OK` again. The server **did not** return "Invoice already paid" or an error. It served the cached success, making the retry seamless for the client.

#### **Scenario B: The "Double Click" Shield (409 Conflict)**
*   **Goal**: Prove that two simultaneous requests don't overlap.
1.  This is hard to do manually, but if you click "Send" twice extremely fast (or use a script).
2.  **Verification**: One request will succeed (`200 OK`), and the other will immediately return `409 Conflict`. This proves the "Lock" is working to prevent the same logic from running twice at the same time.

#### **Scenario C: Key Misuse (Changing the Body)**
*   **Goal**: Prove the key is the absolute source of truth.
1.  Send a request with `Idempotency-Key: key_abc` and `amount: 100`. 
2.  Get `200 OK`.
3.  Keep the key `key_abc` but change the body to `amount: 5000`.
4.  **Verification**: You will still get the old `200 OK` (with the response for 100). The server **ignored** your new input because the key told it: *"I already did this work, here is the result."* 

---

## 📱 Mobile / Client Integration (Android/iOS)

When integrating these hardened endpoints into a mobile app, follow these patterns:

### **1. Generating the Idempotency-Key (Android/Kotlin)**
Always generate a fresh UUID for every new "Action" (like a button tap).
```kotlin
// Inside your ViewModel or Repository
val idempotencyKey = java.util.UUID.randomUUID().toString()

// Add to your Retrofit/Ktor-Client headers:
// .header("Idempotency-Key", idempotencyKey)
```

### **2. Handling Retries on Mobile**
If a mobile request fails due to a `SocketTimeoutException` or `NoRouteToHostException`:
1.  **Do NOT** generate a new UUID.
2.  **REUSE** the same UUID from the first attempt.
3.  This ensures that if the first request actually reached the server but the response was dropped, the second attempt will safely return the cached success.

### **3. Ktor Client Example (Android/Mobile)**
If you are using the Ktor Client on Android, you can either pass it manually or use a simple `DefaultRequest` plugin.

**Manual usage per call:**
```kotlin
suspend fun submitPayment(invoiceId: String, amount: Long) {
    // 1. Generate the key ONCE for this transaction
    val transactionId = java.util.UUID.randomUUID().toString()

    client.post("https://api.v1/accounting/invoices/$invoiceId/pay") {
        contentType(ContentType.Application.Json)
        // 2. Attach the key to the header
        header("Idempotency-Key", transactionId)
        setBody(PaymentRequest(amount = amount))
    }
}
```

**Why generating it "just once" matters:**
If the request times out, you call `submitPayment` again. By keeping the **same** `transactionId`, the server knows it's the same attempt. If you generated a *new* UUID on every retry, you'd risk paying twice if the first request actually reached the server but the response was blocked by a weak mobile signal!

### **4. Full Android Clean Architecture Example (Koin + Ktor + MVVM)**
If you are using **MVVM**, **Clean Architecture**, and **Koin**, follow this flow for the `Pay Invoice` feature:

#### **A. The ViewModel (Presentation)**
The ViewModel is the best place to generate the key because it outlives simple screen rotations.
```kotlin
class InvoiceViewModel(private val payInvoiceUseCase: PayInvoiceUseCase) : ViewModel() {
    
    fun processPayment(invoiceId: String, amount: Long) {
        // 1. Generate the key ONCE at the start of the user action
        val idempotencyKey = UUID.randomUUID().toString()
        
        viewModelScope.launch {
            val result = payInvoiceUseCase.execute(idempotencyKey, invoiceId, amount)
            // handle success/failure
        }
    }
}
```

#### **B. The Use Case (Domain)**
```kotlin
class PayInvoiceUseCase(private val repository: InvoiceRepository) {
    suspend fun execute(key: String, id: String, amount: Long) = 
        repository.pay(key, id, amount)
}
```

#### **C. The Repository Implementation (Data/Infrastructure)**
Using Ktor Client with your Koin-injected `HttpClient`.
```kotlin
class InvoiceRepositoryImpl(private val client: HttpClient) : InvoiceRepository {
    override suspend fun pay(key: String, id: String, amount: Long) {
        client.post("https://api.v1/accounting/invoices/$id/pay") {
            header("Idempotency-Key", key) // Pass the key generated in the VM
            setBody(PaymentRequest(amount))
        }
    }
}
```

#### **D. Koin Module (DI)**
```kotlin
val accountingModule = module {
    single { HttpClient(Android) { install(ContentNegotiation) { json() } } }
    single<InvoiceRepository> { InvoiceRepositoryImpl(get()) }
    factory { PayInvoiceUseCase(get()) }
    viewModel { InvoiceViewModel(get()) }
}
```

**Why this works**: By following this flow, the **Idempotency-Key** is treated as part of the "Command" data. It is consistently passed from the user's intent (ViewModel) down to the actual execution (Ktor), ensuring it stays identical even if the Repository triggers an automatic retry for network issues.

---

## 🧪 Expected Behavior (Detailed API Outputs)
