# Phase 1 — Module: Rentals RAM Optimization

> **Scope**: `modules/rentals/` — `RentalRepositoryImpl.kt`, `Rental.kt`  
> **Goal**: Eliminate intermediate list allocations, redundant count queries, and temporary object creation  
> **Risk**: None — same query results, same API output  
> **Status**: Complete ✅ (2026-05-02)

---

## 1. RentalRepositoryImpl — Eliminate Intermediate Lists

**File**: `modules/rentals/infrastructure/persistence/RentalRepositoryImpl.kt`

### findById() — Line 48-55

```diff
     override suspend fun findById(id: RentalId): Rental? =
         dbQuery {
             RentalsTable
                 .selectAll()
                 .where { RentalsTable.id eq UUID.fromString(id.value) }
-                .map { it.toRental() }
                 .singleOrNull()
+                ?.toRental()
         }
```

### findByRentalNumber() — Line 78-85

Same pattern — replace `.map { it.toRental() }.singleOrNull()` → `.singleOrNull()?.toRental()`.

**Skill Ref**: §2 — Avoid intermediate collections

---

## 2. RentalRepositoryImpl — Optimize Exists Check in save()

Line 89-93 uses `.selectAll().where { ... }.count() > 0`. Replace with `.select(RentalsTable.id).where { ... }.limit(1).singleOrNull() != null`.

**Skill Ref**: §8 — Avoiding Iterator Overhead

---

## 3. Rental.kt — Eliminate Duration Object in durationDays()

**File**: `modules/rentals/domain/model/Rental.kt`

Replace `java.time.Duration.between(start, end).toDays().toInt()` → `ChronoUnit.DAYS.between(start, end).toInt()`. Same result, no intermediate `Duration` allocation.

**Skill Ref**: §3 — Primitive optimization

---

## Checklist

- [x] `findById()` — `.map{}.singleOrNull()` → `.singleOrNull()?.toRental()`
- [x] `findByRentalNumber()` — same pattern
- [x] `save()` — `.count() > 0` → `.select(id).limit(1).singleOrNull() != null`
- [x] `Rental.durationDays()` — `Duration.between()` → `ChronoUnit.DAYS.between()`

---

## Before & After Comparison

### Pattern A: `.map{}.singleOrNull()` → `.singleOrNull()?.toX()` — 2 locations

**BEFORE** — `findById()` / `findByRentalNumber()`:
```kotlin
RentalsTable
    .selectAll()
    .where { RentalsTable.id eq UUID.fromString(id.value) }
    .map { it.toRental() }   // ← creates NEW ArrayList, maps row into it
    .singleOrNull()            // ← reads index 0, discards the list
```

**AFTER**:
```kotlin
RentalsTable
    .selectAll()
    .where { RentalsTable.id eq UUID.fromString(id.value) }
    .singleOrNull()            // ← returns ResultRow? directly (no list)
    ?.toRental()               // ← maps inline only if non-null
```

**Per-call savings**: ~80 bytes × 2 methods = ~160 bytes/request cycle eliminated

---

### Pattern B: `.count() > 0` → `.select(id).limit(1)` — 1 location

**BEFORE** — `save()`:
```sql
SELECT COUNT(*) FROM rentals WHERE id = '...'
```

**AFTER**:
```sql
SELECT rentals.id FROM rentals WHERE rentals.id = '...' LIMIT 1
```

| Aspect | Before | After |
|---|---|---|
| Columns fetched | All (`*`) | 1 (`id` only) |
| DB scan | Counts all matches | Stops at first match |

---

### Pattern C: `Duration.between()` → `ChronoUnit.DAYS.between()` — 1 location

**BEFORE** — `Rental.durationDays()`:
```kotlin
java.time.Duration
    .between(start, end)  // ← allocates a Duration object on the heap (~40 bytes)
    .toDays()
    .toInt()
    .coerceAtLeast(1)
```

**AFTER**:
```kotlin
ChronoUnit.DAYS.between(start, end)  // ← returns Long directly, no object allocated
    .toInt()
    .coerceAtLeast(1)
```

**Per-call savings**: ~40 bytes (`Duration` object with two `long` fields + header)

---

### Aggregate Impact

| Category | Before | After |
|---|---|---|
| ArrayList allocations per lookup | 1 per call (2 methods) | 0 |
| SQL exists-check | `SELECT COUNT(*)` | `SELECT id LIMIT 1` |
| Duration object per `durationDays()` | 1 (`~40 bytes`) | 0 |
| API response output | Identical | Identical ✅ |
