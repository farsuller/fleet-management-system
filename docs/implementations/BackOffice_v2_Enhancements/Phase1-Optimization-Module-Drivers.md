# Phase 1 — Module: Drivers RAM Optimization

> **Scope**: `modules/drivers/infrastructure/persistence/DriverRepositoryImpl.kt`  
> **Goal**: Eliminate intermediate list allocations and redundant count queries  
> **Risk**: None — same query results, same API output

---

## 1. Single-Entity Lookups — Eliminate Intermediate Lists

**File**: `modules/drivers/infrastructure/persistence/DriverRepositoryImpl.kt`

**Problem**: Six methods use `.map { it.toDriver/toAssignment/toDriverShift() }.singleOrNull()` which allocates an intermediate list for each lookup.

**Fix**: Replace with `.singleOrNull()?.toX()`.

### findById() — Line 64-71
```diff
     override suspend fun findById(id: DriverId): Driver? =
         dbQuery {
             DriversTable
                 .selectAll()
                 .where { DriversTable.id eq UUID.fromString(id.value) }
-                .map { it.toDriver() }
                 .singleOrNull()
+                ?.toDriver()
         }
```

### findByEmail() — Line 73-80
Same pattern — `.singleOrNull()?.toDriver()`

### findByLicenseNumber() — Line 82-89
Same pattern — `.singleOrNull()?.toDriver()`

### findActiveAssignmentByVehicle() — Line 201-210
```diff
-                .map { it.toAssignment() }
                 .singleOrNull()
+                ?.toAssignment()
```

### findActiveAssignmentByDriver() — Line 212-221
Same pattern — `.singleOrNull()?.toAssignment()`

### findActiveShift() — Line 243-250
```diff
-                .map { it.toDriverShift() }
                 .singleOrNull()
+                ?.toDriverShift()
```

**Skill Ref**: §2 — Avoid intermediate collections

---

## 2. save() — Optimize Exists Check

**Problem**: Line 103 uses `.selectAll().where { ... }.count() > 0`.

**Fix**: Select only the ID column with `limit(1)`.

```diff
-            val exists = DriversTable.selectAll().where { DriversTable.id eq uuid }.count() > 0
+            val exists = DriversTable
+                .select(DriversTable.id)
+                .where { DriversTable.id eq uuid }
+                .limit(1)
+                .singleOrNull() != null
```

**Skill Ref**: §8 — Avoiding Iterator Overhead

---

## Checklist

- [ ] `findById()` → `.singleOrNull()?.toDriver()`
- [ ] `findByEmail()` → `.singleOrNull()?.toDriver()`
- [ ] `findByLicenseNumber()` → `.singleOrNull()?.toDriver()`
- [ ] `findActiveAssignmentByVehicle()` → `.singleOrNull()?.toAssignment()`
- [ ] `findActiveAssignmentByDriver()` → `.singleOrNull()?.toAssignment()`
- [ ] `findActiveShift()` → `.singleOrNull()?.toDriverShift()`
- [ ] `save()` → `.select(id).limit(1).singleOrNull() != null`

---

## Before & After Comparison

### Pattern A: `.map{}.singleOrNull()` → `.singleOrNull()?.toX()` — 6 locations

**BEFORE** — `findById()`, `findByEmail()`, `findByLicenseNumber()`, `findActiveAssignmentByVehicle()`, `findActiveAssignmentByDriver()`, `findActiveShift()`:
```kotlin
DriversTable
    .selectAll()
    .where { DriversTable.id eq UUID.fromString(id.value) }
    .map { it.toDriver() }   // ← creates NEW ArrayList, maps row into it
    .singleOrNull()            // ← reads index 0, discards the list
```

**AFTER**:
```kotlin
DriversTable
    .selectAll()
    .where { DriversTable.id eq UUID.fromString(id.value) }
    .singleOrNull()            // ← returns ResultRow? directly (no list)
    ?.toDriver()               // ← maps inline only if non-null
```

**Per-call savings**: ~80 bytes × 6 methods = ~480 bytes/request cycle eliminated

---

### Pattern B: `.count() > 0` → `.select(id).limit(1)` — 1 location

**BEFORE** — `save()`:
```sql
SELECT COUNT(*) FROM drivers WHERE id = '...'
```

**AFTER**:
```sql
SELECT drivers.id FROM drivers WHERE drivers.id = '...' LIMIT 1
```

| Aspect | Before | After |
|---|---|---|
| Columns fetched | All (`*`) | 1 (`id` only) |
| DB scan | Counts all matches | Stops at first match |

---

### Aggregate Impact

| Category | Before | After |
|---|---|---|
| ArrayList allocations per request | Up to 6 (one per lookup method) | 0 |
| Garbage per driver lookup cycle | ~480 bytes | ~0 |
| SQL exists-check | `SELECT COUNT(*)` | `SELECT id LIMIT 1` |
| API response output | Identical | Identical ✅ |
