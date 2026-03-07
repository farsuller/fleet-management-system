# Tracking Module - Sample Payloads

This document details the tracking and spatial features, including real-time vehicle location updates, route snapping (Digital Rails), and Phase 7 real-time vehicle broadcasting.

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
  "routeId": "7b8e1e5b-...",
  "speed": 45.5,
  "heading": 180.0,
  "accuracy": 5.0
}
```

**Response**: `200 OK`
```json
{
  "success": true,
  "data": {
    "latitude": 14.6001,
    "longitude": 121.0250,
    "progress": 0.42,
    "status": "IN_TRANSIT",
    "distanceFromRoute": 8.5
  },
  "requestId": "req_..."
}
```
> [!NOTE]
> The response returns the "snapped" location if a `routeId` was provided and snapping was successful. Otherwise, it returns the raw location provided in the request.
> Phase 7 addition: Response now includes `progress`, `status`, and `distanceFromRoute` for real-time tracking.

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
      "id": "7b8e1e5b-550e-4e2f-8c3a-9d2e1f3c4b5a",
      "name": "Village Main Loop",
      "description": "Core route through the main village streets",
      "distance": 12500.0,
      "waypoints": 15
    }
  ],
  "requestId": "req_..."
}
```

## Phase 7 - Real-Time Vehicle Tracking

### Get Current Vehicle State
**Endpoint**: `GET /v1/tracking/vehicles/{vehicleId}/state`
**Context**: Retrieve the current complete state of a specific vehicle including all Phase 7 tracking data.
**Permissions**: Authenticated

**Response**: `200 OK`
```json
{
  "success": true,
  "data": {
    "vehicleId": "0a8e652a-604d-4cfb-81c6-e14eccc80ac8",
    "routeId": "68a1a7f1-76dd-4ec9-ad63-fefc22acf428",
    "progress": 0.42,
    "segmentId": "seg-101",
    "speed": 45.5,
    "heading": 180.0,
    "status": "IN_TRANSIT",
    "distanceFromRoute": 8.5,
    "location": {
      "latitude": 14.6001,
      "longitude": 121.0250
    },
    "timestamp": "2026-03-07T14:32:15Z"
  },
  "requestId": "req_abc123def456"
}
```

### Get Fleet Real-Time Status
**Endpoint**: `GET /v1/tracking/fleet/status`
**Context**: Retrieve current state of all vehicles in the fleet with their Phase 7 tracking metrics.
**Permissions**: Authenticated (Admin/Manager)

**Response**: `200 OK`
```json
{
  "success": true,
  "data": {
    "totalVehicles": 5,
    "activeVehicles": 4,
    "vehicles": [
      {
        "vehicleId": "0a8e652a-604d-4cfb-81c6-e14eccc80ac8",
        "routeId": "68a1a7f1-76dd-4ec9-ad63-fefc22acf428",
        "status": "IN_TRANSIT",
        "speed": 45.5,
        "progress": 0.42,
        "distanceFromRoute": 8.5,
        "timestamp": "2026-03-07T14:32:15Z"
      },
      {
        "vehicleId": "1b9f763b-715e-5dg0-92d7-f25fdddd91bd9",
        "routeId": "68a1a7f1-76dd-4ec9-ad63-fefc22acf428",
        "status": "IDLE",
        "speed": 0.0,
        "progress": 0.65,
        "distanceFromRoute": 2.1,
        "timestamp": "2026-03-07T14:32:10Z"
      }
    ]
  },
  "requestId": "req_fleet_status_001"
}
```

### Get Vehicle Tracking History
**Endpoint**: `GET /v1/tracking/vehicles/{vehicleId}/history?limit=100&offset=0`
**Context**: Retrieve historical tracking data for a specific vehicle over a time period.
**Permissions**: Authenticated (Admin/Manager)

**Response**: `200 OK`
```json
{
  "success": true,
  "data": {
    "vehicleId": "0a8e652a-604d-4cfb-81c6-e14eccc80ac8",
    "totalRecords": 1250,
    "records": [
      {
        "id": "track_001",
        "progress": 0.42,
        "speed": 45.5,
        "heading": 180.0,
        "status": "IN_TRANSIT",
        "distanceFromRoute": 8.5,
        "location": {
          "latitude": 14.6001,
          "longitude": 121.0250
        },
        "timestamp": "2026-03-07T14:32:15Z"
      },
      {
        "id": "track_002",
        "progress": 0.40,
        "speed": 40.0,
        "heading": 180.0,
        "status": "IN_TRANSIT",
        "distanceFromRoute": 5.2,
        "location": {
          "latitude": 14.5950,
          "longitude": 121.0200
        },
        "timestamp": "2026-03-07T14:31:15Z"
      }
    ]
  },
  "requestId": "req_history_001"
}
```

### WebSocket Connection (Live Fleet Tracking)
**Endpoint**: `WS /v1/fleet/live`
**Context**: Establishes a WebSocket connection to receive real-time vehicle state updates and delta-encoded position changes for all vehicles in the fleet.
**Permissions**: Authenticated (JWT Bearer Token)
**Protocol**: WebSocket (WSS recommended for HTTPS)

**Connection Request** (via Postman WebSocket):
```
wss://api.fleet-management.com/v1/fleet/live
Headers:
  Authorization: Bearer {JWT_TOKEN}
```

**Initial State Message** (Received after connection):
```json
{
  "type": "initial_state",
  "data": [
    {
      "vehicleId": "v-123",
      "routeId": "7b8e1e5b-550e-4e2f-8c3a-9d2e1f3c4b5a",
      "progress": 0.42,
      "segmentId": "seg-101",
      "speed": 45.5,
      "heading": 180.0,
      "status": "IN_TRANSIT",
      "distanceFromRoute": 8.5,
      "timestamp": "2026-03-07T14:32:15Z"
    }
  ],
  "timestamp": "2026-03-07T14:32:15Z"
}
```

### Live Vehicle State Update (Delta Message)
**Context**: Broadcasted when a vehicle's state changes. Only changed fields are included (delta encoding).
**Received from**: `WS /v1/fleet/live`
**Frequency**: Real-time as vehicles move

**Sample Delta Update** (Progress changed):
```json
{
  "vehicleId": "v-123",
  "progress": 0.45,
  "bearing": 180.0,
  "timestamp": "2026-03-07T14:32:22Z"
}
```

> [!NOTE]
> Delta message includes only changed fields. `null` fields mean no change from previous state.

**Sample Delta Update** (Speed and Status changed):
```json
{
  "vehicleId": "v-124",
  "status": "IDLE",
  "speed": 0.0,
  "timestamp": "2026-03-07T14:32:25Z"
}
```

**Sample Delta Update** (Off-route detection):
```json
{
  "vehicleId": "v-125",
  "status": "OFF_ROUTE",
  "distanceFromRoute": 150.5,
  "timestamp": "2026-03-07T14:32:30Z"
}
```

### Full Vehicle State Update
**Context**: Complete vehicle state snapshot sent when initially connecting or when full state sync is needed.
**Received from**: `WS /v1/fleet/live`

**Full State Message**:
```json
{
  "vehicleId": "v-123",
  "routeId": "7b8e1e5b-550e-4e2f-8c3a-9d2e1f3c4b5a",
  "progress": 0.45,
  "segmentId": "seg-101",
  "speed": 45.5,
  "heading": 180.0,
  "status": "IN_TRANSIT",
  "distanceFromRoute": 8.5,
  "timestamp": "2026-03-07T14:32:22Z"
}
```

### Client Heartbeat (Ping)
**Context**: Send periodic heartbeat to keep WebSocket connection alive.
**Direction**: Client → Server

**Ping Frame**:
```
PING
```

**Server Response** (Pong Frame):
```
PONG
```

### WebSocket Connection Close
**Context**: Gracefully close the WebSocket connection.
**Direction**: Client → Server

**Close Frame**:
```
1000 (Normal Closure)
```

## Sensor Ping Payload (GPS Telemetry)

### Send Raw GPS Ping with Telemetry
**Endpoint**: `POST /v1/tracking/vehicles/{vehicleId}/location`
**Context**: Sends complete GPS telemetry including speed, heading, and accuracy for real-time tracking.
**Permissions**: Authenticated Vehicles/Drivers

**Request**:
```json
{
  "latitude": 14.5995,
  "longitude": 121.0244,
  "routeId": "7b8e1e5b-550e-4e2f-8c3a-9d2e1f3c4b5a",
  "speed": 45.5,
  "heading": 180.0,
  "accuracy": 5.0
}
```

**Response**: `200 OK` - Location processed and broadcasted via WebSocket
```json
{
  "success": true,
  "data": {
    "message": "Location update processed successfully",
    "vehicleId": "c9352986-639a-4841-bed9-9ff99f2e3349",
    "timestamp": "2026-03-07T14:32:15Z",
    "progress": "Tracking active - check WebSocket for real-time updates"
  },
  "requestId": "req_abc123def456"
}
```

> [!IMPORTANT]
> Phase 7 Enhancement: The location update is processed asynchronously. The response confirms receipt, but the actual vehicle state updates are **broadcasted via WebSocket** to all connected clients in real-time. 
> 
> **To see the vehicle state update:**
> 1. Connect to `WS /v1/fleet/live` WebSocket
> 2. Send location update via this endpoint
> 3. Monitor WebSocket for delta message with updated `progress`, `speed`, `status`, etc.

### Stationary Vehicle (Speed = 0)
**Context**: When vehicle speed is below 5.0 m/s, status changes to IDLE.

**Request**:
```json
{
  "latitude": 14.5995,
  "longitude": 121.0244,
  "routeId": "7b8e1e5b-550e-4e2f-8c3a-9d2e1f3c4b5a",
  "speed": 0.0,
  "heading": null,
  "accuracy": 3.0
}
```

**Response**: `200 OK`
```json
{
  "success": true,
  "data": {
    "vehicleId": "v-123",
    "routeId": "7b8e1e5b-550e-4e2f-8c3a-9d2e1f3c4b5a",
    "progress": 0.42,
    "segmentId": "seg-101",
    "speed": 0.0,
    "heading": 0.0,
    "status": "IDLE",
    "distanceFromRoute": 2.1,
    "timestamp": "2026-03-07T14:35:22Z"
  },
  "requestId": "req_xyz789uvw012"
}
```

### Off-Route Detection (>100m from route)
**Context**: When vehicle is more than 100 meters from the route, status changes to OFF_ROUTE.

**Request**:
```json
{
  "latitude": 14.7100,
  "longitude": 121.1500,
  "routeId": "7b8e1e5b-550e-4e2f-8c3a-9d2e1f3c4b5a",
  "speed": 30.5,
  "heading": 270.0,
  "accuracy": 8.0
}
```

**Response**: `200 OK`
```json
{
  "success": true,
  "data": {
    "vehicleId": "v-125",
    "routeId": "7b8e1e5b-550e-4e2f-8c3a-9d2e1f3c4b5a",
    "progress": 0.65,
    "segmentId": "seg-105",
    "speed": 30.5,
    "heading": 270.0,
    "status": "OFF_ROUTE",
    "distanceFromRoute": 152.3,
    "location": {
      "latitude": 14.7100,
      "longitude": 121.1500
    },
    "timestamp": "2026-03-07T14:40:45Z"
  },
  "requestId": "req_off_route_001"
}
```

> [!WARNING]
> Off-route status triggers alerts and may stop real-time tracking until vehicle returns within tolerance.

### GPS Validation Error (Invalid Speed)
**Context**: Speed exceeds maximum allowed (>100 m/s or ~360 km/h).
**Status**: `400 Bad Request`

**Request**:
```json
{
  "latitude": 14.5995,
  "longitude": 121.0244,
  "routeId": "7b8e1e5b-550e-4e2f-8c3a-9d2e1f3c4b5a",
  "speed": 150.0,
  "heading": 180.0,
  "accuracy": 5.0
}
```

**Response**:
```json
{
  "success": false,
  "error": {
    "code": "INVALID_SENSOR_DATA",
    "message": "Speed validation failed: 150.0 m/s exceeds maximum (100.0 m/s)",
    "field": "speed"
  },
  "requestId": "req_validation_001"
}
```

### GPS Validation Error (Invalid Heading)
**Context**: Heading exceeds valid range (0-360 degrees).
**Status**: `400 Bad Request`

**Request**:
```json
{
  "latitude": 14.5995,
  "longitude": 121.0244,
  "routeId": "7b8e1e5b-550e-4e2f-8c3a-9d2e1f3c4b5a",
  "speed": 45.5,
  "heading": 400.0,
  "accuracy": 5.0
}
```

**Response**:
```json
{
  "success": false,
  "error": {
    "code": "INVALID_SENSOR_DATA",
    "message": "Heading validation failed: 400.0° exceeds maximum (360°)",
    "field": "heading"
  },
  "requestId": "req_validation_002"
}
```

## Error Responses

### Vehicle Not Found
**Status**: `404 Not Found`

**Response**:
```json
{
  "success": false,
  "error": {
    "code": "VEHICLE_NOT_FOUND",
    "message": "Vehicle not found: v-nonexistent",
    "id": "v-nonexistent"
  },
  "requestId": "req_..."
}
```

### Route Not Found
**Status**: `404 Not Found`

**Response**:
```json
{
  "success": false,
  "error": {
    "code": "ROUTE_NOT_FOUND",
    "message": "Route not found: 7b8e1e5b-invalid",
    "routeId": "7b8e1e5b-invalid"
  },
  "requestId": "req_..."
}
```

### Coordinate Reception Disabled
**Status**: `503 Service Unavailable`

**Response**:
```json
{
  "success": false,
  "error": {
    "code": "COORDINATE_RECEPTION_DISABLED",
    "message": "Coordinate reception is currently disabled globally or for this vehicle",
    "vehicleId": "v-123"
  },
  "requestId": "req_..."
}
```

### WebSocket Authentication Failed
**Status**: `401 Unauthorized`

**Response**:
```json
{
  "success": false,
  "error": {
    "code": "UNAUTHORIZED",
    "message": "Invalid or expired JWT token",
    "endpoint": "/v1/fleet/live"
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
- Feeds into Phase 7 real-time delta broadcasting.

### Real-Time Delta Encoding (Phase 7)
The WebSocket broadcasts only changed vehicle state fields to minimize payload size:
- **Full State**: Sent on initial connection or state sync (~50-60 bytes)
- **Delta Update**: Only changed fields sent (~15-25 bytes)
- **Efficiency Target**: >80% reduction in broadcast size

### Vehicle Status States
| Status | Condition | Action |
|--------|-----------|--------|
| `IN_TRANSIT` | Speed ≥ 5.0 m/s AND distance ≤ 100m | Normal tracking |
| `IDLE` | Speed < 5.0 m/s AND distance ≤ 100m | Stationary alert |
| `OFF_ROUTE` | Distance > 100m from route | Geofence alert |

### Geofencing (Planned)
Future updates will include payloads for entering and exiting geofenced zones (Depots, Restricted Areas).

## Postman Testing Guide

### 1. HTTP Location Update
```
Method: POST
URL: http://localhost:8080/v1/tracking/vehicles/v-123/location
Headers:
  Authorization: Bearer {JWT_TOKEN}
  Content-Type: application/json
Body:
{
  "latitude": 14.5995,
  "longitude": 121.0244,
  "routeId": "7b8e1e5b-550e-4e2f-8c3a-9d2e1f3c4b5a",
  "speed": 45.5,
  "heading": 180.0,
  "accuracy": 5.0
}
```

### 2. WebSocket Live Fleet Connection
```
Method: WebSocket
URL: ws://localhost:8080/v1/fleet/live
Headers:
  Authorization: Bearer {JWT_TOKEN}
```

**Steps in Postman:**
1. Create new request
2. Select "WebSocket" request type
3. Enter URL: `ws://localhost:8080/v1/fleet/live`
4. Go to "Authorization" tab → Type: Bearer Token → Enter JWT
5. Click "Connect"
6. Monitor incoming messages in the "Messages" panel
7. Trigger location updates via HTTP endpoint to see delta messages
8. Click "Send" for Ping frame (keep-alive)

### 3. Test Off-Route Scenario
1. Send location update with `distanceFromRoute > 100m`
2. Observe WebSocket message with `status: "OFF_ROUTE"`
3. Verify delta message broadcasts `distanceFromRoute` value

### 4. Test Idle Scenario
1. Send location update with `speed: 0.0`
2. Observe response with `status: "IDLE"`
3. Verify WebSocket broadcasts status change
