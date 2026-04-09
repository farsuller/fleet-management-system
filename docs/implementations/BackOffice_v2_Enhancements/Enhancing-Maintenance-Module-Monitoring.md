# Enhanced Maintenance Module: Implementation-Aligned Plan

This plan has been reviewed against the current codebase in both repositories:

- `fleet-management` backend: Kotlin + Ktor modular monolith
- `Fleet Management BackOffice` client: Kotlin Multiplatform + Compose web back office

The goal is to enhance maintenance monitoring without drifting from what is already implemented, with emphasis on daily and weekly operational reporting rather than real-time shop-floor streaming.

---

## 1. Current Baseline Confirmed in the Codebase

The maintenance module is not a greenfield feature. It already exists in both the backend and the web back office.

### Backend capabilities already implemented

- `maintenance_jobs` persistence with `SCHEDULED`, `IN_PROGRESS`, `COMPLETED`, and `CANCELLED`
- `maintenance_parts` persistence for per-job part costing
- `vehicle_incidents` linked to maintenance jobs
- `maintenance_schedules` table for recurring maintenance data
- `assigned_to_user_id` and `completed_by_user_id` fields on maintenance jobs
- maintenance REST endpoints under `/v1/maintenance/jobs`
- incident endpoints under `/v1/incidents` and `/v1/vehicles/{plate|id}/incidents`
- `MAINTENANCE` as a valid vehicle state in the vehicle domain
- rental protection that already blocks rental activation when a vehicle is not `AVAILABLE`
- live fleet tracking infrastructure via WebSocket and Redis Pub/Sub, but only for tracking deltas today

### Back-office capabilities already implemented

- maintenance list screen with filtering
- maintenance detail screen with incidents and usage history
- create, start, complete, and cancel maintenance workflows
- repository, use cases, and DTOs aligned to `/v1/maintenance/jobs`
- vehicle tracking WebSocket client for live map updates

### Important constraint

The current maintenance lifecycle is intentionally simple. It does not yet support technician workflow depth, predictive auto-scheduling, or accounting ledger postings.

---

## 2. Drift Identified During Review

The original version of this plan described several capabilities as if they already existed. They do not. Those items remain valid enhancement targets, but they should be framed as phased work.

### Items that are enhancement targets, not current behavior

- granular maintenance sub-statuses such as `AWAITING_PARTS` or `QUALITY_CHECK`
- scheduled maintenance reporting and alerting rather than real-time maintenance event streaming
- automatic vehicle state coordination when a job starts or completes
- automatic ledger postings on maintenance completion
- predictive job creation from odometer and service schedules
- technician assignment workflows exposed in API and UI
- nearest-service-center routing for incidents

### Documentation drift that should be treated as a cleanup item

- some backend docs and sample payloads still reference `/v1/maintenance` while the implemented route used by the back office is `/v1/maintenance/jobs`
- some older examples still use historical labels such as `ROUTINE` or generic `totalCost`-only payloads, while the current shared contract uses `PREVENTIVE`, `CORRECTIVE`, `INSPECTION`, `EMERGENCY`, plus `laborCostPhp` and `partsCostPhp`

---

## 3. Enhancement Principles

To stay aligned with the current architecture, maintenance enhancements should follow these rules:

1. Reuse the existing maintenance job lifecycle instead of replacing it.
2. Extend the current `/v1/maintenance/jobs` contract rather than introducing a parallel maintenance API.
3. Reuse the existing Redis and WebSocket infrastructure, but avoid overloading the fleet location delta payload with maintenance-specific semantics.
4. Keep vehicle availability enforcement rooted in the vehicle and rental domains, not only in the front end.
5. Treat reporting, accounting, and predictive scheduling as separate phases so they can be rolled out and verified independently.

---

## 4. Recommended Enhancement Roadmap

## Phase 1: Contract Alignment and Operational Hardening

This phase closes the gap between what the database can represent and what the API and UI currently expose.

### Phase 1 Backend

- standardize all maintenance documentation and OpenAPI examples on `/v1/maintenance/jobs`
- expose fields already present in persistence where useful to the UI:
  - `jobNumber`
  - `assignedToUserId`
  - `completedByUserId`
  - `odometerKm`
  - `notes`
  - `startedAt`
  - `completedAt`
- add explicit use cases or endpoints for assignment if assignment is intended to be an operational workflow rather than a hidden data field
- coordinate vehicle state with maintenance transitions:
  - starting a maintenance job should move the vehicle to `MAINTENANCE`
  - completing a maintenance job should require an explicit release path back to `AVAILABLE`
  - cancellation should avoid leaving the vehicle stranded in `MAINTENANCE`

### Phase 1 Back office

- surface assignee and timing fields on the list and detail screens
- show operational timestamps so managers can distinguish scheduled work from active work and completed work
- add explicit release workflow language so the UI does not imply that job completion alone already restores vehicle availability when the backend does not yet guarantee that invariant

### Expected outcome

The maintenance module becomes operationally trustworthy before new intelligence is added.

---

## Phase 2: Daily and Weekly Maintenance Reporting

This phase turns maintenance activity into a reliable management review loop instead of a real-time monitoring stream.

### Phase 2 Backend

- add reporting queries or materialized read models for:
  - jobs created in the last day or week
  - jobs completed in the last day or week
  - jobs overdue or aging in `SCHEDULED` or `IN_PROGRESS`
  - incidents reported and incidents converted into jobs
  - maintenance cost totals by vehicle and by period
- support scheduled report generation at daily and weekly cadence
- expose report-oriented endpoints that the back office can consume without replaying raw operational records in the client

### Phase 2 Back office

- add a maintenance reporting view focused on:
  - daily completion summary
  - weekly backlog summary
  - overdue jobs
  - top vehicles by maintenance cost
  - incident volume by severity
- present trend cards and tables for managers instead of live maintenance event feeds
- align maintenance summaries with the existing fleet-status dashboard where useful, but keep reporting separate from the live map experience

### Recommended scope guard

Do not build maintenance streaming unless there is a later operational need. Daily and weekly reporting is enough for this phase.

---

## Phase 3: Technician Assignment and Workflow Depth

The data model already contains assignment fields. This phase makes that useful to operations.

### Phase 3 Backend

- link assignment flows to actual staff records and roles
- add assignment and reassignment use cases for maintenance jobs
- validate that assigned users belong to valid maintenance-capable roles or departments
- optionally add richer operational states only if the business truly needs them:
  - `AWAITING_PARTS`
  - `QUALITY_CHECK`
  - `READY_FOR_RELEASE`

### Phase 3 Back office

- add technician selection and reassignment controls
- show assigned technician, elapsed job time, and aging buckets
- distinguish assignment state from work state so the UI does not collapse all progress into `IN_PROGRESS`

### Recommendation

Only introduce sub-statuses if they drive a concrete screen, report, SLA, or automation rule. If not, keep the current four-state lifecycle and add assignment metadata first.

---

## Phase 4: Predictive and Schedule-Driven Maintenance

This phase uses tables and invariants that already exist but are not yet orchestrated.

### Existing backend assets to build on

- `odometer_readings`
- vehicle `current_odometer_km`
- `maintenance_schedules`
- service mileage columns already added to vehicle schema

### Phase 4 Backend enhancements

- create a scheduler or evaluation use case that detects upcoming service thresholds
- emit a maintenance-due event or create an alert record when a vehicle approaches its next service mileage or date threshold
- optionally auto-create a `SCHEDULED` maintenance job when the rule is explicit and approved by operations
- ensure duplicate preventive jobs are not created for the same schedule window

### Phase 4 Back office enhancements

- show service-due alerts in the dashboard and vehicle detail screens
- surface preventive jobs separately from corrective or emergency work
- allow managers to convert alerts into scheduled jobs when full automation is not desired

### Important business rule

Predictive maintenance should augment the current workflow, not bypass it. A due alert is not the same thing as a started job.

---

## Phase 5: Financial Integrity for Maintenance Costs

The current backend records labor and parts costs, but it does not yet post them into the accounting ledger.

### Phase 5 Backend

- extend the accounting service with a maintenance posting workflow
- create ledger entries when a maintenance job is completed and financially approved
- debit a maintenance expense account and credit the appropriate payable, inventory, or cash account depending on the final procurement flow
- use `maintenance_parts` as the source of truth for itemized cost reporting where part-line detail is available

### Phase 5 Back office

- show itemized maintenance cost composition:
  - labor
  - parts
  - total
- support finance and operations views separately if accounting approval becomes a distinct step
- enable per-vehicle cost trend reporting and maintenance-to-revenue comparisons

### Scope guard

Do not post financial entries at job start. Ledger impact should be tied to completion or financial approval, depending on policy.

---

## Phase 6: Incident Response and Spatial Intelligence

The tracking and spatial stack is strong enough to support smarter incident handling, but this should come after the maintenance core is hardened.

### Phase 6 Backend

- enrich `vehicle_incidents` handling with nearest-service-center lookup
- use PostGIS-based routing or proximity logic for reactive maintenance dispatch support
- connect incident severity to maintenance prioritization rules where appropriate

### Phase 6 Back office

- show incidents on the fleet map and in vehicle maintenance detail
- allow operators to create a maintenance job directly from an incident
- highlight vehicle downtime exposure by combining rental value and maintenance duration

---

## 5. Updated Implementation Flow

| Stage | Back office / Driver surfaces | Backend responsibility |
| :--- | :--- | :--- |
| **Detect** | Manager sees upcoming service alert or reported incident. | Evaluate `maintenance_schedules`, `odometer_readings`, and incident submissions. |
| **Schedule** | Manager creates or confirms a job in the maintenance UI. | Persist job in `maintenance_jobs` with correct type, priority, and audit fields. |
| **Assign** | Manager assigns a technician or maintenance staff user. | Validate assignee and store `assigned_to_user_id`. |
| **Start Work** | Technician or manager starts the job. | Transition job to `IN_PROGRESS` and coordinate vehicle state to `MAINTENANCE`. |
| **Execute** | Technician logs notes, parts, and cost details. | Persist progress details, parts usage, and reporting data needed for daily and weekly review. |
| **Complete** | Manager or technician marks work completed. | Transition job to `COMPLETED`, capture costs, and trigger financial posting workflow if enabled. |
| **Release** | Manager explicitly releases the vehicle back to service. | Validate release prerequisites and return vehicle state to `AVAILABLE`. |

---

## 6. Back-Office Metrics Worth Monitoring

These metrics are grounded in the current schema and proposed enhancement path.

1. **Mean Time to Repair (MTTR)**: Average time from `startedAt` to `completedAt`.
2. **Scheduled Aging**: Number of jobs stuck in `SCHEDULED` beyond their target start window.
3. **Fleet in Maintenance**: Count and percentage of vehicles currently unavailable due to maintenance.
4. **Preventive Compliance Rate**: Share of vehicles serviced before or near their due mileage/date threshold.
5. **Incident-to-Job Conversion Rate**: Percentage of reported incidents that create maintenance jobs.
6. **Maintenance Cost per Vehicle**: Labor plus parts over a selected period.
7. **Maintenance-to-Revenue Ratio**: Repair cost compared with rental revenue generated by the same vehicle.
8. **Daily Completion Count**: Number of jobs completed per day.
9. **Weekly Backlog Trend**: Change in open maintenance workload week over week.

---

## 7. Recommended Next Delivery Slice

The most pragmatic next slice is:

1. align docs and API contract naming
2. automate vehicle state coordination for maintenance start and release
3. expose assignment and timing fields in the API and back office
4. add daily and weekly maintenance reporting views and backend report queries

That sequence improves operational correctness first, then unlocks reporting value without taking on predictive scheduling, streaming, and accounting complexity too early.
