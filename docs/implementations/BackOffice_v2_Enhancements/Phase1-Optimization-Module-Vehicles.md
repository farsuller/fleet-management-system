# Phase 1 — Module: Vehicles RAM Optimization

> **Scope**: `modules/vehicles/infrastructure/persistence/VehicleRepositoryImpl.kt`  
> **Goal**: Eliminate intermediate list allocations and redundant full-count queries  
> **Risk**: None — same query results, same API output  
> **Status**: Complete ✅ (2026-05-02)

---

## 1. findByPlateNumber() — Eliminate Intermediate List

**Problem**: `.map { it.toVehicle() }.singleOrNull()` creates an intermediate `List<Vehicle>` from all matching rows, then extracts one. Since `plateNumber` is unique, this always produces 0 or 1 rows, but the `map` still allocates a list.

**Fix**: Use `.singleOrNull()?.toVehicle()` — maps only the single result directly.

```diff
     override suspend fun findByPlateNumber(plateNumber: String): Vehicle? =
         dbQuery {
             VehiclesTable
                 .selectAll()
                 .where { VehiclesTable.plateNumber eq plateNumber }
-                .map { it.toVehicle() }
                 .singleOrNull()
+                ?.toVehicle()
         }
```

**Skill Ref**: §2 — Sequences / Avoid intermediate collections

---

## 2. save() — Optimize Exists Check

**Problem**: Line 105 uses `.selectAll().where { ... }.count() > 0` to check if a vehicle exists. This forces the database to count **all** matching rows (always 0 or 1 for a primary key, but the intent is wasteful — it requests `SELECT COUNT(*)` instead of a simple existence probe).

**Fix**: Use `.limit(1).singleOrNull() != null` or Exposed's `.empty()` check. This tells the DB to stop scanning after finding one match.

```diff
-            val exists = VehiclesTable.selectAll().where { VehiclesTable.id eq vehicleUuid }.count() > 0
+            val exists = VehiclesTable
+                .select(VehiclesTable.id)
+                .where { VehiclesTable.id eq vehicleUuid }
+                .limit(1)
+                .singleOrNull() != null
```

**Skill Ref**: §8 — Avoiding Iterator Overhead

---

## Checklist

- [x] `findByPlateNumber()` — `.map{}.singleOrNull()` → `.singleOrNull()?.toVehicle()`
- [x] `save()` — `.count() > 0` → `.select(id).limit(1).singleOrNull() != null`

---

## Before & After Comparison

### Pattern A: `.map{}.singleOrNull()` → `.singleOrNull()?.toX()` — 1 location

**BEFORE** — `findByPlateNumber()`:
```kotlin
VehiclesTable
    .selectAll()
    .where { VehiclesTable.plateNumber eq plateNumber }
    .map { it.toVehicle() }   // ← creates NEW ArrayList, maps row into it
    .singleOrNull()            // ← reads index 0, discards the list
```

**AFTER**:
```kotlin
VehiclesTable
    .selectAll()
    .where { VehiclesTable.plateNumber eq plateNumber }
    .singleOrNull()            // ← returns ResultRow? directly (no list)
    ?.toVehicle()              // ← maps inline only if non-null
```

**Per-call savings**: ~80 bytes (`ArrayList` header + backing `Object[10]` + internal fields) → 0 bytes

---

### Pattern B: `.count() > 0` → `.select(id).limit(1)` — 1 location

**BEFORE** — `save()`:
```sql
SELECT COUNT(*) FROM vehicles WHERE id = '...'
```

**AFTER**:
```sql
SELECT vehicles.id FROM vehicles WHERE vehicles.id = '...' LIMIT 1
```

| Aspect | Before | After |
|---|---|---|
| Columns fetched | All (`*`) | 1 (`id` only) |
| DB scan | Counts all matches | Stops at first match |

---

### Aggregate Impact

| Category | Before | After |
|---|---|---|
| ArrayList allocations per lookup | 1 per call | 0 |
| SQL exists-check | `SELECT COUNT(*)` | `SELECT id LIMIT 1` |
| API response output | Identical | Identical ✅ |
