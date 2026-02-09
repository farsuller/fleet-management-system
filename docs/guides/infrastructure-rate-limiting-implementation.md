# Infrastructure: API Rate Limiting & Abuse Protection Guide

**Status**: PROPOSED / PRODUCTION-READY  
**Version**: 1.1  
**Last Updated**: 2026-02-07  
**Objective**: Protect the Fleet Management API from DDoS attacks, brute-force attempts, and resource exhaustion, aligning with OWASP API4:2023 Resource Consumption.

---

## 1. Overview

Rate limiting is a critical security layer that controls the frequency of requests to the API. Following the principles in `@[skills/api-patterns/rate-limiting.md]`, we implement protection at multiple levels to ensure system availability and fairness.

### Goals:
- **DDoS Mitigation**: Prevent high-volume traffic from crashing the service.
- **Brute-Force Protection**: Limit attempts on sensitive endpoints (Login, Register).
- **Resource Fairness**: Ensure API resources are available to all users.
- **Cost Control**: Manage infrastructure load and related costs.

---

## 2. Strategy Selection

Based on the `@[skills/api-patterns/rate-limiting.md]`, we have selected the **Token Bucket** strategy for the Fleet Management System.

| Strategy | Why we use it |
|----------|---------------|
| **Token Bucket** | Allows for **traffic bursts** (e.g., loading a dashboard with many sub-requests) while maintaining a strict average rate over time. Ideal for modern RESTful APIs. |

---

## 3. Technology: Ktor Rate Limit Plugin

We use the official [Ktor Rate Limit](https://ktor.io/docs/rate-limit.html) plugin.

### Essential Dependencies:
```kotlin
implementation("io.ktor:ktor-server-rate-limit:$ktor_version")
```

---

## 4. Configuration & Registration

### **Why This Matters**:
We define multiple "Buckets" of rate limits. This tiered approach allows us to be generous with trusted, logged-in users while being extremely strict with anonymous traffic and sensitive endpoints like Login/Register.

### **RateLimiting.kt**
`src/main/kotlin/com/solodev/fleet/shared/plugins/RateLimiting.kt`

```kotlin
fun Application.configureRateLimiting() {
    install(RateLimit) {
        // 1. Default Limit: Acts as a final safety net.
        // Used by calling rateLimit {} in Routing.
        register {
            rateLimiter(limit = 5, refillPeriod = 60.seconds)
        }

        // 2. Public API: IP-based limit for guest users.
        register(RateLimitName("public_api")) {
            rateLimiter(limit = 100, refillPeriod = 1.minutes)
            requestKey { call -> call.request.origin.remoteHost }
        }

        // 3. Sensitive Endpoints: Strict limits for Login/Register.
        register(RateLimitName("auth_strict")) {
            rateLimiter(limit = 5, refillPeriod = 1.minutes)
            requestKey { call -> call.request.origin.remoteHost }
        }

        // 4. Authenticated API: User-based limiting.
        register(RateLimitName("authenticated_api")) {
            rateLimiter(limit = 500, refillPeriod = 1.minutes)
            requestKey { call -> 
                call.principal<JWTPrincipal>()?.payload?.getClaim("id")?.asString() 
                    ?: call.request.origin.remoteHost
            }
        }
    }
}
```

---

## 5. Standardizing the 429 Response

### **Bridge Logic**:
To ensure all rate limits (manual or automatic) return the exact same JSON format, we use `StatusPages` to bridge the HTTP 429 status to our internal `RateLimitException`.

### **StatusPages.kt Configuration**
`src/main/kotlin/com/solodev/fleet/shared/plugins/StatusPages.kt`

```kotlin
install(StatusPages) {
    // 1. Bridge Ktor's automatic 429 status to our Domain Exception
    status(HttpStatusCode.TooManyRequests) { call, _ ->
        val retryAfter = call.response.headers["Retry-After"]
        throw RateLimitException("Too many requests. Please wait $retryAfter seconds.")
    }

    // 2. The Single Source of Truth for the JSON output
    exception<RateLimitException> { call, cause ->
        call.respond(
            HttpStatusCode.TooManyRequests,
            ApiResponse.error(
                code = cause.errorCode,
                message = cause.message ?: "Rate limit exceeded",
                requestId = call.requestId
            )
        )
    }
}
```

---

## 6. Applying Limits to Routes

Defining the buckets is just the registration phase. You must wrap your routes in `Routing.kt` to activate the protection.

### **Routing.kt Implementation**
`src/main/kotlin/com/solodev/fleet/Routing.kt`

```kotlin
routing {
    // 1. Apply Default Tier (5/min)
    rateLimit {
        get("/") { ... }
    }

    // 2. Apply Public API Tier (100/min)
    rateLimit(RateLimitName("public_api")) {
        // vehicleRoutes(vehicleRepo)
        // customerRoutes(customerRepo)
    }

    // 3. Apply Strict Auth Tier (5/min)
    rateLimit(RateLimitName("auth_strict")) {
        // userRoutes(userRepository = userRepo, ...)
    }

    // 4. Apply Authenticated Tier (500/min)
    // Always wrap inside the authenticate block for correct User-ID tracking
    authenticate("auth-jwt") {
        rateLimit(RateLimitName("authenticated_api")) {
            // accountingRoutes(...)
        }
    }
}
```

---

## 7. Bypass Protection

### **Why This Matters**:
Basic rate limiting is easy to circumvent. We implement these "Gotchas" to ensure the protection is meaningful in a production environment.

1.  **Header Trust**: **Gotcha:** If you use a load balancer, `remoteHost` might always be the balancer's IP. You MUST configure Ktor to trust `X-Forwarded-For` from known proxies.
2.  **Scope Variety**: By switching from IP to User ID as soon as a user logs in, we stop botnets that rotate through many residential IPs.
3.  **Endpoint Shadowing**: We apply limits to route groups to stop attackers from trying variations like `/v1/users` and `/v1/users/` to reset counts.

---

## 8. Summary Table of Recommended Limits

| Usage Class | Scope | Limit | Refill | OWASP Focus |
|-------------|-------|-------|--------|-------------|
| **Default/Global** | IP | 5 | 1 min | API4 (Basic) |
| **Public API** | IP | 100 | 1 min | API4 (Bulk Scraping) |
| **Authenticated** | User ID | 500 | 1 min | API4 (Advanced) |
| **Authentication** | IP | 5 | 1 min | API2 (Brute-force) |

---

## 9. Security Testing Checklist

- [x] **Consitency**: Verify the 429 response is JSON and matches `ApiResponse`.
- [x] **Bridge**: Verify that both the plugin and manual `throw` use the same format.
- [x] **Headers**: Verify `X-RateLimit-Remaining` is present in responses.
- [x] **Scope**: Verify that logged-in users are limited by ID, not just IP.

---

## 10. Testing & Verification

To verify the implementation, you can use `curl` with the `-i` flag to inspect both the headers and the body.

### **Step 1: Successful Request (Under Limit)**
Hit the root endpoint (Default limit: 5/min).

**Command**:
```bash
curl -i http://localhost:8080/
```

**Output (Before Limit)**:
```http
HTTP/1.1 200 OK
Content-Type: application/json
X-RateLimit-Remaining: 4
...

{
  "success": true,
  "data": {
    "message": "Fleet Management API v1"
  },
  "requestId": "req_8e23..."
}
```

### **Step 2: Rate Limited (Limit Exceeded)**
Repeat the command 5 more times quickly.

**Command**:
```bash
curl -i http://localhost:8080/
```

**Output (After Limit)**:
```http
HTTP/1.1 429 Too Many Requests
Content-Type: application/json
Retry-After: 58
...

{
  "success": false,
  "error": {
    "code": "RATE_LIMITED",
    "message": "Too many requests. Please wait 58 seconds."
  },
  "requestId": "req_f2a1..."
}
```
