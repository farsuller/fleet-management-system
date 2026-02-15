# API Test Scenarios - Comprehensive Fleet Operations

**Last Updated**: 2026-02-15  
**Base URL**: `http://localhost:8080`

---

## Prerequisites (Authentication)

Most endpoints require a valid JWT. Perform login first:

**Endpoint**: `POST /v1/users/login`  
**Header**: `Content-Type: application/json`  
**Request**: `{"email": "admin@example.com", "password": "securepassword"}`  
**Save**: `token` from response.

---

## Test Scenario 1: Complete Rental Flow

### Step 1.1: Create a Customer
**Endpoint**: `POST /v1/customers`  
**Header**: `Authorization: Bearer <token>`

**Request**:
```json
{
  "email": "john.doe@example.com",
  "firstName": "John",
  "lastName": "Doe",
  "phone": "+63-917-123-4567",
  "driversLicense": "N01-12-345678",
  "driverLicenseExpiry": "2028-12-31"
}
```

---

### Step 1.3: Create a Rental
**Endpoint**: `POST /v1/rentals`  
**Header**: `Authorization: Bearer <token>`

**Request**:
```json
{
  "vehicleId": "vehicle-uuid-here",
  "customerId": "customer-uuid-here",
  "startDate": "2026-02-10T10:00:00Z",
  "endDate": "2026-02-15T10:00:00Z"
}
```

---

## Test Scenario 4: Maintenance Lifecycle

### Step 4.1: Schedule Maintenance
**Endpoint**: `POST /v1/maintenance`  
**Header**: `Authorization: Bearer <token>`

**Request**:
```json
{
  "vehicleId": "vehicle-uuid-here",
  "serviceType": "OIL_CHANGE",
  "scheduledDate": "2026-02-20",
  "notes": "Regular 10k checkup"
}
```

**Expected Response** (201 Created)

---

### Step 4.2: Start Maintenance
**Endpoint**: `POST /v1/maintenance/{jobId}/start`  
**Header**: `Authorization: Bearer <token>`

**Expected Response** (200 OK): Status transitions to `IN_PROGRESS`.

---

### Step 4.3: Complete Maintenance
**Endpoint**: `POST /v1/maintenance/{jobId}/complete`  
**Header**: `Authorization: Bearer <token>`

**Request**:
```json
{
  "laborCost": 1500,
  "partsCost": 2500
}
```

**Expected Result**: Status transitions to `COMPLETED`. Vehicle state returns to `AVAILABLE`.

---

## Test Scenario 5: Financial Workflow

### Step 5.1: Issue Invoice
**Endpoint**: `POST /v1/accounting/invoices`  
**Header**: `Authorization: Bearer <token>`

**Request**:
```json
{
  "customerId": "customer-uuid-here",
  "rentalId": "rental-uuid-here",
  "amount": 12500,
  "dueDate": "2026-03-01"
}
```

---

### Step 5.2: Pay Invoice (Idempotent)
**Endpoint**: `POST /v1/accounting/invoices/{id}/pay`  
**Header**: `Authorization: Bearer <token>`, `X-Idempotency-Key: pay_unique_001`

**Request**:
```json
{
  "amount": 12500,
  "paymentMethod": "CASH",
  "notes": "Paid over counter"
}
```

**Expected Result**: Returns a `PaymentReceiptResponse`. Balance for `CASH_A1` account increases.

---

## cURL Examples

### List All Accounts
```bash
curl -X GET http://localhost:8080/v1/accounting/accounts \
  -H "Authorization: Bearer <your_jwt_here>"
```

### Complete Maintenance
```bash
curl -X POST http://localhost:8080/v1/maintenance/{id}/complete \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"laborCost": 2000, "partsCost": 500}'
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
