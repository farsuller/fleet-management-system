# FleetDrive App Integration - API Endpoints

> [!NOTE]
> **Mobile App Focus**: This document is specifically curated for the **FleetDrive** mobile application development. It prioritizes endpoints used for driver self-service, real-time telemetry, and field operations.

> [!TIP]
> **Swagger Integration**: All endpoints listed below are synchronized with the backend's OpenAPI specification. You can test these interactions interactively at: `http://localhost:8080/swagger`

---

## 1. Authentication & Account
Endpoints for driver self-service, login, and token maintenance.

### 1.1 Driver Self-Registration
**Endpoint**: `POST /v1/drivers/register`
**Context**: Used for new drivers to create their account. An email verification link will be sent to the provided address.
**Permissions**: Public

**Request**:
```json
{
  "email": "juan.delacruz@example.com",
  "passwordRaw": "SecurePass123!",
  "firstName": "Juan",
  "lastName": "Dela Cruz",
  "phone": "+639171234567",
  "licenseNumber": "N01-12-345678",
  "licenseExpiry": "2029-12-31",
  "licenseClass": "Professional",
  "address": "123 Main St",
  "city": "Manila",
  "country": "Philippines"
}
```

**Response**: `201 Created`
```json
{
  "success": true,
  "data": {
    "id": "driver_uuid_789",
    "firstName": "Juan",
    "lastName": "Dela Cruz",
    "email": "juan.delacruz@example.com",
    "isActive": true
  },
  "requestId": "req_..."
}
```

### 1.2 Driver Login
**Endpoint**: `POST /v1/auth/token`
**Context**: Authenticates a driver and returns session tokens.
**Permissions**: Public

**Request**:
```json
{
  "email": "juan.delacruz@example.com",
  "password": "SecurePass123!"
}
```

**Response**: `200 OK`
```json
{
  "accessToken": "eyJhbGciOiJIUzI1Ni...",
  "refreshToken": "def456...",
  "driverId": "driver_uuid_789"
}
```

### 1.3 Token Refresh
**Endpoint**: `POST /v1/auth/token/refresh`
**Context**: Exchanges a valid refresh token for a new access token pair.
**Permissions**: Public

**Request**:
```json
{
  "refreshToken": "def456..."
}
```

**Response**: `200 OK`
```json
{
  "accessToken": "new_eyJhbGciOiJIUzI1Ni...",
  "refreshToken": "new_def456..."
}
```

### 1.4 Email Verification
**Endpoint**: `GET /v1/auth/verify?token={token}`
**Context**: Activates the driver account after clicking the link in the registration email.
**Permissions**: Public

---

## 2. Driver Profile & State
Endpoints for the driver to manage their profile and check current status.

### 2.1 Get Driver Profile
**Endpoint**: `GET /v1/drivers/{id}`
**Context**: Retrieve current profile details, including the currently assigned vehicle (if any).
**Permissions**: Authenticated (Driver JWT)

**Response**: `200 OK`
```json
{
  "success": true,
  "data": {
    "id": "driver_uuid_789",
    "firstName": "Juan",
    "lastName": "Dela Cruz",
    "email": "juan.delacruz@example.com",
    "currentAssignment": {
      "isActive": true,
      "vehicleId": "vehicle_uuid_123",
      "assignedAt": 1714700000000
    },
    "vehiclePlate": "ABC 1234",
    "vehicleType": "TRUCK"
  },
  "requestId": "req_..."
}
```

---

## 3. Shift Management
Endpoints for tracking driver work hours and vehicle usage.

### 3.1 Start Shift
**Endpoint**: `POST /v1/drivers/shifts/start`
**Context**: Driver begins their work period for a specific vehicle.
**Permissions**: Authenticated (Driver JWT)

**Request**:
```json
{
  "vehicleId": "vehicle_uuid_123",
  "notes": "Starting morning shift, vehicle check passed."
}
```

**Response**: `201 Created`
```json
{
  "success": true,
  "data": {
    "id": "shift_uuid_456",
    "vehicleId": "vehicle_uuid_123",
    "startedAt": "2026-05-03T08:00:00Z",
    "isActive": true
  },
  "requestId": "req_..."
}
```

### 3.2 Check Active Shift
**Endpoint**: `GET /v1/drivers/shifts/active`
**Context**: Check if the current driver has a shift in progress.
**Permissions**: Authenticated (Driver JWT)

**Response**: `200 OK`
```json
{
  "success": true,
  "data": {
    "id": "shift_uuid_456",
    "vehicleId": "vehicle_uuid_123",
    "startedAt": "2026-05-03T08:00:00Z",
    "isActive": true
  },
  "requestId": "req_..."
}
```

### 3.3 End Shift
**Endpoint**: `POST /v1/drivers/shifts/end`
**Context**: Driver ends their current work period.
**Permissions**: Authenticated (Driver JWT)

**Request**:
```json
{
  "notes": "Shift ended, fuel topped up."
}
```

**Response**: `200 OK`
```json
{
  "success": true,
  "data": {
    "id": "shift_uuid_456",
    "endedAt": "2026-05-03T18:00:00Z",
    "isActive": false
  },
  "requestId": "req_..."
}
```

---

## 4. Telemetry & Tracking
Endpoints for real-time location and sensor data reporting.

### 4.1 Update Vehicle Location
**Endpoint**: `POST /v1/tracking/vehicles/{vehicleId}/location`
**Context**: Periodic GPS ping (e.g., every 5-10 seconds).
**Permissions**: Authenticated (Driver/Vehicle JWT)

**Request**:
```json
{
  "latitude": 14.5995,
  "longitude": 121.0244,
  "speed": 45.5,
  "heading": 180.0,
  "accuracy": 5.0,
  "routeId": "optional_route_uuid"
}
```

### 4.2 Update Odometer Reading
**Endpoint**: `POST /v1/vehicles/{id}/odometer`
**Context**: Report current vehicle mileage (e.g., during inspection or end of day).
**Permissions**: Authenticated

**Request**:
```json
{
  "mileageKm": 12500
}
```

### 4.3 Batch Sensor Ping
**Endpoint**: `POST /v1/sensors/ping`
**Context**: High-frequency telemetry batching (GPS + IMU) to save battery and network bandwidth.
**Permissions**: Authenticated (Driver/Vehicle JWT)

**Request**:
```json
[
  {
    "vehicleId": "vehicle_uuid_123",
    "latitude": 14.5995,
    "longitude": 121.0244,
    "accuracy": 5.0,
    "speed": 45.5,
    "heading": 180.0,
    "timestamp": "2026-05-03T09:00:00.123Z",
    "accelX": -0.5,
    "gyroZ": 0.01,
    "batteryLevel": 85
  }
]
```

### 4.4 Get Current Vehicle State
**Endpoint**: `GET /v1/tracking/vehicles/{vehicleId}/state`
**Context**: Retrieve the driver's own current tracking status (speed, heading, progress, and off-route status).
**Permissions**: Authenticated (Driver JWT)

**Response**: `200 OK`
```json
{
  "success": true,
  "data": {
    "vehicleId": "vehicle_uuid_123",
    "routeId": "route_uuid_789",
    "progress": 0.42,
    "speed": 45.5,
    "heading": 180.0,
    "status": "IN_TRANSIT",
    "distanceFromRoute": 8.5,
    "location": {
      "latitude": 14.6001,
      "longitude": 121.0250
    },
    "timestamp": "2026-05-03T10:00:00Z"
  }
}
```

### 4.5 Get Active Routes
**Endpoint**: `GET /v1/tracking/routes/active`
**Context**: List pre-defined routes (Digital Rails) for the driver to select and snap their location to.
**Permissions**: Public

---

## 5. Real-Time Updates (WebSocket)
The driver app can subscribe to real-time updates for its own state or fleet status.

### 5.1 Live Fleet Tracking
**Endpoint**: `WS /v1/fleet/live`
**Context**: Establishes a WebSocket connection to receive real-time state changes and delta updates.
**Permissions**: Authenticated (Driver JWT)

**Sample Delta Message (Off-Route Alert)**:
```json
{
  "vehicleId": "vehicle_uuid_123",
  "status": "OFF_ROUTE",
  "distanceFromRoute": 150.5,
  "timestamp": "2026-05-03T10:15:00Z"
}
```

---

## 6. Maintenance & Incidents
Endpoints for reporting issues encountered on the road.

### 5.1 Report Incident
**Endpoint**: `POST /v1/vehicles/{plate}/incidents`
**Context**: Driver reports a breakdown, accident, or minor issue.
**Permissions**: Authenticated (Driver JWT)

**Request**:
```json
{
  "title": "Flat Tire",
  "description": "Right rear tire lost pressure on EDSA north bound.",
  "severity": "MEDIUM",
  "odometerKm": 12505,
  "latitude": 14.5995,
  "longitude": 121.0244
}
```

**Response**: `201 Created`
```json
{
  "success": true,
  "data": {
    "id": "incident_uuid_101",
    "vehiclePlate": "ABC 1234",
    "title": "Flat Tire",
    "status": "OPEN",
    "severity": "MEDIUM",
    "createdAt": "2026-05-03T09:05:00Z"
  },
  "requestId": "req_..."
}
```

---

## 6. Standard API Response Wrapper
All successful responses (except authentication tokens) follow this standard structure:

```json
{
  "success": true,
  "data": { ... },
  "requestId": "unique_request_id",
  "metadata": { "optional": "context" }
}
```

In case of error:
```json
{
  "success": false,
  "error": {
    "code": "ERROR_CODE",
    "message": "Human readable message",
    "details": { "field": "error info" }
  },
  "requestId": "unique_request_id"
}
```
