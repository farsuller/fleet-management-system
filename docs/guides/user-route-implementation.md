# Phase 3: User API Implementation Guide

**Implementation Date**: Pending  
**Verification**: Not yet started  
**Server Status**: ⏳ PENDING  
**Compliance**: 0%  
**Ready for Next Phase**: ❌ NO

This guide outlines the implementation for the User Management domain.

## 1. Directory Structure

```text
src/main/kotlin/com/solodev/fleet/modules/
├── identity/ (or users/)
│   ├── application/
│   │   ├── dto/            <-- UserResponse, StaffProfileDTO
│   │   └── usecases/       <-- RegisterUserUseCase, UpdateProfileUseCase
│   └── infrastructure/
│       └── http/           <-- UserRoutes.kt
```

## 2. Implementation Checklist

### A. DTOs
- [ ] `UserRequest.kt`: Registration fields (email, password, names).
- [ ] `UserResponse.kt`: Public user info.
- [ ] `StaffProfileDTO.kt`: Employee-specific details.

### B. Use Cases
- [ ] `RegisterUserUseCase.kt`: Hash password and create record.
- [ ] `AssignRoleUseCase.kt`: Link user to a role.
- [ ] `UpdateStaffProfileUseCase.kt`: Manage employee data.

### C. Routes
- [ ] `UserRoutes.kt`: 
  - `POST /v1/users/register`
  - `GET /v1/users/me`
  - `PUT /v1/users/{id}/profile`

## 3. Code Samples

### UserResponse Mapper
```kotlin
fun fromDomain(u: User) = UserResponse(
    id = u.id.toString(),
    email = u.email,
    firstName = u.firstName,
    lastName = u.lastName,
    isActive = u.isActive
)
```

---

## 3. API Endpoints & Sample Payloads

### **A. User Registration**
- **Endpoint**: `POST /v1/users/register`
- **Request Body**:
```json
{
  "email": "john.doe@example.com",
  "password": "SecurePassword123!",
  "firstName": "John",
  "lastName": "Doe"
}
```
- **Response Body (200 OK)**:
```json
{
  "success": true,
  "data": {
    "id": "u-444",
    "email": "john.doe@example.com",
    "firstName": "John",
    "lastName": "Doe",
    "isActive": true
  },
  "requestId": "req-444"
}
```

---

## 4. Wiring
In `Routing.kt`:
```kotlin
val userRepo = UserRepositoryImpl()
userRoutes(userRepo)
```
