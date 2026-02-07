# Infrastructure: Production-Grade JWT Authentication Guide

**Status**: PROPOSED / PRODUCTION-READY  
**Version**: 1.1  
**Last Updated**: 2026-02-07  
**Objective**: Replace mock tokens with a secured, verifiable, and industry-standard JWT implementation following OWASP API Security best practices.

---

## 1. Overview

In a production environment, authentication must be stateless, secure, and verifiable. This guide details the implementation of **JWT (JSON Web Tokens)** using Ktor's auth plugin, following "Zero Trust" security principles and recommended patterns from `@[skills/api-patterns]`.

### Key Security Features:
- **Asymmetric Signature**: Using `HS256` (HMAC with SHA-256) or `RS256`.
- **Password Hashing**: Utilizing **BCrypt** instead of plaintext or mock hashing.
- **Stateless Verification**: The server verifies tokens without database lookups for every request.
- **Claim-Based RBAC**: Roles are embedded in the token for instant permission checks.

---

## 2. Configuration (`application.conf`)

Never hardcode secrets. Use environment variables defined in your configuration file.

```hocon
jwt {
    domain = "https://api.fleet-management.com"
    audience = "fleet-management-api"
    realm = "Fleet Management Access"
    issuer = "fleet-management-auth"
    # Read from environment variable
    secret = ${JWT_SECRET} 
    # Expiration: Recommended short expiry (e.g., 1 hour)
    expiresIn = 3600000 
}
```

---

## 3. Password Hashing Utility

### **Why This Matters**:
Plaintext passwords are a massive liability. We use **BCrypt** (a Blowfish-based adaptive hashing function) because it is computationally expensive, making brute-force and rainbow table attacks significantly harder.

### **PasswordHasher.kt**
`src/main/kotlin/com/solodev/fleet/shared/utils/PasswordHasher.kt`

```kotlin
import at.favre.lib.crypto.bcrypt.BCrypt

/**
 * Utility for secure password management.
 * Uses BCrypt with a default cost factor (12) to balance security and performance.
 */
object PasswordHasher {
    private val hasher = BCrypt.withDefaults()
    private val verifier = BCrypt.verifyer()

    /**
     * Hashes a raw password using BCrypt.
     * @param password The plaintext password provided by the user.
     * @return A secure, salted hash string.
     */
    fun hash(password: String): String {
        return hasher.hashToString(12, password.toCharArray())
    }

    /**
     * Verifies a raw password against an existing hash.
     * @param password The plaintext password to verify.
     * @param hash The stored hash retrieved from the database.
     * @return True if the password matches the hash.
     */
    fun verify(password: String, hash: String): Boolean {
        // Essential: Standardize on char arrays to prevent password persistence in memory strings
        return verifier.verify(password.toCharArray(), hash).verified
    }
}
```

---

## 4. Token Service Implementation

### **Why This Matters**:
The `JwtService` creates the "Passport" for the user. By embedding **Claims** (like `id` and `roles`), we allow the API to verify the user's identity and permissions without querying the database on every single request, significantly improving performance.

### **JwtService.kt**
`src/main/kotlin/com/solodev/fleet/shared/utils/JwtService.kt`

```kotlin
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.solodev.fleet.modules.domain.models.User
import java.util.*

class JwtService(
    private val secret: String,
    private val issuer: String,
    private val audience: String,
    private val expiresInMs: Long
) {
    // HS256: Symmetric signature requiring a shared secret between all nodes
    private val algorithm = Algorithm.HMAC256(secret)

    /**
     * Issues a new JWT for an authenticated user.
     */
    fun generateToken(user: User): String {
        return JWT.create()
            .withSubject("Authentication")
            .withIssuer(issuer)
            .withAudience(audience)
            .withClaim("id", user.id.value) // Primary identifier
            .withClaim("email", user.email)
            // RBAC Foundation: Roles are included in the token for instant AuthZ checks
            .withArrayClaim("roles", user.roles.map { it.name }.toTypedArray())
            .withIssuedAt(Date())
            // Expiry prevents "forever tokens" if one is leaked
            .withExpiresAt(Date(System.currentTimeMillis() + expiresInMs))
            .sign(algorithm)
    }
}
```

---

## 5. Ktor Security Configuration

### **Why This Matters**:
This configuration acts as the "Gatekeeper". It initializes the Ktor Authentication plugin, defining how the server should verify the incoming `Authorization: Bearer` token.

### **Security.kt**
`src/main/kotlin/com/solodev/fleet/shared/plugins/Security.kt`

```kotlin
/**
 * Hooks into Ktor's pipeline to intercept requests and verify JWTs.
 */
fun Application.configureSecurity(jwtService: JwtService) {
    val jwtSecret = environment.config.property("jwt.secret").getString()
    val jwtIssuer = environment.config.property("jwt.issuer").getString()
    val jwtAudience = environment.config.property("jwt.audience").getString()

    install(Authentication) {
        jwt("auth-jwt") {
            realm = environment.config.property("jwt.realm").getString()
            verifier(
                // Strict Verification: Checks Algorithm, Secret, Issuer, and Audience
                JWT.require(Algorithm.HMAC256(jwtSecret))
                    .withIssuer(jwtIssuer)
                    .withAudience(jwtAudience)
                    .build()
            )
            validate { credential ->
                // Validation Step: Verify the 'id' claim is not malformed
                if (credential.payload.getClaim("id").asString().isNotEmpty()) {
                    JWTPrincipal(credential.payload)
                } else {
                    null // Rejects the token if the expected claim is missing
                }
            }
        }
    }
}
```

---

## 6. Updated Login Use Case

### **Why This Matters**:
The Use Case orchestrates the "Trust Handshake". It verify the user's existence, checks their credentials securely, and then issues the JWT "Passport".

### **LoginUserUseCase.kt**
```kotlin
class LoginUserUseCase(
    private val repository: UserRepository,
    private val jwtService: JwtService
) {
    /**
     * Orchestrates the authentication flow.
     * @return A Pair containing the User domain model and their fresh JWT.
     * @throws AuthenticationException with generic message to prevent user enumeration.
     */
    suspend fun execute(request: LoginRequest): Pair<User, String> {
        val user = repository.findByEmail(request.email)
            ?: throw AuthenticationException("Invalid credentials")

        // Secure check against hashed password
        if (!PasswordHasher.verify(request.passwordRaw, user.passwordHash)) {
            // Gotcha: Use same error message to prevent probing for existing emails
            throw AuthenticationException("Invalid credentials")
        }

        val token = jwtService.generateToken(user)
        return Pair(user, token)
    }
}
```

---

## 7. Headers & Token Usage

### **Authorization Header Pattern**
For all protected endpoints, the JWT token **MUST** be sent in the `Authorization` header using the `Bearer` scheme.

**Standard Format:**
```http
Authorization: Bearer <JWT_TOKEN>
```

### **Client-Side Storage**
- **Web**: Store tokens in `httpOnly`, `Secure`, `SameSite=Strict` cookies to prevent XSS-based theft. Avoid `localStorage`.
- **Mobile**: Store in secure storage (iOS Keychain, Android EncryptedSharedPreferences).

---

## 8. Protecting Routes & RBAC

Use the `authenticate` wrapper to enforce JWT verification and perform authorization checks.

### **UserRoutes.kt**
```kotlin
routing {
    route("/v1/users") {
        // Public routes
        post("/login") { ... }
        post("/register") { ... }

        // Protected routes
        authenticate("auth-jwt") {
            get("/me") {
                val principal = call.principal<JWTPrincipal>()
                val userIdFromToken = principal?.payload?.getClaim("id")?.asString()
                // Implementation of AuthZ check (Peer check)
                // ... logic
            }
            
            // RBAC Example: Only Admin can delete
            delete("/{id}") {
                val roles = call.principal<JWTPrincipal>()?.payload?.getClaim("roles")?.asList(String::class.java)
                if (roles?.contains("ADMIN") == false) {
                    call.respond(HttpStatusCode.Forbidden)
                    return@delete
                }
                // Implementation of AuthZ check (Context check)
                // ... logic
            }
        }
    }
}
```

---

## 9. Security Testing & OWASP Alignment

Based on the `@[skills/api-patterns/security-testing.md]`, ensure the following checks:

### **Authentication (API2)**
| Test Area | Focus |
|-----------|-------|
| **Algorithm** | Ensure `none` algorithm is rejected. |
| **Secret** | Verify the system uses a high-entropy secret (min 64 chars). |
| **Expiration** | Verify tokens expire as expected and are rejected after expiry. |
| **Signature** | Attempt to modify the payload and verify the signature check fails. |

### **Authorization (API1, API5)**
| Test Area | Focus |
|-----------|-------|
| **BOLA/IDOR** | Attempt to access `/v1/users/{id}` for a different user ID using a valid token. |
| **Vertical Esc.** | Attempt to call a `delete` endpoint with a `CUSTOMER` role token. |
| **Context** | Ensure users can only modify their own related resources (e.g., their own rentals). |

---

## 10. Production Check-List

1.  **Secret Rotation**: Use a key vault (AWS Secrets Manager, HashiCorp Vault).
2.  **HTTPS Only**: Never send tokens over HTTP.
3.  **Short Expiration**: Set access tokens to expire quickly (15m - 1h).
4.  **Audience Validation**: Always verify the `aud` claim.
5.  **Claim Minimization**: Do not put PII (Personally Identifiable Information) in the token payload.
6.  **Refresh Tokens**: Implement a refresh token flow for long-lived sessions to avoid long-lived access tokens.

---

## 11. Testing with cURL

```bash
# 1. Login to get real token
curl -X POST http://localhost:8080/v1/users/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@fleet.com", "passwordRaw":"secure_pass"}'

# 2. Extract token from response and use it in header
curl -X GET http://localhost:8080/v1/users/me \
  -H "Authorization: Bearer <your_token_here>"
```
