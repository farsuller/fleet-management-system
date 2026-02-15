# Rental Module - Sample Payloads

This document details the lifecycle of a Rental request, from creation to activation and completion.

## Sample Payloads

### Create Rental
**Endpoint**: `POST /v1/rentals`
**Context**: Creates a new reservation for a vehicle. Validates availability and calculates estimated costs. Does NOT hand over the vehicle yet.
**Permissions**: Customer & Staff

**Request**:
```json
{
  "vehicleId": "46b6a07c-...",
  "customerId": "00000000-...",
  "startDate": "2024-06-01T10:00:00Z",
  "endDate": "2024-06-05T10:00:00Z"
}
```

**Response**: `201 Created`
```json
{
  "success": true,
  "data": {
    "id": "rental_123...",
    "rentalNumber": "RNT-1717234567890",
    "vehicleId": "46b6a07c-...",
    "customerId": "00000000-...",
    "status": "RESERVED",
    "startDate": "2024-06-01T10:00:00Z",
    "endDate": "2024-06-05T10:00:00Z",
    "actualStartDate": null,
    "actualEndDate": null,
    "totalCost": 200,
    "currencyCode": "PHP"
  },
  "requestId": "req_..."
}
```

### Activate Rental
**Endpoint**: `POST /v1/rentals/{id}/activate`
**Context**: Marks the vehicle as "picked up". Transitions the vehicle state to `RENTED`. Should be called by staff when handing over keys.
**Permissions**: Staff Only

**Response**: `200 OK`
```json
{
  "success": true,
  "data": {
    "id": "rental_123...",
    "status": "ACTIVE",
    "actualStartDate": "2024-06-01T10:15:00Z",
    "..." : "..."
  },
  "requestId": "req_..."
}
```

### Error: Vehicle Conflict
**Context**: Occurs when trying to create a rental for dates that overlap with an existing reservation.
**Status**: `422 Unprocessable Entity`

**Response**:
```json
{
  "success": false,
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Vehicle is already rented during this period"
  },
  "requestId": "req_..."
}
```
