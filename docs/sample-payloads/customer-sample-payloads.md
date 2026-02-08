# Customer Module - Sample Payloads

This document details the API endpoints for managing customer profiles, including driver's license information.

## cURL Examples

### Create Customer
**Endpoint**: `POST /v1/customers`
**Context**: Registers a new customer in the system. Required before a rental can be created for them. Validates unique email and driver's license.
**Permissions**: Customer (Self-registration) or Staff (Walk-in)

```bash
curl -X POST http://localhost:8080/v1/customers \
  -H "Content-Type: application/json" \
  -d '{
    "email": "customer@example.com",
    "firstName": "John",
    "lastName": "Doe",
    "phone": "+63-917-123-4567",
    "driversLicense": "N01-12-345678",
    "driverLicenseExpiry": "2028-12-31"
  }'
```

### List Customers
**Endpoint**: `GET /v1/customers`
**Context**: Retrieves a list of all customers. Used by staff for looking up customer history or processing walk-ins.
**Permissions**: Staff Only

```bash
curl -X GET http://localhost:8080/v1/customers
```

### Get Customer by ID
**Endpoint**: `GET /v1/customers/{id}`
**Context**: Fetches full details for a specific customer, including their license validity. Critical for verifying eligibility before rental.
**Permissions**: Staff & Customer (Own profile)

```bash
curl -X GET http://localhost:8080/v1/customers/550e8400-e29b-41d4-a716-446655440000
```
