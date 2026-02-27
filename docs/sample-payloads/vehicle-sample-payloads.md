# Vehicle Module - Sample Payloads

This document provides sample payloads for the Vehicle API, designed to help frontend developers integrate with the fleet inventory system.

## Sample API Requests & Responses

### 1. List All Vehicles
**Endpoint**: `GET /v1/vehicles`
**Context**: Retrieves a list of all vehicles in the fleet. Used for the main inventory dashboard.
**Permissions**: Authenticated Users

**Response**: `200 OK`
```json
{
  "success": true,
  "data": [
    {
      "id": "46b6a07c-3f8e-4d21-9c1a-8e7f5a2b3c4d",
      "vin": "1HGBH41JXMN109186",
      "licensePlate": "ABC-1234",
      "make": "Toyota",
      "model": "Camry",
      "year": 2024,
      "color": "Silver",
      "state": "AVAILABLE",
      "mileageKm": 15000,
      "dailyRate": 2500.0,
      "currencyCode": "PHP",
      "passengerCapacity": 5,
      "lastLocation": { "latitude": 14.702, "longitude": 121.103 },
      "routeProgress": 0.45,
      "bearing": 120.5
    },
    {
      "id": "7c8d9e0f-1a2b-3c4d-5e6f-7a8b9c0d1e2f",
      "vin": "2HGFC2F59HH123456",
      "licensePlate": "XYZ-5678",
      "make": "Honda",
      "model": "Civic",
      "year": 2023,
      "color": "Blue",
      "state": "RENTED",
      "mileageKm": 8500,
      "dailyRate": 2000,
      "currencyCode": "PHP",
      "passengerCapacity": 5
    }
  ],
  "requestId": "req_abc123xyz"
}
```

### 2. Create Vehicle
**Endpoint**: `POST /v1/vehicles`
**Context**: Onboards a new vehicle into the system. Initial state is always `AVAILABLE`.
**Permissions**: Admin Only

**Request**:
> [!IMPORTANT]
> **VIN (Vehicle Identification Number)**: This 17-digit identifier is the unique "DNA" of the vehicle. 
> - **Business**: Used for insurance, legal compliance (ISO 3779), and tracking lifecycle/recalls.
> - **Technical**: Acts as a unique database constraint. Must be exactly 17 characters and should ideally pass a mathematical checksum for real-world integration.

```json
{
  "vin": "1HGBH41JXMN109186",
  "licensePlate": "ABC-1234",
  "make": "Toyota",
  "model": "Camry",
  "year": 2024,
  "color": "Silver",
  "mileageKm": 0,
  "dailyRate": 2500.0,
  "passengerCapacity": 5
}
```

**Response**: `201 Created`
```json
{
  "success": true,
  "data": {
    "id": "46b6a07c-3f8e-4d21-9c1a-8e7f5a2b3c4d",
    "vin": "1HGBH41JXMN109186",
    "licensePlate": "ABC-1234",
    "make": "Toyota",
    "model": "Camry",
    "year": 2024,
    "color": "Silver",
    "state": "AVAILABLE",
    "mileageKm": 0,
    "dailyRate": 2500,
    "currencyCode": "PHP",
    "passengerCapacity": 5
  },
  "requestId": "req_create_001"
}
```

**Validation Error**: `422 Unprocessable Entity`
```json
{
  "success": false,
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Validation failed",
    "fields": [
      {
        "field": "vin",
        "message": "VIN must be exactly 17 characters"
      },
      {
        "field": "year",
        "message": "Year must be between 1900 and 2100"
      }
    ]
  },
  "requestId": "req_create_002"
}
```

### 3. Get Vehicle by ID
**Endpoint**: `GET /v1/vehicles/{id}`
**Context**: Fetches detailed information for a specific vehicle. Used for the "Vehicle Details" or "Edit Vehicle" view.
**Permissions**: Authenticated Users

**Response**: `200 OK`
```json
{
  "success": true,
  "data": {
    "id": "46b6a07c-3f8e-4d21-9c1a-8e7f5a2b3c4d",
    "vin": "1HGBH41JXMN109186",
    "licensePlate": "ABC-1234",
    "make": "Toyota",
    "model": "Camry",
    "year": 2024,
    "color": "Silver",
    "state": "AVAILABLE",
    "mileageKm": 15000,
    "dailyRate": 2500,
    "currencyCode": "PHP",
    "passengerCapacity": 5
  },
  "requestId": "req_get_001"
}
```

**Not Found**: `404 Not Found`
```json
{
  "success": false,
  "error": {
    "code": "NOT_FOUND",
    "message": "Vehicle not found"
  },
  "requestId": "req_get_002"
}
```

### 4. Update Vehicle
**Endpoint**: `PATCH /v1/vehicles/{id}`
**Context**: Updates static attributes of a vehicle (e.g., color, daily rate). Does NOT change the vehicle state (use /state for that).
**Permissions**: Admin Only

**Request**:
```json
{
  "color": "Red",
  "dailyRate": 2800,
  "passengerCapacity": 4
}
```

**Response**: `200 OK`
```json
{
  "success": true,
  "data": {
    "id": "46b6a07c-3f8e-4d21-9c1a-8e7f5a2b3c4d",
    "vin": "1HGBH41JXMN109186",
    "licensePlate": "ABC-1234",
    "make": "Toyota",
    "model": "Camry",
    "year": 2024,
    "color": "Red",
    "state": "AVAILABLE",
    "mileageKm": 15000,
    "dailyRate": 2800,
    "currencyCode": "PHP",
    "passengerCapacity": 4
  },
  "requestId": "req_update_001"
}
```

### 5. Update Vehicle State
**Endpoint**: `PATCH /v1/vehicles/{id}/state`
**Context**: Manually transitions a vehicle's state (e.g., sending a car to MAINTENANCE). Validates that the transition is legal (e.g., cannot move RENTED -> MAINTENANCE).
**Permissions**: Staff & Admin

**Request**:
```json
{
  "state": "MAINTENANCE"
}
```

**Response**: `200 OK`
```json
{
  "success": true,
  "data": {
    "id": "46b6a07c-3f8e-4d21-9c1a-8e7f5a2b3c4d",
    "vin": "1HGBH41JXMN109186",
    "licensePlate": "ABC-1234",
    "make": "Toyota",
    "model": "Camry",
    "year": 2024,
    "color": "Red",
    "state": "MAINTENANCE",
    "mileageKm": 15000,
    "dailyRate": 2800,
    "currencyCode": "PHP",
    "passengerCapacity": 4
  },
  "requestId": "req_state_001"
}
```

**Invalid State Transition**: `422 Unprocessable Entity`
```json
{
  "success": false,
  "error": {
    "code": "INVALID_STATE",
    "message": "Cannot send rented vehicle to maintenance"
  },
  "requestId": "req_state_002"
}
```

### 6. Record Odometer Reading
**Endpoint**: `POST /v1/vehicles/{id}/odometer`
**Context**: Updates the vehicle's mileage. Typically used during check-out or check-in procedures. Mileage must be monotonically increasing.
**Permissions**: Staff & Admin

**Request**:
```json
{
  "mileageKm": 18500
}
```

**Response**: `200 OK`
```json
{
  "success": true,
  "data": {
    "id": "46b6a07c-3f8e-4d21-9c1a-8e7f5a2b3c4d",
    "vin": "1HGBH41JXMN109186",
    "licensePlate": "ABC-1234",
    "make": "Toyota",
    "model": "Camry",
    "year": 2024,
    "color": "Red",
    "state": "MAINTENANCE",
    "mileageKm": 18500,
    "dailyRate": 2800,
    "currencyCode": "PHP",
    "passengerCapacity": 4
  },
  "requestId": "req_odometer_001"
}
```

**Invalid Mileage**: `422 Unprocessable Entity`
```json
{
  "success": false,
  "error": {
    "code": "INVALID_MILEAGE",
    "message": "New mileage (10000) cannot be less than current mileage (18500)"
  },
  "requestId": "req_odometer_002"
}
```

### 7. Delete Vehicle
**Endpoint**: `DELETE /v1/vehicles/{id}`
**Context**: Soft-deletes a vehicle, removing it from active lists but preserving history.
**Permissions**: Admin Only

**Request**: `DELETE /v1/vehicles/46b6a07c-3f8e-4d21-9c1a-8e7f5a2b3c4d`

**Response**: `204 No Content`
(Empty response body)

**Not Found**: `404 Not Found`
```json
{
  "success": false,
  "error": {
    "code": "NOT_FOUND",
    "message": "Vehicle not found"
  },
  "requestId": "req_delete_001"
}
```
