# API Test Scenarios - Rental & Customer Management

**Last Updated**: 2026-02-07  
**Base URL**: `http://localhost:8080`

---

## Test Scenario 1: Complete Rental Flow (Happy Path)

### Step 1.1: Create a Customer
**Endpoint**: `POST /v1/customers`

**Request**:
```json
{
  "email": "john.doe@example.com",
  "firstName": "John",
  "lastName": "Doe",
  "phone": "+63-917-123-4567",
  "driversLicense": "N01-12-345678",
  "driverLicenseExpiry": "2028-12-31",
  "address": "123 Makati Avenue",
  "city": "Makati",
  "state": "Metro Manila",
  "postalCode": "1200",
  "country": "Philippines"
}
```

**Expected Response** (201 Created):
```json
{
  "success": true,
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "email": "john.doe@example.com",
    "fullName": "John Doe",
    "phone": "+63-917-123-4567",
    "driversLicense": "N01-12-345678",
    "isActive": true
  },
  "requestId": "req_abc123"
}
```

**Save for Next Step**: `customerId = "550e8400-e29b-41d4-a716-446655440000"`

---

### Step 1.2: List All Customers
**Endpoint**: `GET /v1/customers`

**Expected Response** (200 OK):
```json
{
  "success": true,
  "data": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "email": "john.doe@example.com",
      "fullName": "John Doe",
      "phone": "+63-917-123-4567",
      "driversLicense": "N01-12-345678",
      "isActive": true
    }
  ],
  "requestId": "req_list001"
}
```

---

### Step 1.3: Create a Rental
**Endpoint**: `POST /v1/rentals`

**Request**:
```json
{
  "vehicleId": "vehicle-uuid-here",
  "customerId": "550e8400-e29b-41d4-a716-446655440000",
  "startDate": "2026-02-10T10:00:00Z",
  "endDate": "2026-02-15T10:00:00Z"
}
```

**Expected Response** (201 Created):
```json
{
  "success": true,
  "data": {
    "id": "rental-uuid-123",
    "rentalNumber": "RNT-2026-001",
    "vehicleId": "vehicle-uuid-here",
    "customerId": "550e8400-e29b-41d4-a716-446655440000",
    "status": "RESERVED",
    "startDate": "2026-02-10T10:00:00Z",
    "endDate": "2026-02-15T10:00:00Z",
    "actualStartDate": null,
    "actualEndDate": null,
    "startOdometerKm": null,
    "endOdometerKm": null,
    "dailyRateCents": 250000,
    "totalCostCents": 1250000,
    "currencyCode": "PHP"
  },
  "requestId": "req_rental001"
}
```

**Save for Next Step**: `rentalId = "rental-uuid-123"`

---

### Step 1.4: Activate the Rental
**Endpoint**: `POST /v1/rentals/{rentalId}/activate`

**Request**: (No body required)

**Expected Response** (200 OK):
```json
{
  "success": true,
  "data": {
    "id": "rental-uuid-123",
    "rentalNumber": "RNT-2026-001",
    "status": "ACTIVE",
    "actualStartDate": "2026-02-10T10:15:30.123Z",
    "startOdometerKm": 45230,
    "dailyRateCents": 250000,
    "totalCostCents": 1250000,
    "currencyCode": "PHP"
  },
  "requestId": "req_activate001"
}
```

---

### Step 1.5: Complete the Rental
**Endpoint**: `POST /v1/rentals/{rentalId}/complete`

**Request**:
```json
{
  "finalMileage": 45680
}
```

**Expected Response** (200 OK):
```json
{
  "success": true,
  "data": {
    "id": "rental-uuid-123",
    "rentalNumber": "RNT-2026-001",
    "status": "COMPLETED",
    "actualStartDate": "2026-02-10T10:15:30.123Z",
    "actualEndDate": "2026-02-15T14:30:00.456Z",
    "startOdometerKm": 45230,
    "endOdometerKm": 45680,
    "dailyRateCents": 250000,
    "totalCostCents": 1250000,
    "currencyCode": "PHP"
  },
  "requestId": "req_complete001"
}
```

---

## Test Scenario 2: Error Cases

### Test 2.1: Duplicate Email
**Endpoint**: `POST /v1/customers`

**Request**:
```json
{
  "email": "john.doe@example.com",
  "firstName": "Jane",
  "lastName": "Smith",
  "phone": "+63-917-999-8888",
  "driversLicense": "N01-99-888777",
  "driverLicenseExpiry": "2027-06-30"
}
```

**Expected Response** (422 Unprocessable Entity):
```json
{
  "success": false,
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Customer with email john.doe@example.com already exists"
  },
  "requestId": "req_error001"
}
```

---

### Test 2.2: Expired Driver's License
**Endpoint**: `POST /v1/customers`

**Request**:
```json
{
  "email": "expired@example.com",
  "firstName": "Test",
  "lastName": "User",
  "phone": "+63-917-111-2222",
  "driversLicense": "N01-11-222333",
  "driverLicenseExpiry": "2020-01-01"
}
```

**Expected Response** (422 Unprocessable Entity):
```json
{
  "success": false,
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Driver's license is expired (expiry: 2020-01-01)"
  },
  "requestId": "req_error002"
}
```

---

### Test 2.3: Missing Required Fields
**Endpoint**: `POST /v1/customers`

**Request**:
```json
{
  "email": "incomplete@example.com",
  "firstName": "Test"
}
```

**Expected Response** (422 Unprocessable Entity):
```json
{
  "success": false,
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Last name cannot be blank"
  },
  "requestId": "req_error003"
}
```

---

### Test 2.4: Invalid State Transition (Complete without Activate)
**Endpoint**: `POST /v1/rentals/{rentalId}/complete`

**Precondition**: Rental is in RESERVED status (not activated)

**Expected Response** (409 Conflict):
```json
{
  "success": false,
  "error": {
    "code": "INVALID_STATE",
    "message": "Rental must be ACTIVE"
  },
  "requestId": "req_error004"
}
```

---

### Test 2.5: Customer Not Found
**Endpoint**: `GET /v1/customers/non-existent-uuid`

**Expected Response** (404 Not Found):
```json
{
  "success": false,
  "error": {
    "code": "NOT_FOUND",
    "message": "Customer not found"
  },
  "requestId": "req_error005"
}
```

---

### Test 2.6: Invalid Date Format
**Endpoint**: `POST /v1/customers`

**Request**:
```json
{
  "email": "baddate@example.com",
  "firstName": "Test",
  "lastName": "User",
  "phone": "+63-917-111-2222",
  "driversLicense": "N01-11-222333",
  "driverLicenseExpiry": "31-12-2028"
}
```

**Expected Response** (422 Unprocessable Entity):
```json
{
  "success": false,
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Invalid driver license expiry date format. Expected YYYY-MM-DD"
  },
  "requestId": "req_error006"
}
```

---

## Test Scenario 3: Cancel Rental

### Step 3.1: Create and Cancel a Rental
**Endpoint**: `POST /v1/rentals/{rentalId}/cancel`

**Precondition**: Rental is in RESERVED or ACTIVE status

**Expected Response** (200 OK):
```json
{
  "success": true,
  "data": {
    "id": "rental-uuid-123",
    "rentalNumber": "RNT-2026-002",
    "status": "CANCELLED",
    "dailyRateCents": 250000,
    "totalCostCents": 1250000,
    "currencyCode": "PHP"
  },
  "requestId": "req_cancel001"
}
```

---

## cURL Examples

### Create Customer
```bash
curl -X POST http://localhost:8080/v1/customers \
  -H "Content-Type: application/json" \
  -d '{
    "email": "john.doe@example.com",
    "firstName": "John",
    "lastName": "Doe",
    "phone": "+63-917-123-4567",
    "driversLicense": "N01-12-345678",
    "driverLicenseExpiry": "2028-12-31"
  }'
```

### List Customers
```bash
curl -X GET http://localhost:8080/v1/customers
```

### Get Customer by ID
```bash
curl -X GET http://localhost:8080/v1/customers/550e8400-e29b-41d4-a716-446655440000
```

### Create Rental
```bash
curl -X POST http://localhost:8080/v1/rentals \
  -H "Content-Type: application/json" \
  -d '{
    "vehicleId": "vehicle-uuid-here",
    "customerId": "550e8400-e29b-41d4-a716-446655440000",
    "startDate": "2026-02-10T10:00:00Z",
    "endDate": "2026-02-15T10:00:00Z"
  }'
```

### Activate Rental
```bash
curl -X POST http://localhost:8080/v1/rentals/rental-uuid-123/activate
```

### Complete Rental
```bash
curl -X POST http://localhost:8080/v1/rentals/rental-uuid-123/complete \
  -H "Content-Type: application/json" \
  -d '{"finalMileage": 45680}'
```

### Cancel Rental
```bash
curl -X POST http://localhost:8080/v1/rentals/rental-uuid-123/cancel
```

---

## Testing Checklist

- [ ] Create customer with all fields
- [ ] Create customer with minimal fields (no address)
- [ ] List all customers
- [ ] Get customer by ID
- [ ] Attempt duplicate email (should fail)
- [ ] Attempt expired license (should fail)
- [ ] Create rental with valid customer
- [ ] Activate rental
- [ ] Complete rental with mileage
- [ ] Cancel rental
- [ ] Attempt invalid state transitions (should fail)
- [ ] Verify vehicle state changes correctly

---

**End of Test Scenarios**
