# Fleet Management API - Implementation Standards

**Version**: 1.1  
**Last Updated**: 2026-02-07  
**Applies To**: All System Modules & API Implementations  
**Compliance**: 100% Mandatory for Production Readiness

---

## 1. Overview

This document defines the **mandatory standards** for all implementations in the Fleet Management System. These standards ensure high quality, security, performance, and testability across the entire codebase.

### **Integrated Skills & Governance**
- **Clean Code** - Conciseness, directness, and pragmatic maintainability.
- **API Patterns** - Consistent, secure, and performant REST/gRPC interfaces.
- **Performance Optimizer** - Efficiency first, low latency, and resource consciousness.
- **Test Engineer** - Comprehensive, behavior-focused, and systematic verification.
- **Security Hardening** - Zero-trust, secure defaults, and vulnerability prevention.

---

## 2. Clean Code Principles

### **Pragmatic Standards**
1.  **Small Functions**: Max 20 lines. Ideally 5-10. If it's longer, split it.
2.  **Naming Rules**: Reveal intent. If you need a comment to explain a name, rename it.
    -   `isActive`, `hasPermission` (Booleans)
    -   `getUserById()`, `calculateTotal()` (Verbs + Nouns)
    -   `MAX_RETRY_COUNT` (Constants)
3.  **Guard Clauses**: Use early returns for edge cases and errors to keep the "happy path" flat.
4.  **No Magic Values**: Use named constants or enums.
5.  **SRP (Single Responsibility)**: A class/function should do **one thing** and do it well.

---

## 3. API Design Patterns

### **REST Conventions**
-   **Resource-Oriented**: `/v1/vehicles`, not `/v1/getVehicles`.
-   **Standard Status Codes**:
    -   `200 OK` (Read/Update) | `201 Created` (Success creation) | `204 No Content` (Delete)
    -   `400 Bad Request` | `401 Unauthorized` | `403 Forbidden` | `404 Not Found`
    -   `409 Conflict` (State clash) | `422 Unprocessable Entity` (Validation fail)
    -   `429 Too Many Requests` (Rate limit) | `500 Server Error`
-   **Consistent Response Wrapper**: Every payload must use the `ApiResponse` envelope.

### **Version Management**
-   All APIs MUST be versioned at the root (e.g., `/v1/...`).

---

## 4. Performance Standards

### **Backend Efficiency**
1.  **Latency Targets**:
    -   Simple GET/POST: `< 100ms` (P95)
    -   Complex Calculations: `< 300ms` (P95)
2.  **Database Optimization**:
    -   **No N+1 Queries**: Use eager loading/joins.
    -   **Indexing**: Critical query fields (IDs, FKs, frequently filtered fields) MUST be indexed.
    -   **Connection Pooling**: Always use a managed pool.
3.  **Resource Consciousness**:
    -   Close all streams, handles, and connections (use `.use {}` or `try-with-resources`).
    -   Prefer `Sequence` (Lazy) over `List` (Eager) for large dataset transformations in Kotlin.

---

## 5. Security Standards

1.  **Fail-Fast Validation**: Sanitize and validate ALL inputs at the boundary using `init` blocks in DTOs.
2.  **Credential Security**: Store passwords with high-entropy hashing (BCrypt/Argon2). Never plaintext.
3.  **JWT Best Practices**:
    -   Short-lived access tokens (15m - 1h).
    -   Validate `iss`, `aud`, and `exp` on every request.
4.  **Rate Limiting**: Protect every public and sensitive endpoint (Login/Register) as defined in the infrastructure guide.

---

## 6. Testing Standards (The Quality Shield)

### **Testing Pyramid**
-   **Unit Tests (Many)**: Focus on logic, edge cases, and business rules. Speed: `< 100ms` per test.
-   **Integration Tests (Some)**: Focus on DB queries, API contracts, and cross-service logic.
-   **E2E Tests (Few)**: Focus on critical user journeys.

### **Systematic Patterns**
1.  **AAA Pattern**: **Arrange** (Set data), **Act** (Run code), **Assert** (Verify).
2.  **Behavior-Focused**: Test *what* the system does for the user, not *how* it's implemented internally.
3.  **Mocking Policy**:
    -   Mock: External APIs, heavy DB operations (in unit tests), network.
    -   Don't Mock: The specific class/function under test.
4.  **Coverage Target**: **80%+** for critical business logic and API paths.

---

## 7. Directory & File Organization

### **Standard Structure**
```text
src/main/kotlin/com/solodev/fleet/modules/
├── {domain}/
│   ├── application/
│   │   ├── dto/        # Request/Response/Update DTOs
│   │   └── usecases/   # Pure business logic (One class per use case)
│   └── infrastructure/
│       └── http/       # Ktor Routes
└── shared/             # Cross-cutting concerns (Security, Serializers, Utils)
```

---

## 8. Quality Checklist

Before committing any implementation:
- [ ] **Clean**: No commented code, no magic numbers, functions < 20 lines.
- [ ] **Secure**: Input validated at DTO, auth checks applied, PII protected.
- [ ] **Performant**: No N+1 queries, indexes verified, lazy loading used where appropriate.
- [ ] **Tested**: Unit tests follow AAA, 80% coverage on new logic, edge cases covered.
- [ ] **Documented**: Comments explaining the "Why" (for complex logic), not the "What".

---

## 9. References
- **Clean Code Skill**: `skills/clean-code/SKILL.md`
- **Performance Optimizer**: `skills/perfomance-optimizer/performance-optimizer.md`
- **Test Engineer**: `skills/test-engineer/test-engineer.md`
- **API Patterns**: `skills/api-patterns/SKILL.md`
