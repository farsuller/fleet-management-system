# Drivers Module - Sample Payloads

This document covers the driver management API, including self-registration from the mobile app, back-office driver creation, vehicle assignments, and work-hours history.

---

## Driver Registration (Mobile App)

### Self-Register as Driver
**Endpoint**: `POST /v1/drivers/register`
**Context**: Called from the Android driver app when a new driver creates their account. Does **not** require a JWT — the driver receives their credentials in the response.
**Permissions**: Public (no auth required)

**Request**:
```json
{
  "firstName": "Juan",
  "lastName": "dela Cruz",
  "licenseNumber": "N01-12-345678",
  "phone": "+639171234567",
  "email": "juan.delacruz@example.com",
  "password": "SecurePass123!"
}
```

**Response**: `201 Created`
```json
{
  "success": true,
  "data": {
    "id": "d3e5f7a9-1234-5678-abcd-ef0123456789",
    "firstName": "Juan",
    "lastName": "dela Cruz",
    "licenseNumber": "N01-12-345678",
    "phone": "+639171234567",
    "email": "juan.delacruz@example.com",
    "isActive": true,
    "createdAt": "2026-03-07T10:00:00Z"
  },
  "requestId": "req_..."
}
```

---

## Back-Office Driver Management

### List All Drivers
**Endpoint**: `GET /v1/drivers`
**Context**: Returns a paginated list of all registered drivers. Supports `?isActive=true/false` to filter by status.
**Permissions**: Authenticated (Admin/Manager)

**Response**: `200 OK`
```json
{
  "success": true,
  "data": [
    {
      "id": "d3e5f7a9-1234-5678-abcd-ef0123456789",
      "firstName": "Juan",
      "lastName": "dela Cruz",
      "licenseNumber": "N01-12-345678",
      "phone": "+639171234567",
      "email": "juan.delacruz@example.com",
      "isActive": true,
      "currentVehicleId": "0a8e652a-604d-4cfb-81c6-e14eccc80ac8",
      "createdAt": "2026-03-07T10:00:00Z"
    }
  ],
  "requestId": "req_..."
}
```

### Create Driver (Back-Office)
**Endpoint**: `POST /v1/drivers`
**Context**: Admin creates a driver account directly from the back-office. The driver can later log in using their credentials from the mobile app.
**Permissions**: Authenticated (Admin/Manager)

**Request**:
```json
{
  "firstName": "Maria",
  "lastName": "Santos",
  "licenseNumber": "A02-34-567890",
  "phone": "+639189876543",
  "email": "maria.santos@example.com",
  "password": "InitialPass456!"
}
```

**Response**: `201 Created`
```json
{
  "success": true,
  "data": {
    "id": "b1c2d3e4-5678-90ab-cdef-012345678901",
    "firstName": "Maria",
    "lastName": "Santos",
    "licenseNumber": "A02-34-567890",
    "phone": "+639189876543",
    "email": "maria.santos@example.com",
    "isActive": true,
    "createdAt": "2026-03-07T11:00:00Z"
  },
  "requestId": "req_..."
}
```

### Get Driver by ID
**Endpoint**: `GET /v1/drivers/{id}`
**Context**: Retrieve full profile for a specific driver including their current vehicle assignment status.
**Permissions**: Authenticated

**Response**: `200 OK`
```json
{
  "success": true,
  "data": {
    "id": "d3e5f7a9-1234-5678-abcd-ef0123456789",
    "firstName": "Juan",
    "lastName": "dela Cruz",
    "licenseNumber": "N01-12-345678",
    "phone": "+639171234567",
    "email": "juan.delacruz@example.com",
    "isActive": true,
    "currentVehicleId": "0a8e652a-604d-4cfb-81c6-e14eccc80ac8",
    "createdAt": "2026-03-07T10:00:00Z"
  },
  "requestId": "req_..."
}
```

### Deactivate Driver
**Endpoint**: `PATCH /v1/drivers/{id}/deactivate`
**Context**: Soft-deactivates a driver. The driver can no longer log in or be assigned to vehicles. Does not delete any history. No request body required.
**Permissions**: Authenticated (Admin/Manager)

**Response**: `200 OK`
```json
{
  "success": true,
  "data": {
    "id": "d3e5f7a9-1234-5678-abcd-ef0123456789",
    "isActive": false
  },
  "requestId": "req_..."
}
```

---

## Vehicle Assignments

### Assign Driver to Vehicle
**Endpoint**: `POST /v1/drivers/{id}/assign`
**Context**: Assigns an active driver to a vehicle. Only one driver can be assigned to a vehicle at a time. If the vehicle already has a driver, the request will be rejected.
**Permissions**: Authenticated (Admin/Manager)

**Request**:
```json
{
  "vehicleId": "0a8e652a-604d-4cfb-81c6-e14eccc80ac8",
  "notes": "Regular route assignment for morning shift"
}
```

**Response**: `200 OK`
```json
{
  "success": true,
  "data": {
    "assignmentId": "a1b2c3d4-0000-1111-2222-333344445555",
    "driverId": "d3e5f7a9-1234-5678-abcd-ef0123456789",
    "vehicleId": "0a8e652a-604d-4cfb-81c6-e14eccc80ac8",
    "assignedAt": "2026-03-07T06:00:00Z",
    "notes": "Regular route assignment for morning shift"
  },
  "requestId": "req_..."
}
```

### Release Driver from Vehicle
**Endpoint**: `POST /v1/drivers/{id}/release`
**Context**: Ends the current vehicle assignment for the driver. Records the release timestamp. No request body required.
**Permissions**: Authenticated (Admin/Manager)

**Response**: `200 OK`
```json
{
  "success": true,
  "data": {
    "assignmentId": "a1b2c3d4-0000-1111-2222-333344445555",
    "driverId": "d3e5f7a9-1234-5678-abcd-ef0123456789",
    "vehicleId": "0a8e652a-604d-4cfb-81c6-e14eccc80ac8",
    "assignedAt": "2026-03-07T06:00:00Z",
    "releasedAt": "2026-03-07T18:30:00Z",
    "durationHours": 12.5
  },
  "requestId": "req_..."
}
```

### Get Driver Assignment History
**Endpoint**: `GET /v1/drivers/{id}/assignments`
**Context**: Full chronological list of all vehicle assignments for a driver.
**Permissions**: Authenticated (Admin/Manager)

**Response**: `200 OK`
```json
{
  "success": true,
  "data": [
    {
      "assignmentId": "a1b2c3d4-0000-1111-2222-333344445555",
      "vehicleId": "0a8e652a-604d-4cfb-81c6-e14eccc80ac8",
      "vehiclePlate": "ABC 1234",
      "assignedAt": "2026-03-07T06:00:00Z",
      "releasedAt": "2026-03-07T18:30:00Z",
      "durationHours": 12.5,
      "notes": "Regular route assignment for morning shift"
    },
    {
      "assignmentId": "b2c3d4e5-1111-2222-3333-444455556666",
      "vehicleId": "1b9f763b-715e-5d30-92d7-f25fdddd91bd",
      "vehiclePlate": "XYZ 5678",
      "assignedAt": "2026-03-06T06:00:00Z",
      "releasedAt": "2026-03-06T17:00:00Z",
      "durationHours": 11.0,
      "notes": null
    }
  ],
  "requestId": "req_..."
}
```

---

## Work Hours

### Get Driver Work Hours History
**Endpoint**: `GET /v1/drivers/{id}/history`
**Context**: Returns aggregated or raw work-hours data derived from assignment records. Useful for payroll and compliance reporting. Supports `?startDate=` and `?endDate=` query params.
**Permissions**: Authenticated (Admin/Manager)

**Response**: `200 OK`
```json
{
  "success": true,
  "data": {
    "driverId": "d3e5f7a9-1234-5678-abcd-ef0123456789",
    "driverName": "Juan dela Cruz",
    "periodStart": "2026-03-01T00:00:00Z",
    "periodEnd": "2026-03-07T23:59:59Z",
    "totalHours": 72.5,
    "dailyBreakdown": [
      {
        "date": "2026-03-07",
        "assignmentId": "a1b2c3d4-0000-1111-2222-333344445555",
        "vehiclePlate": "ABC 1234",
        "startTime": "2026-03-07T06:00:00Z",
        "endTime": "2026-03-07T18:30:00Z",
        "hours": 12.5
      }
    ]
  },
  "requestId": "req_..."
}
```
