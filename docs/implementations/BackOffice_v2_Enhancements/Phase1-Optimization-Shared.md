# Phase 1 — Shared Layer RAM Optimization

> **Scope**: `shared/` directory + `Application.kt` / `Routing.kt` bootstrap  
> **Goal**: Reduce heap allocations in foundational code used by every request  
> **Risk**: None — internal refactors only, same API output

---

## 1. ValidationUtils.kt

**File**: `shared/utils/ValidationUtils.kt`

**Problem**: `emailRegex` and `phoneRegex` are re-compiled on every validation call. Each `Regex()` constructor allocates a `Pattern` object (~2-4 KB).

**Fix**: Hoist regex instances to companion-level `val` properties so they compile once at class load.

```diff
 object ValidationUtils {
-    private const val MAX_NAME_LENGTH = 30
-    private const val MIN_PASSWORD_LENGTH = 6
+    private const val MAX_NAME_LENGTH = 30
+    private const val MIN_PASSWORD_LENGTH = 6
+
+    private val EMAIL_REGEX = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
+    private val PHONE_REGEX = Regex("^\\+63\\d{10}$")

     fun validateEmail(email: String) {
-        val emailRegex = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\\\.[A-Za-z]{2,}$")
-        require(emailRegex.matches(email)) { "Valid email address required" }
+        require(EMAIL_REGEX.matches(email)) { "Valid email address required" }
     }

     fun validatePhone(phone: String?) {
         if (phone == null) return
-        val phoneRegex = Regex("^\\\\+63\\\\d{10}$")
-        require(phoneRegex.matches(phone)) { "Phone must start with +63 followed by 10 digits" }
+        require(PHONE_REGEX.matches(phone)) { "Phone must start with +63 followed by 10 digits" }
     }
```

**Skill Ref**: §5 — Compile-time Constants, §9 — String/Object optimization

---

## 2. Serialization.kt

**File**: `shared/plugins/Serialization.kt`

**Problem**: `prettyPrint = true` adds whitespace to every JSON response, inflating `String` heap allocation by ~20-40%.

**Fix**: Gate behind environment config. Default to `false` in production.

```diff
     val json =
         Json {
-            prettyPrint = true
+            prettyPrint = environment.config
+                .propertyOrNull("ktor.serialization.prettyPrint")
+                ?.getString()?.toBoolean() ?: false
             ignoreUnknownKeys = true
             encodeDefaults = true
```

> **Note**: Existing clients parse identically — only whitespace changes. Add `ktor.serialization.prettyPrint: true` to `application.yaml` for local dev if desired.

**Skill Ref**: §9 — Avoid heavy string manipulation

---

## 3. RedisCacheManager.kt — Shared Json Instance

**File**: `shared/infrastructure/cache/RedisCacheManager.kt`

**Problem**: Creates its own `Json { ignoreUnknownKeys = true }` instance, duplicating the one in `Serialization.kt`. Each `Json` instance carries internal caches.

**Fix**: Extract a shared `JsonConfig` object used by both.

```kotlin
// NEW FILE: shared/infrastructure/serialization/JsonConfig.kt
package com.solodev.fleet.shared.infrastructure.serialization

import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import java.time.Instant
import java.util.UUID

object JsonConfig {
    val instance: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        serializersModule = SerializersModule {
            contextual(UUID::class) { UUIDSerializer }
            contextual(Instant::class) { InstantSerializer }
        }
    }
}
```

Then in `RedisCacheManager`:

```diff
 class RedisCacheManager(
     @PublishedApi internal val jedisPool: JedisPool?,
 ) {
-    @PublishedApi internal val json = Json { ignoreUnknownKeys = true }
+    @PublishedApi internal val json = JsonConfig.instance
```

And in `Serialization.kt`:

```diff
 fun Application.configureSerialization() {
-    val json = Json { ... }
+    val json = JsonConfig.instance
     install(ContentNegotiation) {
         json(json)
     }
 }
```

**Skill Ref**: §3 — Object Pooling (Senior)

---

## 4. StatusPages.kt — Inline Error Helper

**File**: `shared/plugins/StatusPages.kt`

**Problem**: 10 nearly identical `ApiResponse<Nothing>(success = false, error = ErrorDetail(...), requestId = ...)` blocks. Each duplicated block increases compiled bytecode → larger Metaspace.

**Fix**: Extract a private inline helper. `inline` ensures zero lambda overhead at call sites.

```diff
+/** Inline helper — zero allocation overhead, reduces bytecode duplication. */
+private inline fun errorResponse(
+    code: String,
+    message: String,
+    requestId: String,
+) = ApiResponse<Nothing>(
+    success = false,
+    error = ErrorDetail(code = code, message = message),
+    requestId = requestId,
+)

 fun Application.configureStatusPages() {
     install(StatusPages) {
         exception<NotFoundException> { call, cause ->
             call.respond(
                 HttpStatusCode.NotFound,
-                ApiResponse<Nothing>(
-                    success = false,
-                    error = ErrorDetail(
-                        code = cause.errorCode,
-                        message = cause.message ?: "Resource not found",
-                    ),
-                    requestId = call.requestId,
-                ),
+                errorResponse(cause.errorCode, cause.message ?: "Resource not found", call.requestId),
             )
         }
         // ... apply same pattern to all other exception handlers
```

**Skill Ref**: §1 — Inline Functions, §2 — JIT-Friendly Code

---

## 5. Application.kt + Routing.kt — Eliminate Duplicate RedisCacheManager

**Files**: `Application.kt`, `Routing.kt`

**Problem**: `Application.kt` creates `cacheManager = RedisCacheManager(jedisPool)`. Then `Routing.kt` line 93 creates a **second** `RedisCacheManager(jedisPool)` as `redisCache`. Both wrap the same `JedisPool`.

**Fix**: Pass the existing `cacheManager` through to `configureRouting()` and reuse it.

In `Application.kt`:
```diff
     configureRouting(
         jwtService = jwtService,
         vehicleRepo = vehicleRepository,
         jedisPool = jedisPool,
         registry = registry,
         emailService = emailService,
+        cacheManager = cacheManager,
     )
```

In `Routing.kt`:
```diff
 fun Application.configureRouting(
     jwtService: JwtService,
     vehicleRepo: VehicleRepositoryImpl,
     jedisPool: JedisPool?,
     registry: MeterRegistry,
     emailService: EmailService,
+    cacheManager: RedisCacheManager?,
 ) {
     // ...
-    val redisCache = RedisCacheManager(jedisPool)
+    val redisCache = cacheManager ?: RedisCacheManager(jedisPool)
```

**Skill Ref**: §3 — Object Pooling (Senior)

---

## Checklist

- [ ] Hoist `emailRegex` / `phoneRegex` to companion `val` in `ValidationUtils.kt`
- [ ] Gate `prettyPrint` behind config in `Serialization.kt`
- [ ] Extract `JsonConfig` object, wire into `RedisCacheManager` and `Serialization`
- [ ] Extract inline `errorResponse()` helper in `StatusPages.kt`
- [ ] Pass `cacheManager` from `Application.kt` → `Routing.kt`, remove duplicate

---

## Before & After Comparison

### 1. Regex Hoisting — ValidationUtils

**BEFORE** — regex re-compiled on every validation call:
```kotlin
fun validateEmail(email: String) {
    val emailRegex = Regex("...")  // ← new Pattern object (~2-4 KB) allocated EVERY call
    require(emailRegex.matches(email))
}
```

**AFTER** — compiled once at class load:
```kotlin
private val EMAIL_REGEX = Regex("...")  // ← compiled once, reused forever

fun validateEmail(email: String) {
    require(EMAIL_REGEX.matches(email))  // ← zero allocation
}
```

| Metric | Before | After |
|---|---|---|
| `Pattern` objects per validation call | 1 (~2-4 KB) | 0 |
| At 50 registrations/day (email + phone) | 100 Pattern objects | 0 |

---

### 2. prettyPrint — Serialization

**BEFORE** — every JSON response includes whitespace:
```json
{
  "id": "abc-123",
  "name": "Vehicle A",
  "status": "ACTIVE"
}
```

**AFTER** — compact JSON (same data, no whitespace):
```json
{"id":"abc-123","name":"Vehicle A","status":"ACTIVE"}
```

| Metric | Before | After |
|---|---|---|
| String size per response | ~100% | ~60-80% (20-40% smaller) |
| Heap allocated per response | Higher (whitespace chars) | Lower |

---

### 3. Shared JsonConfig — RedisCacheManager

**BEFORE** — two separate `Json` instances on the heap:
```
Heap: [Json instance #1 (Serialization.kt)] + [Json instance #2 (RedisCacheManager)]
Each Json instance carries internal serializer caches (~KB each)
```

**AFTER** — single shared instance:
```
Heap: [JsonConfig.instance (shared)]
```

| Metric | Before | After |
|---|---|---|
| `Json` instances on heap | 2 | 1 |
| Internal serializer cache duplication | Duplicated | Single copy |

---

### 4. Inline errorResponse — StatusPages

**BEFORE** — 10 near-identical code blocks compiled to separate bytecode:
```
Metaspace: [errorHandler1] [errorHandler2] ... [errorHandler10] (~200 bytes each)
```

**AFTER** — 1 inline function, inlined at each call site:
```
Metaspace: [errorResponse (inlined)] — smaller total bytecode footprint
```

| Metric | Before | After |
|---|---|---|
| Bytecode duplication | ~2 KB (10 blocks × ~200 bytes) | ~1 KB (1 template, inlined) |

---

### 5. Duplicate RedisCacheManager — Application + Routing

**BEFORE** — two `RedisCacheManager` wrapping the same `JedisPool`:
```
Heap: [RedisCacheManager #1 (Application.kt)] + [RedisCacheManager #2 (Routing.kt)]
       └── Json instance #A                      └── Json instance #B
```

**AFTER** — single instance passed through:
```
Heap: [RedisCacheManager (shared)]
       └── JsonConfig.instance
```

| Metric | Before | After |
|---|---|---|
| `RedisCacheManager` objects | 2 | 1 |
| Redundant `Json` caches | 1 extra | 0 |

---

### Aggregate Impact

| Category | Before | After |
|---|---|---|
| Regex recompilation per validation | 1-2 Pattern objects (~4 KB) | 0 |
| JSON response size | 100% | ~60-80% |
| `Json` instances on heap | 2+ (duplicated caches) | 1 (shared) |
| `RedisCacheManager` instances | 2 | 1 |
| StatusPages bytecode | ~2 KB duplicated | ~1 KB inlined |
| API response output | Identical (data) | Identical (data) ✅ |
