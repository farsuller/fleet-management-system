# Feature: Coordinate Reception Toggle

## Overview

System-wide and per-vehicle toggle to enable/disable GPS coordinate reception from Android driver apps, providing administrators with both emergency control and granular driver management.

### Two Levels of Control

1. **Global Toggle** (System-Wide)
   - Emergency shutdown of all coordinate reception
   - Admin settings page
   - Use cases: System maintenance, privacy audits, security incidents

2. **Per-Vehicle Toggle** (Granular Control)
   - Selective control per driver/vehicle
   - Driver management table with toggle per row
   - Use cases: Driver off-duty, privacy requests, vehicle maintenance

### Toggle Hierarchy
```
Global Toggle (OFF) → All vehicles blocked, per-vehicle toggles disabled
Global Toggle (ON)  → Per-vehicle toggles active
  ├─ Vehicle A (ON)  → Coordinates accepted
  ├─ Vehicle B (OFF) → Coordinates blocked
  └─ Vehicle C (ON)  → Coordinates accepted
```

---

## Architecture

### Backend Components
- **TrackingConfigService**: Redis-based global configuration storage
- **VehicleTrackingConfigService**: PostgreSQL per-vehicle configuration
- **CoordinateReceptionGuard**: Validates both global and per-vehicle toggles
- **Admin API**: Global and per-vehicle endpoints

### Frontend Components
- **TrackingSettings**: Admin UI with global toggle switch (Compose for Web)
- **DriverManagementTable**: Table showing all drivers with per-vehicle toggles
- **TrackingSettingsViewModel**: Global state management
- **DriverManagementViewModel**: Driver table state management
- **FleetApiClient**: API integration

### Android Integration
- Handles `503 Service Unavailable` gracefully
- Shows user notification when disabled
- Auto-retry after 5 minutes

---

## Driver Management Table

### Columns
- **Driver Name** (with email)
- **Vehicle** (Make, Model, Plate)
- **Current Location** (with timestamp)
- **Route** (with status badge: In Transit/Idle/Off Route)
- **Coordinates** (lat/lng)
- **GPS Tracking Toggle** (per driver)

### Features
- Individual toggle per driver row
- Disabled when global toggle is OFF
- Confirmation dialog before toggling
- Real-time status updates
- Loading states per row

---
## Quick Start

### Enable/Disable via API

```bash
# Disable coordinate reception
curl -X POST http://localhost:8080/v1/admin/tracking/disable \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"reason": "System maintenance"}'

# Enable coordinate reception
curl -X POST http://localhost:8080/v1/admin/tracking/enable \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"reason": "Maintenance complete"}'

# Get current status
curl http://localhost:8080/v1/admin/tracking/config \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

### Frontend UI

Navigate to: `http://localhost:8080/admin/settings`

Toggle the "Coordinate Reception" switch with optional reason.

---

## Implementation Details

See [implementation_plan.md](file:///C:/Users/user/.gemini/antigravity/brain/a3ec7aeb-7ae5-4936-add8-4a046be26481/implementation_plan.md) for complete code examples.

---

## Security

### Access Control
- Admin-only access (JWT required)
- Full audit logging (who, when, why)
- Rate limiting (max 10 global changes/hour, 50 vehicle changes/hour)

### Database Protection & Cost Control

**Preventing Malicious/Faulty Coordinate Flooding**:

This feature provides **instant protection** against database abuse without requiring code deployment:

#### Use Case 1: Compromised Device
- **Problem**: Driver's phone is hacked, sends fake GPS coordinates continuously
- **Impact**: Database bloat, increased storage costs, data pollution
- **Solution**: Disable per-vehicle toggle for that driver immediately
- **Result**: Stops garbage data instantly, preserves database integrity

#### Use Case 2: Faulty GPS Sensor
- **Problem**: Vehicle GPS malfunctions, sends random/invalid coordinates
- **Impact**: Pollutes location history, breaks route tracking analytics
- **Solution**: Disable per-vehicle toggle until hardware is repaired
- **Result**: Prevents invalid data from corrupting analytics

#### Use Case 3: DDoS Attack
- **Problem**: Multiple devices flood system with coordinate requests
- **Impact**: Database overload, increased costs, performance degradation
- **Solution**: Disable global toggle temporarily
- **Result**: Stops all coordinate ingestion, protects infrastructure

#### Use Case 4: Testing/Development
- **Problem**: QA team testing with simulated coordinates
- **Impact**: Test data mixed with production data
- **Solution**: Disable per-vehicle toggle for test vehicles
- **Result**: Clean separation of test and production data

**Cost Savings**: Prevents unnecessary database writes, reduces storage costs, and protects against malicious expense inflation.

---

## Observability

**Metrics**:
- `tracking.config.changes` - Configuration change count
- `tracking.reception.enabled` - Current state (0 or 1)
- `tracking.coordinates.rejected` - Rejected coordinate count

**Alerts**:
- Warning if disabled for >1 hour
- Critical if >100 coordinates/min rejected

---

## Testing

```bash
# Backend tests
./gradlew :tracking:test --tests "*CoordinateReceptionGuardTest"

# Frontend tests
./gradlew :web:jsTest --tests "*TrackingSettingsTest"

# Integration tests
./gradlew :tracking:integrationTest --tests "*TrackingConfigIntegrationTest"
```

---

## References

- [Implementation Plan](file:///C:/Users/user/.gemini/antigravity/brain/a3ec7aeb-7ae5-4936-add8-4a046be26481/implementation_plan.md)
- [Phase 6 - PostGIS](file:///e:/Antigravity%20Projects/fleet-management/docs/implementations/phase-6-postgis-spatial-extensions.md)
- [Phase 7 - Backend WebSocket](file:///e:/Antigravity%20Projects/fleet-management/docs/implementations/phase-7-schematic-visualization-engine.md)
- [Web Frontend - Schematic Visualization](file:///e:/Antigravity%20Projects/fleet-management/docs/frontend-implementations/web-schematic-visualization.md)
- [Android Driver App](file:///e:/Antigravity%20Projects/fleet-management/docs/frontend-implementations/android-driver-app.md)
