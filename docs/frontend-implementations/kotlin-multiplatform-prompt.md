# Fleet Management UI Implementation Prompt

You are a senior Kotlin/Compose Multiplatform engineer.

Design a **complete UI implementation plan** for the **Fleet Management Platform** using **Kotlin Multiplatform and Compose Multiplatform**.

The UI connects to a **fully implemented Ktor + PostgreSQL + Redis backend** running at a known base URL. Your task is to build only the client layer — do not design a new backend.

The system supports three user types based on backend-defined roles:

- **Backoffice users** — roles: `ADMIN`, `FLEET_MANAGER`, `CUSTOMER_SUPPORT`, `RENTAL_AGENT`
- **Drivers** — role: `DRIVER`
- **Customers** — role: `CUSTOMER`

---

# Platforms

## 1. Backoffice Web Portal

Built using **Kotlin/JS (IR target) + Compose for Web**.

Users: administrators and fleet operators who manage the fleet day-to-day.

## 2. Mobile App (Unified)

Built using **Kotlin Multiplatform + Compose Multiplatform**.

Targets: **Android** and **iOS**.

**Single binary with role-based UI dispatching.** On login the JWT roles determine which experience is activated:

- If roles contain `DRIVER` → Driver Dashboard (sensor telemetry, foreground service, GPS tracking)
- If roles contain only `CUSTOMER` → Customer Rental experience (read-heavy, map polling)
- If roles contain both → app prompts the user to choose a mode

The `AppDependencyDispatcher` activates the appropriate feature set and requests permissions contextually:
- **Driver mode**: requests high-accuracy background GPS, accelerometer, and gyroscope on shift start
- **Customer mode**: requests minimal GPS only (find nearby vehicles)

This avoids two separate APK/IPA binaries while keeping sensitive driver telemetry code behind a role gate.

---

# Backend API Reference

> The backend is live. All UI must integrate against these exact endpoints.

## Authentication
| Method | Endpoint | Notes |
|--------|----------|-------|
| POST | `/v1/users/register` | Returns 201 on success |
| POST | `/v1/users/login` | Returns `{ token, user }` |
| GET | `/v1/auth/verify?token=...` | Email verification |

JWT is returned on login. All protected endpoints require `Authorization: Bearer <token>`.

## Users
| Method | Endpoint | Roles |
|--------|----------|-------|
| GET | `/v1/users` | ADMIN |
| GET | `/v1/users/roles` | ADMIN |
| GET | `/v1/users/{id}` | auth-jwt |
| PATCH | `/v1/users/{id}` | auth-jwt |
| DELETE | `/v1/users/{id}` | ADMIN |
| POST | `/v1/users/{id}/roles` | ADMIN |

## Vehicles
| Method | Endpoint | Roles |
|--------|----------|-------|
| GET | `/v1/vehicles` | auth-jwt |
| POST | `/v1/vehicles` | ADMIN, FLEET_MANAGER |
| GET | `/v1/vehicles/{id}` | ADMIN, FLEET_MANAGER |
| PATCH | `/v1/vehicles/{id}` | ADMIN, FLEET_MANAGER |
| DELETE | `/v1/vehicles/{id}` | ADMIN, FLEET_MANAGER |
| PATCH | `/v1/vehicles/{id}/state` | ADMIN, FLEET_MANAGER |
| POST | `/v1/vehicles/{id}/odometer` | ADMIN, FLEET_MANAGER |

**Vehicle states**: `AVAILABLE` → `RENTED` or `MAINTENANCE` → `AVAILABLE` → `RETIRED`

## Rentals
| Method | Endpoint | Roles |
|--------|----------|-------|
| GET | `/v1/rentals` | auth-jwt |
| POST | `/v1/rentals` | auth-jwt |
| GET | `/v1/rentals/{id}` | auth-jwt |
| POST | `/v1/rentals/{id}/activate` | auth-jwt |
| POST | `/v1/rentals/{id}/complete` | auth-jwt |
| POST | `/v1/rentals/{id}/cancel` | auth-jwt |

**Rental statuses**: `RESERVED` → `ACTIVE` → `COMPLETED` or `CANCELLED`

## Customers
| Method | Endpoint | Roles |
|--------|----------|-------|
| GET | `/v1/customers` | auth-jwt |
| POST | `/v1/customers` | auth-jwt |
| GET | `/v1/customers/{id}` | auth-jwt |

## Maintenance
| Method | Endpoint | Roles |
|--------|----------|-------|
| POST | `/v1/maintenance` | auth-jwt |
| GET | `/v1/maintenance/vehicle/{id}` | auth-jwt |
| POST | `/v1/maintenance/{id}/start` | auth-jwt |
| POST | `/v1/maintenance/{id}/complete` | auth-jwt |
| POST | `/v1/maintenance/{id}/cancel` | auth-jwt |

**Job types**: `ROUTINE`, `REPAIR`, `INSPECTION`, `RECALL`, `EMERGENCY`  
**Priorities**: `LOW`, `NORMAL`, `HIGH`, `URGENT`  
**Statuses**: `SCHEDULED` → `IN_PROGRESS` → `COMPLETED` or `CANCELLED`

## Accounting
| Method | Endpoint | Notes |
|--------|----------|-------|
| POST | `/v1/accounting/invoices` | Returns 201 |
| POST | `/v1/accounting/invoices/{id}/pay` | Requires `Idempotency-Key` header |
| GET | `/v1/accounting/payments` | All payments |
| GET | `/v1/accounting/payments/customer/{id}` | Per-customer payments |
| GET | `/v1/accounting/accounts` | Chart of accounts with balances |
| GET | `/v1/accounting/accounts/{code}/balance` | Single account balance |
| GET | `/v1/accounting/payment-methods` | Available payment methods |

**Invoice statuses**: `DRAFT`, `ISSUED`, `PAID`, `OVERDUE`, `CANCELLED`  
Currency is PHP throughout.

## Tracking
| Method | Endpoint | Notes |
|--------|----------|-------|
| GET | `/v1/tracking/routes` | No auth required |
| POST | `/v1/tracking/vehicles/{id}/location` | Rate-limited: 60/min per vehicle |
| GET | `/v1/tracking/vehicles/{id}/state` | Latest vehicle state + position |
| GET | `/v1/tracking/fleet/status` | Fleet-wide summary |
| GET | `/v1/tracking/vehicles/{id}/history` | Paginated history (`limit`, `offset`) |
| WS | `/v1/fleet/live` | DRIVER, FLEET_MANAGER — real-time delta stream |

**Location POST body**: `{ latitude, longitude, speed, heading, accuracy, routeId }`  
**WebSocket**: Redis-backed delta broadcasting; supports Ping/Pong keep-alive.

## Standard API Response Envelope
```json
{
  "success": true,
  "data": { ... },
  "error": { "code": "...", "message": "...", "fieldErrors": [...] },
  "requestId": "req-12345"
}
```

Pagination uses `limit` + `cursor` query params.

---

# Shared Module

Use **Kotlin Multiplatform shared modules** for all business logic shared across platforms:

```
shared/
  commonMain/   ← DTOs, repositories, use cases, ViewModels
  androidMain/  ← Android-specific platform implementations
  iosMain/      ← iOS-specific platform implementations
  webMain/      ← Web-specific platform implementations
```

The shared module must contain:

- **API DTOs** — exact request/response models matching the backend contract
- **API Client** — Ktor Client with JWT interceptor, error mapping, and `ApiResponse<T>` deserialization
- **Repositories** — one per module (AuthRepository, VehicleRepository, RentalRepository, etc.)
- **ViewModels** — `ScreenModel` or `ViewModel` per screen using `StateFlow` / `SharedFlow`
- **Auth state** — JWT storage, expiry detection, refresh/logout triggers
- **Validation** — client-side field validation matching backend rules (e.g. VIN must be 17 chars)
- **Pagination helpers** — cursor-based pagination state management
- **WebSocket client** — Ktor WebSocket client wrapping `/v1/fleet/live` with reconnect logic
- **`AppDependencyDispatcher`** — decodes JWT roles on login and activates the appropriate feature graph
- **`VehicleStateDelta` / `VehicleRouteState`** — shared delta and full-state models used by both web `DeltaDecoder` and mobile tracking screens

---

# Backoffice Web Portal — Screen Specification

## Navigation Structure

```
Routes:
  /login
  /dashboard
  /vehicles
  /vehicles/:id
  /vehicles/new
  /rentals
  /rentals/:id
  /rentals/new
  /customers
  /customers/:id
  /customers/new
  /maintenance
  /maintenance/:id
  /maintenance/new
  /accounting/invoices
  /accounting/invoices/:id
  /accounting/accounts
  /accounting/payments
  /tracking/map
  /users                  (ADMIN only)
  /users/:id              (ADMIN only)
  /profile
```

## Dashboard Screen (`/dashboard`)

Roles: all backoffice roles

Summary cards (pulled from real API calls):
- Total vehicles (from `GET /v1/vehicles`)
- Active rentals (from `GET /v1/rentals`, filter `status=ACTIVE`)
- Fleet live status (from `GET /v1/tracking/fleet/status`): `totalVehicles`, `activeVehicles`
- Scheduled maintenance jobs (from `GET /v1/maintenance/vehicle/{id}` aggregated or list)

Fleet map panel:
- Embeds a mapping component (Leaflet.js via JS interop or Mapbox SDK)
- Shows all vehicle positions from `GET /v1/tracking/fleet/status`
- Updates in real-time via WebSocket `/v1/fleet/live` — applies delta updates to markers

Recent activity feed:
- Last 5 rentals from `GET /v1/rentals?limit=5`
- Last 5 maintenance jobs from maintenance list endpoint

## Vehicles Screen (`/vehicles`)

- Paginated table with columns: License Plate, Make/Model, Year, State badge, Mileage
- State badge colors: `AVAILABLE` = green, `RENTED` = blue, `MAINTENANCE` = orange, `RETIRED` = grey
- Filter bar: by state, make, model
- "Add Vehicle" button → navigates to `/vehicles/new` (ADMIN, FLEET_MANAGER only)

## Vehicle Detail Screen (`/vehicles/:id`)

Tabs:
1. **Info** — all vehicle fields; inline edit form (ADMIN/FLEET_MANAGER)
2. **State** — current state badge + transition button (e.g. "Send to Maintenance", "Mark Available"); disabled if transition is invalid
3. **Odometer** — history list + "Record Reading" form; validates new reading > last reading
4. **Maintenance** — calls `GET /v1/maintenance/vehicle/{id}`; table of jobs with status badges
5. **Tracking History** — calls `GET /v1/tracking/vehicles/{id}/history`; scrollable list of location records with timestamp/speed/heading

## Rentals Screen (`/rentals`)

- Paginated table: Rental #, Customer, Vehicle, Status badge, Start/End Date, Total (PHP)
- Status badge colors: `RESERVED` = yellow, `ACTIVE` = green, `COMPLETED` = grey, `CANCELLED` = red
- Filter bar: by status, date range

## Rental Detail Screen (`/rentals/:id`)

- Rental summary card: all fields
- Status transition buttons (shown only when the transition is valid):
  - `RESERVED` → "Activate Rental" → `POST /v1/rentals/{id}/activate`
  - `ACTIVE` → "Complete Rental" → `POST /v1/rentals/{id}/complete` (requires final odometer input)
  - `RESERVED` → "Cancel Rental" → `POST /v1/rentals/{id}/cancel`
- Invoice section: linked invoice with status; "Pay Invoice" form with payment method selector

## Customers Screen (`/customers`)

- Paginated table: Name, Email, Phone, License #, License Expiry, Active badge
- Inline "Deactivate" toggle
- Create Customer form: validates license expiry is future date

## Maintenance Screen (`/maintenance`)

- Paginated table across all vehicles: Job #, Vehicle, Type badge, Priority badge, Status, Scheduled Date, Cost
- Priority badge colors: `LOW` = grey, `NORMAL` = blue, `HIGH` = orange, `URGENT` = red
- Create form: vehicle selector, job type, priority, scheduled date, description

## Maintenance Detail Screen (`/maintenance/:id`)

- Job info card
- Transition buttons:
  - `SCHEDULED` → "Start Job" → `POST /v1/maintenance/{id}/start`
  - `IN_PROGRESS` → "Complete Job" → `POST /v1/maintenance/{id}/complete` (requires laborCost, partsCost inputs)
  - `SCHEDULED` → "Cancel Job" → `POST /v1/maintenance/{id}/cancel`
- Cost summary: labor + parts + total

## Accounting Screens

### Invoices (`/accounting/invoices`)
- Table: Invoice #, Customer, Rental, Status badge, Amount, Due Date
- Create Invoice form (links to a rental)
- "Pay" action dispatches `POST /v1/accounting/invoices/{id}/pay` with auto-generated `Idempotency-Key` (UUID v4)

### Accounts (`/accounting/accounts`)
- Chart of accounts tree: grouped by `AccountType` (ASSET, LIABILITY, EQUITY, REVENUE, EXPENSE)
- Each row shows: account code, name, type, current balance
- Balances fetched from `GET /v1/accounting/accounts`

### Payments (`/accounting/payments`)
- Table: invoice link, customer, amount, payment method, date

## Fleet Tracking Map (`/tracking/map`)

Roles: ADMIN, FLEET_MANAGER

**Rendering: Custom SVG schematic map** (no third-party geographic map library required). Routes are stored in the backend as PostGIS `LineString` geometries. The frontend converts them to SVG path data and places animated vehicle markers along the paths.

Components:
- **`FleetMap`** — root `<svg>` canvas with `viewBox="0 0 1000 1000"`; dark background
- **`RouteLayer`** — renders each route from `GET /v1/tracking/routes` as an SVG `<path>` using `polylineToPath(lineString)` conversion
- **`VehicleIcon`** — `<polygon>` marker positioned via `getPointAtProgress(polyline, progress)`; rotated by `bearing`; color reflects `VehicleStatus`; position animates with 500ms `tween`

**WebSocket integration:**
- Connect to `WS /v1/fleet/live` for the page lifetime
- Each message is a `VehicleStateDelta` (partial update — only changed fields)
- `DeltaDecoder.merge(current, delta)` applies the delta to the existing `VehicleRouteState` in a `MutableStateFlow<Map<VehicleId, VehicleRouteState>>`
- Reconnect with 5-second fixed delay on disconnect
- `ConnectionState` enum: `DISCONNECTED` → `CONNECTING` → `CONNECTED` / `ERROR`

**Sidebar:**
- Vehicle list sorted by status; click row to highlight marker
- Info panel on selection: plate, speed, heading, route progress %, last update timestamp

**Status bar:** connection indicator (Connected / Reconnecting / Offline)

**Performance targets:**
- ≥ 55 FPS with 50 concurrent vehicles
- < 16ms per render frame
- < 100ms WebSocket message-to-render latency

## Users Screen (`/users`) — ADMIN only

- Table: Name, Email, Roles, Verified, Active
- Role assignment: multi-select chip UI → `POST /v1/users/{id}/roles`
- Delete user: confirmation dialog → `DELETE /v1/users/{id}`

---

# Driver Mobile App — Screen Specification

Role: `DRIVER`

## Navigation

```
Login → Home → Active Rental Detail → Location Tracking (background)
                                    → Rental History
             → Profile
             → Settings  ← tracking hours, geofence, privacy
```

## Login Screen

Standard email + password form → `POST /v1/users/login`.  
Stores JWT in secure storage (Keystore on Android, Keychain on iOS).  
Verifies role is `DRIVER` before proceeding; shows error if wrong role.

## Home Screen

- Current status indicator: vehicle assignment if in active rental
- "Active Rental" card if a rental is ACTIVE (fetched from `GET /v1/rentals` filtered by driver context)
- Rental history list (recent completed rentals)

## Automatic GPS Tracking

When a rental is ACTIVE, start a **Foreground Service** (Android) with a persistent notification. Tracking continues even when the app is backgrounded.

**Sensor collection (Android `androidMain`):**

| Sensor | Purpose | Rate |
|--------|---------|------|
| Fused Location Provider | Vehicle position, speed, heading | 10s moving / 60s idle (adaptive) |
| `TYPE_ACCELEROMETER` | Harsh braking/acceleration detection | 200ms (`SENSOR_DELAY_NORMAL`) |
| `TYPE_GYROSCOPE` | Sharp turn / swerving detection | 200ms (`SENSOR_DELAY_NORMAL`) |

**Driving event detection (local only — not transmitted to backend):**
- Harsh braking: `accelX < -4.0 m/s²`
- Harsh acceleration: `accelX > 4.0 m/s²`
- Sharp turn: `gyroZ > 1.5 rad/s`

Location accuracy gate: skip pings where `accuracy > 50m`.

**Transmission to backend:**
- Posts `POST /v1/tracking/vehicles/{vehicleId}/location` with `{ latitude, longitude, speed, heading, accuracy, routeId }`
- One POST per second max (60/min rate limit)
- Sends `Idempotency-Key` (UUID v4 per ping) to prevent duplicate processing

**Offline-first buffer (Android `androidMain`):**
- In-memory buffer of up to 10 pings; flush every 60s OR when buffer full
- On network failure: persist batch to **Room DB** (SQLCipher encrypted)
- **WorkManager** retries pending pings with exponential backoff: 1 min → 5 min → 15 min → 1 hr
- Storage limits: max 1,000 pings, 7-day retention; enforce on startup
- On network restore (`ConnectivityManager` callback): drain Room DB immediately

**Tracking status indicator in UI:** `ACTIVE` / `PAUSED` / `OFFLINE_BUFFERING` / `ERROR`

## Work Hours & Geofencing (Privacy Protection)

Prevents local database pollution and off-duty tracking when a driver forgets to end their shift.

**WorkHoursManager** (configurable, stored in `SharedPreferences`):
- Default work hours: 6 AM – 10 PM, Monday – Saturday
- Before each GPS ping: check `shouldTrack()` — if outside hours, skip collection and show a one-per-day notification
- Driver can override in Settings screen

**GeofenceManager** (Android `GeofencingClient`):
- Registers a service-area geofence with default radius 50 km
- `GEOFENCE_TRANSITION_EXIT` → stop Foreground Service, show "Left service area — tracking paused" notification
- `GEOFENCE_TRANSITION_ENTER` → resume Foreground Service
- Can be disabled for inter-city/roaming drivers

**Driver Settings Screen:**
```
Tracking Settings
  [Toggle] Auto-pause outside work hours
    Start Hour slider (0–23)
    End Hour slider (0–23)
  [Toggle] Auto-pause outside service area
  [Info card] Privacy — these settings prevent off-duty data collection
```

Privacy guarantees:
- Off-duty data is **never collected**, not merely discarded later
- Driver can force-stop tracking at any time via manual override
- Data in Room DB is encrypted with SQLCipher

## Profile Screen

- Displays user info from `GET /v1/users/{id}`
- Edit name and phone via `PATCH /v1/users/{id}`
- Logout (clears stored JWT)

---

# Customer Mobile App — Screen Specification

Role: `CUSTOMER`

## Navigation

```
Login / Register → Home → Rental Detail → Live Tracking Map
                        → Rental History
                        → Profile
```

## Login / Register Screens

- Login: `POST /v1/users/login` — verifies role is `CUSTOMER`
- Register: `POST /v1/users/register` — then prompts for email verification
- Email verification prompt: instructs user to check inbox; re-send verification link option

## Home Screen

- Active rental card (if any ACTIVE rental exists)
- "Track My Vehicle" button → opens Live Tracking Map (only shown if rental is ACTIVE)
- Quick access to rental history

## Rental History Screen

- List from `GET /v1/rentals`
- Each item: rental number, vehicle make/model, dates, status badge, total amount
- Tap → Rental Detail Screen

## Rental Detail Screen

- Full rental summary
- Invoice section: linked invoice with status and amount
- Payment receipt if paid

## Live Vehicle Tracking Screen

Only accessible when rental is ACTIVE.

- Map view showing assigned vehicle's position
- Polls `GET /v1/tracking/vehicles/{vehicleId}/state` every 5 seconds (no WebSocket for customer role)
- Displays: vehicle plate, speed, heading arrow, route progress
- Auto-centers map on vehicle position on each update

## Profile Screen

- View and edit: first name, last name, phone via `PATCH /v1/users/{id}`
- Linked customer record: driver license info (view only) from `GET /v1/customers/{id}`
- Logout

---

# Shared Architecture

## State Management

Use `StateFlow<UiState>` per ViewModel/ScreenModel:

```kotlin
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String, val fieldErrors: Map<String, String> = emptyMap()) : UiState<Nothing>
}
```

## API Client

Ktor HttpClient shared in `commonMain`:

- Base URL injected via `expect/actual` per platform
- `Authorization: Bearer <token>` added via `HttpClientPlugin`
- Parses `ApiResponse<T>` envelope; maps `success=false` to typed errors
- On `401` response: clears JWT, triggers navigation to login screen
- On `429` response (rate limit): shows non-blocking snackbar/toast

## Auth Storage

```kotlin
expect class SecureStorage {
    fun saveToken(token: String)
    fun getToken(): String?
    fun clearToken()
}
```

`actual` implementations:
- Android: `EncryptedSharedPreferences`
- iOS: `Keychain`
- Web: `sessionStorage` (not `localStorage` — no persistent JWT on web)

## WebSocket Client (shared)

```kotlin
class FleetLiveClient(private val httpClient: HttpClient) {
    val connectionState: StateFlow<ConnectionState>  // DISCONNECTED | CONNECTING | CONNECTED | ERROR
    suspend fun connect(token: String, onDelta: (VehicleStateDelta) -> Unit)
    fun disconnect()
    // Auto-reconnect: 5s fixed delay on web; exponential backoff (1s→2s→4s→max 30s) on mobile
    // Sends periodic Ping frames; disconnects if Pong not received within 30s
}
```

## Delta Decoding (web)

WebSocket messages are **partial state updates** — only changed fields are transmitted. The web fleet map must merge each delta into the existing vehicle state:

```kotlin
object DeltaDecoder {
    fun merge(current: VehicleRouteState?, delta: VehicleStateDelta): VehicleRouteState
    // Fields present in delta overwrite current; absent fields retain previous values
}
```

The `FleetState` class holds a `MutableStateFlow<Map<VehicleId, VehicleRouteState>>` and applies each incoming delta via `DeltaDecoder.merge()`.

## Pagination

```kotlin
data class PaginatedState<T>(
    val items: List<T> = emptyList(),
    val nextCursor: String? = null,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true
)
```

Each list ViewModel exposes `loadMore()` trigger for infinite scroll / "Load More" button.

## Field Validation (commonMain)

Match backend rules exactly:
- VIN: exactly 17 characters
- License plate: non-blank
- Driver license expiry: must be a future date
- Odometer: new reading must be > last reading (check from vehicle detail)
- Email: RFC 5322 pattern
- Password: minimum 8 characters

---

# Project Structure

```
fleet-platform/
├── backend/                     ← Existing Ktor backend (do not modify)
├── shared/
│   ├── src/commonMain/kotlin/
│   │   ├── api/
│   │   │   ├── FleetApiClient.kt
│   │   │   ├── dto/             ← Request/Response DTOs per module
│   │   │   └── ApiResponse.kt
│   │   ├── repository/
│   │   │   ├── AuthRepository.kt
│   │   │   ├── VehicleRepository.kt
│   │   │   ├── RentalRepository.kt
│   │   │   ├── CustomerRepository.kt
│   │   │   ├── MaintenanceRepository.kt
│   │   │   ├── AccountingRepository.kt
│   │   │   └── TrackingRepository.kt
│   │   ├── viewmodel/           ← One ViewModel per screen
│   │   ├── validation/
│   │   ├── auth/
│   │   │   ├── SecureStorage.kt (expect)
│   │   │   └── AppDependencyDispatcher.kt
│   │   └── tracking/
│   │       ├── FleetLiveClient.kt
│   │       ├── VehicleStateDelta.kt
│   │       └── VehicleRouteState.kt
│   ├── src/androidMain/kotlin/
│   │   ├── auth/SecureStorage.kt        ← EncryptedSharedPreferences
│   │   └── tracking/
│   │       ├── SensorTrackingService.kt ← Foreground Service
│   │       ├── SensorEngine.kt          ← GPS + Accel + Gyro fusion
│   │       ├── WorkHoursManager.kt
│   │       ├── GeofenceManager.kt
│   │       ├── local/
│   │       │   ├── CoordinateDatabase.kt (Room + SQLCipher)
│   │       │   └── SensorPingDao.kt
│   │       └── workers/
│   │           └── CoordinateRetryWorker.kt (WorkManager)
│   ├── src/iosMain/kotlin/
│   │   ├── auth/SecureStorage.kt        ← Keychain
│   │   └── tracking/LocationManager.kt ← CLLocationManager
│   └── src/webMain/kotlin/
│       └── auth/SecureStorage.kt        ← sessionStorage
├── web-backoffice/              ← Kotlin/JS (IR) + Compose for Web
│   └── src/jsMain/kotlin/
│       ├── screens/
│       │   ├── dashboard/
│       │   ├── vehicles/
│       │   ├── rentals/
│       │   ├── customers/
│       │   ├── maintenance/
│       │   ├── accounting/
│       │   ├── tracking/
│       │   └── users/
│       ├── components/
│       │   ├── fleet/
│       │   │   ├── FleetMap.kt          ← SVG canvas root
│       │   │   ├── RouteLayer.kt        ← SVG path per route
│       │   │   ├── VehicleIcon.kt       ← Animated SVG polygon
│       │   │   └── DeltaDecoder.kt
│       │   ├── StatusBadge.kt
│       │   ├── PaginatedTable.kt
│       │   └── WebSocketStatus.kt
│       ├── state/
│       │   ├── FleetState.kt            ← MutableStateFlow<Map<VehicleId, VehicleRouteState>>
│       │   └── WebSocketClient.kt
│       └── utils/
│           └── SvgUtils.kt              ← polylineToPath, getPointAtProgress
├── mobileApp/                   ← Unified Android + iOS entry point
│   ├── src/androidMain/kotlin/
│   │   └── MainActivity.kt
│   ├── src/iosMain/kotlin/
│   │   └── MainViewController.kt
│   └── src/commonMain/kotlin/
│       ├── dispatch/
│       │   └── RoleDispatchViewModel.kt ← Reads roles, routes to driver/customer
│       ├── driver/              ← Driver screens + Settings
│       └── customer/            ← Customer screens
```

---

# Development Roadmap

## Phase 1 — Shared Foundation
1. Set up KMP project with `shared`, `web-backoffice`, `mobileApp` modules
2. Implement `FleetApiClient` with `ApiResponse<T>` parsing and JWT interceptor
3. Implement all repository classes
4. Add `SecureStorage` `expect/actual` for all platforms
5. Implement `UiState<T>` pattern and base ViewModel helpers
6. Wire field validators

## Phase 2 — Backoffice Web Portal
1. Auth screens (Login)
2. Dashboard screen with fleet summary cards + embedded SVG map
3. Vehicles CRUD (list, detail, create, state transitions, odometer)
4. Rentals (list, detail, activate/complete/cancel)
5. Customers (list, detail, create)
6. Maintenance (list, detail, schedule/start/complete/cancel)
7. Accounting screens (invoices, accounts, payments)
8. `SvgUtils.polylineToPath()` + `getPointAtProgress()` route rendering utilities
9. `FleetState` + `WebSocketClient` + `DeltaDecoder` wiring
10. Fleet Tracking Map (`FleetMap`, `RouteLayer`, `VehicleIcon` with 500ms animation) + WebSocket live updates
11. Users management screen (ADMIN only)

## Phase 3 — Driver Mobile App
1. Login with role guard (`DRIVER` check) — role dispatcher wiring
2. Home screen: active rental card + tracking status indicator
3. `SensorTrackingService` Foreground Service with persistent notification
4. `SensorEngine`: GPS adaptive sampling (10s/60s) + Accelerometer + Gyroscope
5. Location POST loop with Idempotency-Key per ping
6. Offline buffer: Room DB persistence + `CoordinateRetryWorker` with exponential backoff
7. `WorkHoursManager` + `GeofenceManager` for privacy/battery protection
8. Driver Settings screen (work hours config, geofence toggle)
9. Driving event detection (local crash/swerve flags — display only, not posted to backend)

## Phase 4 — Customer Mobile App
1. Register + Login + email verification flow
2. Home screen (active rental card)
3. Rental history + detail
4. Live vehicle tracking (polling `GET /v1/tracking/vehicles/{id}/state`)
5. Profile management

---

# Security Considerations

- JWT stored only in secure platform storage — never in cookies or plaintext preferences
- Web portal uses `sessionStorage` (cleared on tab close); no persistent JWT on web
- All API calls go over HTTPS; disallow HTTP in production
- `Idempotency-Key` header generated as UUID v4 (client-side) before each payment request
- Role guard applied at route level: unauthorized roles redirected to `/dashboard` with an alert
- Driver GPS pings include `Idempotency-Key` per ping to prevent duplicate location entries
- On 401 from any API call, immediately clear token and navigate to login