# Customer Module - Sample Payloads

This document details the API endpoints for managing customer profiles, including driver's license information.

## cURL Examples

### Create Customer
**Endpoint**: `POST /v1/customers`
**Context**: Registers a new customer in the system. Required before a rental can be created for them. Validates unique email and driver's license.
**Permissions**: Staff (ADMIN / FLEET_MANAGER)

```bash
curl -X POST http://localhost:8080/v1/customers \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -d '{
    "email": "customer@example.com",
    "firstName": "John",
    "lastName": "Doe",
    "phone": "+63-917-123-4567",
    "driversLicense": "N01-12-345678",
    "driverLicenseExpiry": "2028-12-31"
  }'
```

**Success Response** (`201 Created`):
```json
{
  "success": true,
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "userId": null,
    "firstName": "John",
    "lastName": "Doe",
    "email": "customer@example.com",
    "phone": "+63-917-123-4567",
    "driverLicenseNumber": "N01-12-345678",
    "licenseExpiryMs": 1861833600000,
    "isActive": true,
    "createdAt": 1741420800000
  },
  "error": null,
  "requestId": "req-abc123"
}
```

---

### List Customers
**Endpoint**: `GET /v1/customers`
**Context**: Retrieves a list of all customers. Used by staff for looking up customer history or processing walk-ins.
**Permissions**: Staff Only

```bash
curl -X GET http://localhost:8080/v1/customers \
  -H "Authorization: Bearer <token>"
```

**Success Response** (`200 OK`):
```json
{
  "success": true,
  "data": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "userId": null,
      "firstName": "John",
      "lastName": "Doe",
      "email": "customer@example.com",
      "phone": "+63-917-123-4567",
      "driverLicenseNumber": "N01-12-345678",
      "licenseExpiryMs": 1861833600000,
      "isActive": true,
      "createdAt": 1741420800000
    }
  ],
  "error": null,
  "requestId": "req-abc123"
}
```

---

### Get Customer by ID
**Endpoint**: `GET /v1/customers/{id}`
**Context**: Fetches full details for a specific customer, including their license validity. Critical for verifying eligibility before rental.
**Permissions**: Staff Only

```bash
curl -X GET http://localhost:8080/v1/customers/550e8400-e29b-41d4-a716-446655440000 \
  -H "Authorization: Bearer <token>"
```

**Success Response** (`200 OK`):
```json
{
  "success": true,
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "userId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "firstName": "John",
    "lastName": "Doe",
    "email": "customer@example.com",
    "phone": "+63-917-123-4567",
    "driverLicenseNumber": "N01-12-345678",
    "licenseExpiryMs": 1861833600000,
    "isActive": true,
    "createdAt": 1741420800000
  },
  "error": null,
  "requestId": "req-abc123"
}
```

**Not Found Response** (`404`):
```json
{
  "success": false,
  "data": null,
  "error": { "code": "NOT_FOUND", "message": "Customer not found" },
  "requestId": "req-abc123"
}
```

---

### Toggle Customer Active Status (Deactivate / Reactivate)
**Endpoint**: `PATCH /v1/customers/{id}/deactivate`
**Context**: Flips the `isActive` flag on the customer record. Deactivates an active customer, or reactivates an inactive one. Staff use this instead of hard-deleting customers to preserve rental history.
**Permissions**: ADMIN / FLEET_MANAGER

```bash
curl -X PATCH http://localhost:8080/v1/customers/550e8400-e29b-41d4-a716-446655440000/deactivate \
  -H "Authorization: Bearer <token>"
```

**Success Response** (`200 OK`) — deactivated:
```json
{
  "success": true,
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "userId": null,
    "firstName": "John",
    "lastName": "Doe",
    "email": "customer@example.com",
    "phone": "+63-917-123-4567",
    "driverLicenseNumber": "N01-12-345678",
    "licenseExpiryMs": 1861833600000,
    "isActive": false,
    "createdAt": 1741420800000
  },
  "error": null,
  "requestId": "req-abc123"
}
```

**Not Found Response** (`404`):
```json
{
  "success": false,
  "data": null,
  "error": { "code": "NOT_FOUND", "message": "Customer not found" },
  "requestId": "req-abc123"
}
```

