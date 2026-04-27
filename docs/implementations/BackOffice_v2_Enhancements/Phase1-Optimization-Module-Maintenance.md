# Phase 1 — Module: Maintenance RAM Optimization

> **Scope**: `modules/maintenance/infrastructure/persistence/MaintenanceRepositoryImpl.kt`  
> **Goal**: Eliminate exception-as-control-flow and redundant count queries  
> **Risk**: None — same query results, same API output  
> **Status**: Complete ✅ (2026-04-27)

---

## 1. toMaintenanceJob() — Replace try-catch with getOrNull

**Problem**: Lines 43-60 use `try { this[VehiclesTable.plateNumber] } catch (e: Exception) { null }` three times for optional joined columns. Exceptions are **extremely expensive** on the JVM — each one allocates a full stack trace object (~1-4 KB depending on call depth).

**Fix**: Replace with `this.getOrNull(VehiclesTable.plateNumber)` — the same pattern already used in `RentalRepositoryImpl` and `MaintenanceRepositoryImpl.toVehicleIncident()`.

```diff
     private fun ResultRow.toMaintenanceJob() =
         MaintenanceJob(
             id = MaintenanceJobId(this[MaintenanceJobsTable.id].value.toString()),
             jobNumber = this[MaintenanceJobsTable.jobNumber],
             vehicleId = VehicleId(this[MaintenanceJobsTable.vehicleId].value.toString()),
-            vehiclePlate =
-                try {
-                    this[VehiclesTable.plateNumber]
-                } catch (e: Exception) {
-                    null
-                },
-            vehicleMake =
-                try {
-                    this[VehiclesTable.make]
-                } catch (e: Exception) {
-                    null
-                },
-            vehicleModel =
-                try {
-                    this[VehiclesTable.model]
-                } catch (e: Exception) {
-                    null
-                },
+            vehiclePlate = this.getOrNull(VehiclesTable.plateNumber),
+            vehicleMake = this.getOrNull(VehiclesTable.make),
+            vehicleModel = this.getOrNull(VehiclesTable.model),
             status = MaintenanceStatus.valueOf(this[MaintenanceJobsTable.status]),
```

**Skill Ref**: §2 — JIT-Friendly Code (Senior), avoid reflection/exception overhead

---

## 2. saveJob() + saveIncident() — Optimize Exists Check

**Problem**: Both `saveJob()` (line 129-133) and `saveIncident()` (line 285-289) use `.selectAll().where { ... }.count() > 0`.

**Fix**: Replace with `.select(id).where { ... }.limit(1).singleOrNull() != null`.

### saveJob()
```diff
-            val exists =
-                MaintenanceJobsTable
-                    .selectAll()
-                    .where { MaintenanceJobsTable.id eq UUID.fromString(job.id.value) }
-                    .count() > 0
+            val exists =
+                MaintenanceJobsTable
+                    .select(MaintenanceJobsTable.id)
+                    .where { MaintenanceJobsTable.id eq UUID.fromString(job.id.value) }
+                    .limit(1)
+                    .singleOrNull() != null
```

### saveIncident()
```diff
-            val exists =
-                VehicleIncidentsTable
-                    .selectAll()
-                    .where { VehicleIncidentsTable.id eq incident.id.value }
-                    .count() > 0
+            val exists =
+                VehicleIncidentsTable
+                    .select(VehicleIncidentsTable.id)
+                    .where { VehicleIncidentsTable.id eq incident.id.value }
+                    .limit(1)
+                    .singleOrNull() != null
```

**Skill Ref**: §8 — Avoiding Iterator Overhead

---

## Checklist

- [x] `toMaintenanceJob()` — 3x `try/catch` → `getOrNull()` for vehicle plate/make/model
- [x] `saveJob()` — `.count() > 0` → `.select(id).limit(1).singleOrNull() != null`
- [x] `saveIncident()` — `.count() > 0` → `.select(id).limit(1).singleOrNull() != null`

---

## Before & After Comparison

### Pattern A: `try/catch` → `getOrNull()` — 3 locations (HIGHEST IMPACT)

**BEFORE** — `toMaintenanceJob()`:
```kotlin
vehiclePlate =
    try {
        this[VehiclesTable.plateNumber]  // ← if column not in ResultRow...
    } catch (e: Exception) {             // ← JVM allocates Exception object (~16 bytes)
        null                             //    + fills StackTraceElement[] (~100 entries × 32 bytes = ~3.2 KB)
    },                                   //    Total: ~3.2 KB per exception, 3 fields = ~9.6 KB per row
```

**AFTER**:
```kotlin
vehiclePlate = this.getOrNull(VehiclesTable.plateNumber),  // ← returns null directly, zero allocation
```

**Per-call savings** (when column is absent — e.g., queries without LEFT JOIN):

| Allocation | Before (per field) | After | Saved |
|---|---|---|---|
| `Exception` object | ~16 bytes | 0 | 16 bytes |
| `StackTraceElement[]` (~100 entries) | ~3,200 bytes | 0 | ~3,200 bytes |
| `String` for message | ~40 bytes | 0 | ~40 bytes |
| **Per field** | **~3.3 KB** | **0** | **~3.3 KB** |
| **× 3 fields per row** | **~9.9 KB** | **0** | **~9.9 KB** |

> At 50 maintenance jobs listed per request, each missing the vehicle join:
> **Before**: 50 rows × 3 exceptions × ~3.3 KB = **~495 KB of garbage per request**
> **After**: 0 bytes

---

### Pattern B: `.count() > 0` → `.select(id).limit(1)` — 2 locations

**BEFORE** — `saveJob()` / `saveIncident()`:
```sql
SELECT COUNT(*) FROM maintenance_jobs WHERE id = '...'
```

**AFTER**:
```sql
SELECT maintenance_jobs.id FROM maintenance_jobs WHERE maintenance_jobs.id = '...' LIMIT 1
```

| Aspect | Before | After |
|---|---|---|
| Columns fetched | All (`*`) | 1 (`id` only) |
| DB scan | Counts all matches | Stops at first match |

---

### Aggregate Impact

| Category | Before | After |
|---|---|---|
| Exception objects per list query | 3 per row (plate/make/model) | 0 |
| StackTrace garbage per list query | ~9.9 KB per row | 0 |
| Garbage at 50 rows | ~495 KB | ~0 |
| SQL exists-check | `SELECT COUNT(*)` | `SELECT id LIMIT 1` |
| API response output | Identical | Identical ✅ |

> **Note**: This is the **single highest-impact optimization** in Phase 1 on a per-call basis. Exception stack traces are the most expensive temporary objects the JVM creates.
