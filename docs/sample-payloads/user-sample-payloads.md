# User Module - Sample Payloads

This document details the request and response structures for the User Management API, including authentication, profile management, and role assignment.

## Sample Payloads & cURL Examples

### 11.1 User Login
**Endpoint**: `POST /v1/users/login`
**Context**: Primary authentication endpoint. Returns a JWT token required for all secured endpoints and the user's role information for frontend routing (e.g., admin dashboard vs customer view).
**Permissions**: Public

**Request**:
```json
{
  "email": "test@mail.com",
  "passwordRaw": "**************"
}
```

**Response (200 OK)**:
```json
{
  "success": true,
  "data": {
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "user": {
      "id": "e2f1b0a8-3d5c-4b9e-8f2d-1a2b3c4d5e6f",
      "email": "agent.smith@fleet.com",
      "fullName": "John Smith",
      "roles": ["CUSTOMER"]
    }
  }
}
```

```

### 11.2 Email Verification
**Endpoint**: `GET /v1/auth/verify?token={token}`
**Context**: Verifies a user's email address using the token received during registration. This activates the user account for full access.
**Permissions**: Public

**Request**:
```bash
curl -X GET "http://localhost:8080/v1/auth/verify?token=550e8400-e29b-41d4-a716-446655440000"
```

**Response (200 OK)**:
```json
{
  "success": true,
  "data": {
    "message": "Email successfully verified"
  },
  "requestId": "req-123456"
}
```

### 11.3 List All Users
**Endpoint**: `GET /v1/users`
**Context**: Retrieves a paginated list of all registered users. Used by Admins for staff directory and user management.
**Permissions**: Admin Only

**Request**:
```bash
curl -X GET http://localhost:8080/v1/users \
  -H "Authorization: Bearer <your_token>" \
  -H "Accept: application/json"
```

**Response (200 OK)**:
```json
{
  "success": true,
  "data": [
    {
      "id": "e2f1b0a8-3d5c-4b9e-8f2d-1a2b3c4d5e6f",
      "email": "admin@fleet.com",
      "firstName": "System",
      "lastName": "Admin",
      "fullName": "System Admin",
      "phone": "+63-2-8888-0000",
      "isActive": true,
      "roles": ["ADMIN", "FLEET_MANAGER"],
      "staffProfile": {
        "id": "b1a2c3d4-e5f6-4a5b-9c8d-7e6f5a4b3c2d",
        "employeeId": "EMP-001",
        "department": "IT Operations",
        "position": "Senior Admin",
        "hireDate": "2024-01-15"
      }
    }
  ],
  "requestId": "req-12345"
}
```

```

### 11.4 Get Specific User
**Endpoint**: `GET /v1/users/{id}`
**Context**: Fetches the profile of a single user. Users can view their own profile; Admins can view any profile.
**Permissions**: Admin or Owner (Self)

**Request**:
```bash
curl -X GET http://localhost:8080/v1/users/e2f1b0a8-3d5c-4b9e-8f2d-1a2b3c4d5e6f \
  -H "Authorization: Bearer <your_token>"
```

**Response (200 OK)**:
```json
{
  "success": true,
  "data": {
    "id": "e2f1b0a8-3d5c-4b9e-8f2d-1a2b3c4d5e6f",
    "email": "admin@fleet.com",
    "firstName": "System",
    "lastName": "Admin",
    "fullName": "System Admin",
    "isActive": true,
    "roles": ["ADMIN"]
  }
}
```

```

### 11.5 Partial Profile Update
**Endpoint**: `PATCH /v1/users/{id}`
**Context**: Allows users to update their own basic contact information (phone, name). Does not allow changing roles or secure fields.
**Permissions**: Admin or Owner (Self)

**Request**:
```bash
curl -X PATCH http://localhost:8080/v1/users/e2f1b0a8-3d5c-4b9e-8f2d-1a2b3c4d5e6f \
  -H "Authorization: Bearer <your_token>" \
  -H "Content-Type: application/json" \
  -d '{
    "phone": "+63-917-555-0199",
    "lastName": "Administrator"
  }'
```

---

---

### 11.6 Full Update (Including Staff Profile)
**Endpoint**: `PATCH /v1/users/{id}` (with staffProfile)
**Context**: Updates extended profile fields, including employment details. Used for promotions or department transfers.
**Permissions**: Admin Only

**Request**:
```bash
curl -X PATCH http://localhost:8080/v1/users/e2f1b0a8-3d5c-4b9e-8f2d-1a2b3c4d5e6f \
  -H "Authorization: Bearer <your_token>" \
  -H "Content-Type: application/json" \
  -d '{
    "staffProfile": {
      "department": "Fleet Logistics",
      "position": "Operations Manager"
    }
  }'
```

---

---

### 11.7 Assign Role to User
**Endpoint**: `POST /v1/users/{id}/roles`
**Context**: Grants a new role (permission set) to a user. Critical for RBAC management.
**Permissions**: Admin Only

**Request**:
```bash
curl -X POST http://localhost:8080/v1/users/e2f1b0a8-3d5c-4b9e-8f2d-1a2b3c4d5e6f/roles \
  -H "Authorization: Bearer <your_token>" \
  -H "Content-Type: application/json" \
  -d '{
    "roleName": "RENTAL_AGENT"
  }'
```

**Response (200 OK)**:
```json
{
  "success": true,
  "data": {
    "id": "e2f1b0a8-3d5c-4b9e-8f2d-1a2b3c4d5e6f",
    "email": "admin@fleet.com",
    "roles": ["ADMIN", "RENTAL_AGENT"]
  }
}
```

---

---

### 11.8 Delete User
**Endpoint**: `DELETE /v1/users/{id}`
**Context**: Deactivates (soft-delete) or removes a user account. Used for offboarding staff.
**Permissions**: Admin Only

**Request**:
```bash
curl -X DELETE http://localhost:8080/v1/users/e2f1b0a8-3d5c-4b9e-8f2d-1a2b3c4d5e6f \
  -H "Authorization: Bearer <your_token>"
```

**Response (200 OK)**:
```json
{
  "success": true,
  "data": {
    "message": "User deleted successfully"
  },
  "requestId": "req-12345"
}
```
