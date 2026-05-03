# Phase 1 — Module: Users RAM Optimization

> **Scope**: `modules/users/infrastructure/persistence/UserRepositoryImpl.kt`  
> **Goal**: Eliminate intermediate list allocations and redundant count queries  
> **Risk**: None — same query results, same API output  
> **Status**: Complete ✅ (2026-05-02)

---

## 1. staffProfile Lookups — Eliminate Intermediate Lists

**File**: `modules/users/infrastructure/persistence/UserRepositoryImpl.kt`

### findById() — staffProfile query, Line 68-73

```diff
             val staffProfile =
                 StaffProfilesTable
                     .selectAll()
                     .where { StaffProfilesTable.userId eq UUID.fromString(id.value) }
-                    .map { it.toStaffProfile() }
                     .singleOrNull()
+                    ?.toStaffProfile()
```

### findByEmail() — staffProfile query, Line 91-96

Same pattern — `.singleOrNull()?.toStaffProfile()`

### findAll() — inner staffProfile query, Line 190-195

Same pattern — `.singleOrNull()?.toStaffProfile()`

### findRoleByName() — Line 205-212

```diff
     override suspend fun findRoleByName(name: String): Role? =
         dbQuery {
             RolesTable
                 .selectAll()
                 .where { RolesTable.name eq name }
-                .map { it.toRole() }
                 .singleOrNull()
+                ?.toRole()
         }
```

**Skill Ref**: §2 — Avoid intermediate collections

---

## 2. save() — Optimize Exists Checks

### User exists check — Line 106

```diff
-            val exists = UsersTable.selectAll().where { UsersTable.id eq userUuid }.count() > 0
+            val exists = UsersTable
+                .select(UsersTable.id)
+                .where { UsersTable.id eq userUuid }
+                .limit(1)
+                .singleOrNull() != null
```

### Staff profile exists check — Line 146-150

```diff
                 val profileExists =
                     StaffProfilesTable
-                        .selectAll()
+                        .select(StaffProfilesTable.id)
                         .where { StaffProfilesTable.id eq profile.id }
-                        .count() > 0
+                        .limit(1)
+                        .singleOrNull() != null
```

**Skill Ref**: §8 — Avoiding Iterator Overhead

---

## Checklist

- [x] `findById()` staffProfile — `.map{}.singleOrNull()` → `.singleOrNull()?.toStaffProfile()`
- [x] `findByEmail()` staffProfile — same pattern
- [x] `findAll()` inner staffProfile — same pattern
- [x] `findRoleByName()` — `.map{}.singleOrNull()` → `.singleOrNull()?.toRole()`
- [x] `save()` user exists — `.count() > 0` → `.select(id).limit(1).singleOrNull() != null`
- [x] `save()` profile exists — `.count() > 0` → `.select(id).limit(1).singleOrNull() != null`

---

## Before & After Comparison

### Pattern A: `.map{}.singleOrNull()` → `.singleOrNull()?.toX()` — 4 locations

**BEFORE** — `findById()` staffProfile, `findByEmail()` staffProfile, `findAll()` inner staffProfile, `findRoleByName()`:
```kotlin
StaffProfilesTable
    .selectAll()
    .where { StaffProfilesTable.userId eq UUID.fromString(id.value) }
    .map { it.toStaffProfile() }   // ← creates NEW ArrayList per user lookup
    .singleOrNull()
```

**AFTER**:
```kotlin
StaffProfilesTable
    .selectAll()
    .where { StaffProfilesTable.userId eq UUID.fromString(id.value) }
    .singleOrNull()                // ← returns ResultRow? directly (no list)
    ?.toStaffProfile()             // ← maps inline only if non-null
```

**Per-call savings**: ~80 bytes × 4 methods

> **Multiplied effect in `findAll()`**: The inner staffProfile query runs **once per user** in the result set. With 20 users → 20 unnecessary `ArrayList` objects eliminated per `findAll()` call.

---

### Pattern B: `.count() > 0` → `.select(id).limit(1)` — 2 locations

**BEFORE** — `save()` user exists + staff profile exists:
```sql
SELECT COUNT(*) FROM users WHERE id = '...'
SELECT COUNT(*) FROM staff_profiles WHERE id = '...'
```

**AFTER**:
```sql
SELECT users.id FROM users WHERE users.id = '...' LIMIT 1
SELECT staff_profiles.id FROM staff_profiles WHERE staff_profiles.id = '...' LIMIT 1
```

| Aspect | Before | After |
|---|---|---|
| Columns fetched | All (`*`) | 1 (`id` only) |
| DB scan | Counts all matches | Stops at first match |

---

### Aggregate Impact

| Category | Before | After |
|---|---|---|
| ArrayList per single user lookup | 1 (staffProfile sub-query) | 0 |
| ArrayList per `findAll()` (N users) | N (one per user) | 0 |
| SQL exists-checks per save | 2× `SELECT COUNT(*)` | 2× `SELECT id LIMIT 1` |
| API response output | Identical | Identical ✅ |
