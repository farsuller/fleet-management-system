# Maintenance Module - Sample Payloads

This document details the Maintenance module's API endpoints, providing context for scheduling and completing maintenance jobs for the fleet.

---

## 1. Schedule Maintenance Job
**Endpoint**: `POST /v1/maintenance`
**Context**: Schedules a new maintenance job (e.g., Routine Service, Repairs) for a vehicle. This marks the vehicle as unavailable for rentals during the scheduled period.
**Permissions**: Fleet Manager (Staff)

> **Frontend Validation**: To prevent `maintenance_dates_valid` errors, ensure the selected `scheduledDate` is not in the future if the job is intended to start immediately. The database enforces that `startedAt` (current time) must be on or after `scheduledDate`. Ideally, default the date picker to the current date.

```json
{
  "vehicleId": "v-12345",
  "jobType": "ROUTINE",
  "description": "Annual oil change and safety inspection",
  "scheduledDate": "2026-02-01T10:00:00Z"
}
```

---

## 2. Start Maintenance Job
**Endpoint**: `POST /v1/maintenance/{id}/start`
**Context**: Marks a scheduled maintenance job as "IN_PROGRESS". This confirms that work has begun on the vehicle.
**Permissions**: Fleet Manager (Staff)

**Request**:
(Empty body)

**Response**: `200 OK`
```json
{
  "success": true,
  "data": {
    "id": "e2f012cc-1234-4567-890a-bcdef0123456",
    "jobNumber": "MAINT-1741603200000",
    "vehicleId": "v-12345",
    "status": "IN_PROGRESS",
    "jobType": "ROUTINE",
    "description": "Annual oil change and safety inspection",
    "scheduledDate": "2026-12-01T10:00:00Z",
    "startedAt": "2026-12-01T10:05:00Z",
    "totalCost": 0
  },
  "requestId": "req_start_001"
}
```

---

## 3. Complete Maintenance Job
**Endpoint**: `POST /v1/maintenance/{id}/complete`
**Context**: Mechanic or Fleet Manager finalizes a job. This action records the actual labor/parts costs and transitions the vehicle state back to `AVAILABLE`.
**Permissions**: Fleet Manager (Staff)

```json
{
  "status": "COMPLETED",
  "laborCost": 50,
  "partsCost": 120
}
```

---

## 4. Cancel Maintenance Job
**Endpoint**: `POST /v1/maintenance/{id}/cancel`
**Context**: Cancels a scheduled maintenance job. Can only be performed on jobs that are in `SCHEDULED` status.
**Permissions**: Fleet Manager (Staff)

**Request**:
(Empty body)

**Response**: `200 OK`
```json
{
  "success": true,
  "data": {
    "id": "e2f012cc-1234-4567-890a-bcdef0123456",
    "jobNumber": "MAINT-1741603200000",
    "vehicleId": "v-12345",
    "status": "CANCELLED",
    "jobType": "ROUTINE",
    "description": "Annual oil change and safety inspection",
    "scheduledDate": "2026-12-01T10:00:00Z",
    "totalCost": 0
  },
  "requestId": "req_cancel_001"
}
```

---

## 5. Maintenance Job Response
**Context**: Standard response structure for a single maintenance job. Returned by creation, update, and retrieval endpoints. `totalCost` is calculated dynamically (labor + parts).

```json
{
  "id": "e2f012cc-1234-4567-890a-bcdef0123456",
  "jobNumber": "MAINT-1741603200000",
  "vehicleId": "v-12345",
  "status": "SCHEDULED",
  "jobType": "ROUTINE",
  "description": "Annual oil change and safety inspection",
  "scheduledDate": "2026-12-01T10:00:00Z",
  "totalCost": 0
}
```

---

## 6. List Maintenance History
**Endpoint**: `GET /v1/maintenance/vehicle/{id}`
**Context**: Shows the full service history for a specific vehicle. Critical for tracking asset depreciation, warranty compliance, and resale value.
**Permissions**: Fleet Manager (Staff)

```json
[
  {
    "id": "e2f012cc-1234-4567-890a-bcdef0123456",
    "jobNumber": "MAINT-1741603200000",
    "vehicleId": "v-12345",
    "status": "COMPLETED",
    "jobType": "ROUTINE",
    "description": "Annual oil change and safety inspection",
    "scheduledDate": "2026-12-01T10:00:00Z",
    "totalCost": 170
  },
  {
    "id": "e2f012cc-9999-4444-2222-abcdef123456",
    "jobNumber": "MAINT-1741705600000",
    "vehicleId": "v-12345",
    "status": "SCHEDULED",
    "jobType": "INSPECTION",
    "description": "Brake pad replacement",
    "scheduledDate": "2026-12-15T14:30:00Z",
    "totalCost": 0
  }
]
```
