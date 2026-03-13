# Fleet Management Routing Architecture (Free Stack)

> [!IMPORTANT]
> Review status: This document is a conceptual free-stack routing draft.
> For implementation-aligned plans consolidated with the current backend and frontend docs, use:
> - `docs/frontend-implementations/android-routing-integration-consolidated.md`
> - `docs/frontend-implementations/backend-backoffice-routing-consolidated.md`
>
> Notable mismatch in this draft: it references `POST /api/driver/route`, while the current implementation uses `/v1/tracking/*` and `WS /v1/fleet/live`.

## Overview

This document describes how the fleet management system calculates and displays routes using a **fully free stack**:

* Map tiles: OpenStreetMap
* Routing engine: Open Source Routing Machine
* Mobile map SDK: MapLibre
* Backend: Kotlin (Ktor)

This architecture avoids any paid services such as Google Maps SDK while still providing full navigation-like route polylines.

---

# System Components

## 1. Driver Mobile Application (Android)

Responsibilities:

* Display map using OpenStreetMap tiles
* Get driver GPS location
* Allow driver to choose destination
* Send route request to backend
* Render route polyline returned by backend

Recommended Android map SDK:

* MapLibre Android SDK (open-source)
* Supports vector tiles
* Supports GeoJSON layers
* Supports polylines

---

# Full Routing Flow

```
Driver opens mobile app
        ↓
App loads OpenStreetMap map
        ↓
App reads GPS location (current position)
        ↓
Driver taps destination on map
        ↓
Mobile app sends coordinates to backend
        ↓
Backend calls OSRM routing engine
        ↓
OSRM calculates road-following route
        ↓
Backend returns GeoJSON polyline
        ↓
Mobile app renders polyline on map
```

---

# Step 1 — Get Driver Current Location (Android)

The driver app retrieves the GPS location using Android's location services.

Example:

```kotlin
val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

fusedLocationClient.lastLocation.addOnSuccessListener { location ->
    val latitude = location.latitude
    val longitude = location.longitude
}
```

Example result:

```
Current Location
Latitude: 14.69910
Longitude: 121.10914
```

---

# Step 2 — Driver Selects Destination

The driver taps the map to select a destination.

MapLibre provides the coordinates of the tapped location.

Example destination:

```
Destination
Latitude: 14.60000
Longitude: 121.05000
```

---

# Step 3 — Send Route Request to Backend

The mobile app sends both coordinates to the backend.

Example API request:

```
POST /api/driver/route
```

Request body:

```json
{
  "driverId": "driver_123",
  "currentLocation": {
    "lat": 14.69910,
    "lon": 121.10914
  },
  "destination": {
    "lat": 14.60000,
    "lon": 121.05000
  }
}
```

---

# Step 4 — Backend Calls OSRM Route API

The Kotlin backend calls the OSRM routing API.

Example request:

```
GET http://router.project-osrm.org/route/v1/driving/
121.10914,14.69910;121.05000,14.60000
?overview=full&geometries=geojson
```

Parameters explained:

| Parameter          | Purpose               |
| ------------------ | --------------------- |
| route              | calculate route       |
| driving            | vehicle routing       |
| overview=full      | full polyline         |
| geometries=geojson | return GeoJSON format |

---

# Step 5 — OSRM Response

OSRM returns a road-following route.

Example response:

```json
{
  "routes": [
    {
      "distance": 11000,
      "duration": 960,
      "geometry": {
        "type": "LineString",
        "coordinates": [
          [121.10914,14.69910],
          [121.10890,14.69850],
          [121.10810,14.69720],
          [121.10500,14.69500],
          [121.10000,14.69000],
          [121.05000,14.60000]
        ]
      }
    }
  ]
}
```

Important:

The `coordinates` list represents the **polyline path along real roads**.

---

# Step 6 — Backend Returns Route to Mobile App

Backend response example:

```json
{
  "driverId": "driver_123",
  "route": {
    "type": "LineString",
    "coordinates": [
      [121.10914,14.69910],
      [121.10890,14.69850],
      [121.10810,14.69720],
      [121.10500,14.69500],
      [121.10000,14.69000],
      [121.05000,14.60000]
    ]
  }
}
```

This is **GeoJSON-ready for map rendering**.

---

# Step 7 — Draw Polyline on Android Map

The mobile app receives the coordinates and renders them as a polyline.

Example:

```kotlin
val lineOptions = LineOptions()

coordinates.forEach { coord ->
    val lon = coord[0]
    val lat = coord[1]
    lineOptions.add(LatLng(lat, lon))
}

map.addPolyline(lineOptions)
```

Result:

```
Driver ●
        \
         \
          \____
               \
                ○ Destination
```

The line follows **actual road paths**, not a straight line.

---

# Live Driver Tracking (Optional)

After the route is drawn:

1. Driver sends GPS updates every few seconds
2. Backend broadcasts location updates
3. Fleet dashboard moves the driver marker on the route

Example update interval:

```
Driver GPS update every 3 seconds
```

---

# Production Recommendation

The public OSRM server should **not be used for production**.

Recommended setup:

Run your own OSRM server using OpenStreetMap data.

Benefits:

* Unlimited routing requests
* Faster responses
* Full control of routing data
* No external dependency

---

# Final Architecture

```
Driver App (Android)
        ↓
OpenStreetMap Map
        ↓
Send start + destination
        ↓
Kotlin Backend (Ktor)
        ↓
OSRM Routing Engine
        ↓
GeoJSON Polyline
        ↓
Render Route on Mobile Map
```

---

# Stack Summary

| Component      | Technology    |
| -------------- | ------------- |
| Map tiles      | OpenStreetMap |
| Routing engine | OSRM          |
| Mobile map SDK | MapLibre      |
| Backend        | Kotlin + Ktor |
| Data format    | GeoJSON       |

All components are **open-source and free to use**.

---

# Result

Your fleet management system will have:

* driver navigation routes
* road-following polylines
* real-time vehicle tracking
* zero Google Maps cost
* full control of routing infrastructure

This is the same architecture used by many modern fleet tracking systems.
