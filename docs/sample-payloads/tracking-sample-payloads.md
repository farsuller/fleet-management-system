# Tracking Module - Sample Payloads

This document details the tracking and spatial features, including real-time vehicle location updates and route snapping (Digital Rails).

## Sample Payloads

### Update Vehicle Location
**Endpoint**: `POST /v1/tracking/vehicles/{id}/location`
**Context**: Sends a raw GPS ping from a vehicle. If a `routeId` is provided, the system "snaps" the coordinate to the nearest point on the route polyline to ensure data accuracy and efficiency.
**Permissions**: Authenticated Vehicles/Drivers

**Request**:
```json
{
  "latitude": 14.5995,
  "longitude": 121.0244,
  "routeId": "7b8e1e5b-..." 
}
```

**Response**: `200 OK`
```json
{
  "success": true,
  "data": {
    "latitude": 14.6001,
    "longitude": 121.0250
  },
  "requestId": "req_..."
}
```
> [!NOTE]
> The response returns the "snapped" location if a `routeId` was provided and snapping was successful. Otherwise, it returns the raw location provided in the request.

### List Available Routes
**Endpoint**: `GET /v1/tracking/routes`
**Context**: Retrieves all pre-defined routes (Digital Rails) available in the system. Use these IDs to enable route snapping in the location update endpoint.
**Permissions**: Public / Authenticated

**Response**: `200 OK`
```json
{
  "success": true,
  "data": [
    {
      "id": "7b8e1e5b-...",
      "name": "Village Main Loop",
      "description": "Core route through the main village streets"
    }
  ],
  "requestId": "req_..."
}
```

### Error: Vehicle Not Found
**Context**: Occurs when the provided `id` does not match any existing vehicle in the system.
**Status**: `404 Not Found`

**Response**:
```json
{
  "success": false,
  "error": {
    "code": "VEHICLE_NOT_FOUND",
    "message": "Vehicle not found: {id}"
  },
  "requestId": "req_..."
}
```

## Spatial Concepts

### Digital Rails (Route Snapping)
By providing a `routeId`, the system uses PostGIS `ST_ClosestPoint` to match the raw GPS ping to the pre-defined route geometry. This:
- Reduces "GPS noise" and jitter on the map.
- Calculates real-time progress (0.0 to 1.0) along the route.
- Enables "Off-Route" detection.

### Geofencing (Planned)
Future updates will include payloads for entering and exiting geofenced zones (Depots, Restricted Areas).
