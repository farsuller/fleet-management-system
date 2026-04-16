# Live Tracking Page — Enhancement Plan

This plan has been reviewed against the current codebase in both repositories:

- `fleet-management` backend: Kotlin + Ktor modular monolith
- `Fleet Management BackOffice` client: Kotlin Multiplatform + Compose web back office

The goal is to increase operational context density on the Live Tracking screen without replacing or breaking any of the tracking infrastructure already in production.

---

## 1. Current Baseline Confirmed in the Codebase

The Live Tracking module is fully implemented end-to-end. Every layer of the stack is active.

### Backend capabilities confirmed

- `POST /v1/sensors/ping` — rate-limited (60/min), idempotent batch sensor ingestion
- `WS /v1/fleet/live?token=<JWT>` — Redis-backed delta broadcast via `RedisDeltaBroadcaster`
- `GET /v1/tracking/fleet/status` — fleet counts + `List<VehicleStatusSummary>` with plate, make, model, state, speed, progress, lat/lon/heading
- `GET /v1/tracking/routes/active` — all active `RouteDto` with WKT linestring, name, vehicleId, startedAt, endedAt
- `GET /v1/vehicles/{vehicleId}/driver` — active `VehicleDriverAssignment` including `DriverDto` fields
- `GET /v1/rentals?vehicleId={id}&status=ACTIVE` — active rental with denormalized customer name and customerId on `RentalDto`

### Back-office capabilities confirmed

- `FleetLiveClient` — WebSocket client, auto-reconnects up to 5 attempts with exponential backoff
- `FleetTrackingViewModel` — `StateFlow` over `fleetState: Map<String, VehicleRouteState>`, `fleetStatus: FleetStatusDto?`, `selectedVehicleId: String?`, `routesState`, `connectionState`
- `FleetMapCanvas` — three-layer canvas: OSM tiles → route polylines → animated car markers; `onVehicleClick` already wired and surfaces `vehicleId`
- `LiveTrackingScreen` — `Row { FleetMapCanvas(weight 1) | Sidebar(240dp) }` with `ConnectionStatusBar` footer
- Sidebar vehicle cards already render plate number and state dot; selection sets `selectedVehicleId` in the ViewModel

### Important constraints identified

- `RentalDto` has no `startLocation` or `endLocation` field. Route origin/destination information comes exclusively from `RouteDto` (WKT linestring stored in PostGIS). The first and last coordinate of the linestring represent start and end points.
- `RentalDto` has no `driverId`. Driver assignment is a separate concern managed through `VehicleDriverAssignment`.
- Vehicle has no `isRental` flag. Rental involvement is expressed via `VehicleState.RENTED`.
- `VehicleStatusSummary` (used by sidebar) carries only `vehicleId`, `licensePlate`, `make`, `model`, `status`, `speed`, `progress`, `distanceFromRoute`, `lat`, `lon`, `heading`. Rental and driver data require additional API calls.

---

## 2. Requested Enhancements

### Enhancement 1 — Vehicle Detail Bottom Sheet

**Trigger**: selecting a vehicle from the sidebar panel or clicking a car icon on the map canvas.

Today, `selectedVehicleId` is already set in the ViewModel on both entry points. The UI does not yet act on this selection beyond highlighting the sidebar card.

The bottom sheet should slide up from the bottom of the `LiveTrackingScreen` layout when `selectedVehicleId != null` and close when dismissed or when selection is cleared.

#### Bottom sheet sections

**Section A — Vehicle Details**

| Field | Source |
|---|---|
| Make, Model, Year | `VehicleDto` via `GET /v1/vehicles/{id}` |
| License Plate | `VehicleStatusSummary.licensePlate` (already in memory) |
| Color | `VehicleDto.color` |
| Vehicle Type | `VehicleDto.vehicleType` (SEDAN, SUV, VAN, TRUCK, BUS, MOTORCYCLE, etc.) |
| State | `VehicleStatusSummary.status` or `VehicleDto.state` |
| Odometer | `VehicleDto.mileageKm` |
| Daily Rate | `VehicleDto.dailyRateAmount` + `currencyCode` |

> `VehicleDto` is already used across the back-office codebase via `VehicleRepository.getVehicle(id)`. No new endpoint needed.

**Section B — Assigned Driver**

Populated only when an active assignment exists. Call `GET /v1/vehicles/{vehicleId}/driver`.

This endpoint maps to `DriverRepository.getVehicleActiveDriver(vehicleId)` which already exists in the back-office repository. No new infrastructure needed.

| Field | Source |
|---|---|
| Full Name | `DriverDto.firstName + lastName` |
| License Number | `DriverDto.licenseNumber` |
| License Class | `DriverDto.licenseClass` |
| Phone | `DriverDto.phone` |
| Status | `AssignmentDto.isActive` |

Show a "No driver assigned" empty state when the call returns no active assignment.

**Section C — Active Rental and Customer** *(conditional: only shown when `VehicleState == RENTED`)*

Call `GET /v1/rentals?vehicleId={id}&status=ACTIVE` and take the first result. `RentalDto` carries denormalized `customerName`, `vehiclePlateNumber`, `vehicleMake`, `vehicleModel`, `dailyRate`, `totalCost`, and `currencyCode`.

| Field | Source |
|---|---|
| Rental Number | `RentalDto.rentalNumber` |
| Customer Name | `RentalDto.customerName` |
| Rental Status | `RentalDto.status` |
| Start Date | `RentalDto.startDate` |
| End Date | `RentalDto.endDate` |
| Daily Rate | `RentalDto.dailyRate` + `currencyCode` |
| Total Cost | `RentalDto.totalCost` |

Show a "Not currently rented" empty state when the vehicle is not in `RENTED` state. Do not make the API call in that case.

**Section D — Active Route**

Match `fleetState[vehicleId].routeId` against the in-memory `routesState` list. No additional API call needed if routes are already loaded.

| Field | Source |
|---|---|
| Route Name | `RouteDto.name` |
| Started At | `RouteDto.startedAt` |
| Start Point | First coordinate from `RouteDto.lineString` WKT (parsed by existing `SvgUtils`) |
| End Point | Last coordinate from `RouteDto.lineString` WKT |
| Progress | `VehicleRouteState.routeProgress` (0.0–1.0, already in `fleetState`) |
| Distance from Route | `VehicleStatusSummary.distanceFromRoute` |
| Current Speed | `VehicleRouteState.speedKph` |
| Heading | `VehicleRouteState.headingDeg` |

When no `routeId` is present, show "No active route assigned."

**Section E — Sensor Telemetry** *(optional, collapsible)*

Fields already flowing through `VehicleRouteState` and `VehicleStateDelta` from the WebSocket stream.

| Field | Source |
|---|---|
| Battery Level | `VehicleRouteState.batteryLevel` |
| Harsh Brake | `VehicleRouteState.harshBrake` |
| Harsh Acceleration | `VehicleRouteState.harshAccel` |
| Sharp Turn | `VehicleRouteState.sharpTurn` |

---

### Enhancement 2 — Sidebar Panel Badges

Currently each sidebar vehicle card shows only the license plate and a state color dot. The following additions should sit inline on each card without increasing card height significantly.

#### Rental badge

When `VehicleStatusSummary.status == RENTED`, render a compact pill-style badge labeled **"RENTAL"** in the accent color next to the plate number. This requires no API call — `status` is already in the fleet status snapshot.

#### Vehicle type icon

Render a small icon or abbreviated label for `vehicleType` (available in `VehicleDto` via the fleet status list). If `VehicleStatusSummary` does not yet carry `vehicleType`, this field should be added to `FleetStatusDto`/`VehicleStatusSummary` on the backend and to the corresponding DTO on the front end.

#### Speed inline

Render current speed in km/h from `fleetState[vehicleId].speedKph` inline below the plate. Already in memory. Shows `—` when vehicle is offline.

#### State label alignment

Replace the state dot with a small colored label chip (e.g. AVAILABLE, RENTED, MAINTENANCE, RETIRED) to reduce reliance on color alone for accessibility.

---

## 3. ViewModel Changes Required

All new data is fetched lazily when `selectedVehicleId` changes. No bulk pre-loading.

```
selectedVehicleId changes
  └─ launch coroutine
       ├─ VehicleRepository.getVehicle(id) → selectedVehicleDetail: VehicleDto?
       ├─ DriverRepository.getVehicleActiveDriver(id) → selectedVehicleDriver: DriverDto?
       └─ if (state == RENTED)
            └─ RentalRepository.getRentals(vehicleId = id, status = ACTIVE) → selectedVehicleRental: RentalDto?
```

New `StateFlow` fields to add to `FleetTrackingViewModel`:

| Field | Type | Purpose |
|---|---|---|
| `selectedVehicleDetail` | `UiState<VehicleDto>` | Full vehicle record for bottom sheet section A |
| `selectedVehicleDriver` | `UiState<DriverDto?>` | Active driver or null for section B |
| `selectedVehicleRental` | `UiState<RentalDto?>` | Active rental or null for section C |
| `isBottomSheetVisible` | `Boolean` | Controls sheet slide-in/out |

Cancel and re-launch on each `selectedVehicleId` change. Use `viewModelScope.launch` with `Job` cancellation to avoid stale responses from the previous selection.

---

## 4. New Composable Components Required

| Component | Location | Responsibility |
|---|---|---|
| `VehicleDetailBottomSheet` | `webMain/.../tracking/components/` | Root sheet container. Shows sections A–E. Wired to `isBottomSheetVisible`. |
| `VehicleInfoSection` | same | Section A: vehicle make/model/year/plate/color/type/state/odometer |
| `DriverInfoSection` | same | Section B: driver name/license/phone or empty state |
| `RentalInfoSection` | same | Section C: rental number/customer/dates/rate or empty state |
| `RouteInfoSection` | same | Section D: route name/start-end points/progress bar/speed |
| `SensorTelemetrySection` | same | Section E: battery/harsh event flags, collapsible |
| `VehicleTypeBadge` | same | Reusable pill for type or rental badge on sidebar card |

---

## 5. Backend Changes Required

### 5a. `VehicleStatusSummary` — add `vehicleType`

`vehicleType` is present on the `Vehicle` domain model. It is not currently projected into `VehicleStatusSummary`. This field is needed for the sidebar vehicle type icon without requiring a per-vehicle GET.

**Backend change**: add `vehicleType: VehicleType` to `VehicleStatusSummary` DTO and update the query projection in the tracking service.

**Front-end change**: add `vehicleType: String` (or a `VehicleType` enum) to `VehicleStatusSummary` in `TrackingDtos.kt`.

### 5b. No other backend changes required

- `GET /v1/vehicles/{vehicleId}/driver` already exists
- `GET /v1/rentals?vehicleId=&status=ACTIVE` already exists
- `GET /v1/vehicles/{id}` already exists
- Route start/end extraction from WKT is handled client-side by the existing `SvgUtils.parseLineString()`

---

## 6. Additional Suggestions

These are enhancements not explicitly requested but architecturally aligned and low-risk to implement.

### Suggestion 1 — Follow Vehicle Mode

Add a "Follow" toggle to the bottom sheet. When active, `FleetTrackingViewModel` pipes each incoming `VehicleStateDelta` for the selected vehicle into `mapState` via `MapViewState.panned()` to keep the vehicle centered. Toggle off resumes free pan. This uses only existing ViewModel and MapProjection infrastructure.

### Suggestion 2 — Route Highlight on Selection

When a vehicle with an active `routeId` is selected, re-render its polyline in a distinct highlight color on `FleetMapCanvas`. All route polylines are already drawn on the canvas using WKT → `MapProjection.toCanvasXY`. Selection highlight requires passing `selectedVehicleId` into the canvas and conditionally changing stroke color for the matching `RouteDto.vehicleId`.

### Suggestion 3 — Location History Breadcrumb Trail

On vehicle selection, call `GET /v1/tracking/vehicles/{vehicleId}/history?limit=50` (already implemented in `TrackingRepository.getLocationHistory()`). Render the returned coordinates as a faded polyline trail behind the current vehicle position on the canvas. Cache locally per `selectedVehicleId` in the ViewModel to avoid repeated fetches.

### Suggestion 4 — Harsh Event Alert Badges on Sidebar Cards

`VehicleStateDelta.harshBrake`, `harshAccel`, and `sharpTurn` are already flowing through the WebSocket stream and stored in `fleetState`. Add small warning indicators (e.g. a red exclamation chip) on the sidebar card when the latest delta carries a harsh event flag. Auto-clear after 30 seconds using a timestamp comparison with `VehicleRouteState.timestamp`.

### Suggestion 5 — Offline Duration Indicator

For vehicles showing `Offline` status in the sidebar, compute elapsed time since the last seen `VehicleRouteState.timestamp`. Display as "Offline 12m" or "Offline 2h" inline on the card. Drives faster operational awareness without any API call — all data already in `fleetState`.

### Suggestion 6 — Route Progress Bar on Sidebar Card

`VehicleRouteState.routeProgress` (0.0–1.0) is already in memory. Render a thin horizontal progress bar at the bottom of each sidebar card for vehicles that have an active `routeId`. Zero-width for vehicles with no route. Mirrors the dashboard pattern already used for fleet status distribution.

---

## 7. Implementation Phases

### Phase 1 — Sidebar Badge Layer *(low-risk, no new API calls)*

- Add `vehicleType` to `VehicleStatusSummary` (backend + DTO sync)
- Add **RENTAL** badge on sidebar cards based on `status == RENTED`
- Replace state dot with state label chip
- Add speed inline from `fleetState`

### Phase 2 — Vehicle Detail Bottom Sheet *(lazy data, no bulk loading)*

- Add `selectedVehicleDetail`, `selectedVehicleDriver`, `selectedVehicleRental` flows to ViewModel
- Implement `VehicleDetailBottomSheet` with sections A through D
- Wire map click and sidebar card click to open sheet

### Phase 3 — Sensor Telemetry and Suggestions *(low-risk, data already in memory)*

- Add collapsible section E to the bottom sheet
- Implement Follow Vehicle mode
- Implement route highlight on selection
- Implement harsh event badges on sidebar cards
- Implement offline duration indicator

### Phase 4 — Location History Breadcrumb Trail *(requires additional API call)*

- Add `getLocationHistory` call on selection to ViewModel
- Render faded breadcrumb polyline on `FleetMapCanvas`
- Add caching by `selectedVehicleId` to avoid redundant fetches

---

## 8. Design Constraints

- Bottom sheet must not obscure the map entirely on the typical 1920×1080 fixed-lens viewport. A collapsed height of 280–320dp exposing map above it is recommended.
- All lazy data fetches must be scoped to the current `selectedVehicleId` and cancelled on deselection to prevent stale state.
- Sensor telemetry section E should be collapsed by default to avoid overwhelming operators who only need location and context.
- Sidebar badge additions must not increase card height — use inline chips within the existing card row layout.
- Do not pre-fetch driver or rental data for vehicles that are not selected. The full vehicle list can be large. Fetch on demand only.
