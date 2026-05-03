# Phase 1 — Module: Accounts RAM Optimization ✅ COMPLETE

> **Scope**: `modules/accounts/infrastructure/persistence/` — AccountRepositoryImpl, InvoiceRepositoryImpl, PaymentMethodRepositoryImpl, LedgerRepositoryImpl  
> **Goal**: Eliminate intermediate list allocations and redundant count queries  
> **Risk**: None — same query results, same API output  
> **Status**: ✅ Applied, compiled, tests passed

---

## 1. AccountRepositoryImpl — Eliminate Intermediate Lists

**File**: `modules/accounts/infrastructure/persistence/AccountRepositoryImpl.kt`

### findById() — Line 33-40

```diff
     override suspend fun findById(id: AccountId): Account? =
         dbQuery {
             AccountsTable
                 .selectAll()
                 .where { AccountsTable.id eq UUID.fromString(id.value) }
-                .map { it.toAccount() }
                 .singleOrNull()
+                ?.toAccount()
         }
```

### findByCode() — Line 42-49

```diff
     override suspend fun findByCode(accountCode: String): Account? =
         dbQuery {
             AccountsTable
                 .selectAll()
                 .where { AccountsTable.accountCode eq accountCode }
-                .map { it.toAccount() }
                 .singleOrNull()
+                ?.toAccount()
         }
```

**Skill Ref**: §2 — Avoid intermediate collections

---

## 2. AccountRepositoryImpl — Optimize Exists Check in save()

**Line 56**: `.selectAll().where { ... }.count() > 0`

```diff
-            val exists = AccountsTable.selectAll().where { AccountsTable.id eq accountUuid }.count() > 0
+            val exists = AccountsTable
+                .select(AccountsTable.id)
+                .where { AccountsTable.id eq accountUuid }
+                .limit(1)
+                .singleOrNull() != null
```

---

## 3. InvoiceRepositoryImpl — Eliminate Intermediate Lists

**File**: `modules/accounts/infrastructure/persistence/InvoiceRepositoryImpl.kt`

### findById() — Line 46-53

```diff
-                .map { it.toInvoice() }
                 .singleOrNull()
+                ?.toInvoice()
```

### findByInvoiceNumber() — Line 55-62

Same pattern — `.singleOrNull()?.toInvoice()`

### findByRentalId() — Line 72-79

Same pattern — `.singleOrNull()?.toInvoice()`

### save() — Line 85

`.count() > 0` → `.select(InvoicesTable.id).limit(1).singleOrNull() != null`

**Skill Ref**: §2 — Avoid intermediate collections, §8 — Iterator overhead

---

## 4. PaymentMethodRepositoryImpl — Eliminate Intermediate Lists + Exists Check

**File**: `modules/accounts/infrastructure/persistence/PaymentMethodRepositoryImpl.kt`

### findById() — Line 58-66

```diff
-                .map { it.toPaymentMethod() }
                 .singleOrNull()
+                ?.toPaymentMethod()
```

### findByCode() — Line 68-76

Same pattern — `.singleOrNull()?.toPaymentMethod()`

### save() — Line 96-100

`.count() > 0` → `.select(PaymentMethodsTable.id).limit(1).singleOrNull() != null`

---

## 5. LedgerRepositoryImpl — Optimize Exists Check

**File**: `modules/accounts/infrastructure/persistence/LedgerRepositoryImpl.kt`

**Line 105**: `.count() > 0` → `.select(id).limit(1).singleOrNull() != null`

---

## Checklist

- [x] `AccountRepositoryImpl.findById()` → `.singleOrNull()?.toAccount()`
- [x] `AccountRepositoryImpl.findByCode()` → `.singleOrNull()?.toAccount()`
- [x] `AccountRepositoryImpl.save()` → `.select(id).limit(1).singleOrNull() != null`
- [x] `InvoiceRepositoryImpl.findById()` → `.singleOrNull()?.toInvoice()`
- [x] `InvoiceRepositoryImpl.findByInvoiceNumber()` → `.singleOrNull()?.toInvoice()`
- [x] `InvoiceRepositoryImpl.findByRentalId()` → `.singleOrNull()?.toInvoice()`
- [x] `InvoiceRepositoryImpl.save()` → `.select(id).limit(1).singleOrNull() != null`
- [x] `PaymentMethodRepositoryImpl.findById()` → `.singleOrNull()?.toPaymentMethod()`
- [x] `PaymentMethodRepositoryImpl.findByCode()` → `.singleOrNull()?.toPaymentMethod()`
- [x] `PaymentMethodRepositoryImpl.save()` → `.select(id).limit(1).singleOrNull() != null`
- [x] `LedgerRepositoryImpl.save()` → `.select(id).limit(1).singleOrNull() != null`

---

## Before & After Comparison

### Pattern A: `.map{}.singleOrNull()` → `.singleOrNull()?.toX()` — 7 locations

**BEFORE** — allocates an intermediate `ArrayList` on every call:
```kotlin
AccountsTable
    .selectAll()
    .where { AccountsTable.id eq UUID.fromString(id.value) }
    .map { it.toAccount() }   // ← creates NEW ArrayList, maps ALL rows into it
    .singleOrNull()            // ← reads index 0, discards the list
```

**AFTER** — maps only the single result, zero list allocation:
```kotlin
AccountsTable
    .selectAll()
    .where { AccountsTable.id eq UUID.fromString(id.value) }
    .singleOrNull()            // ← returns ResultRow? directly (no list created)
    ?.toAccount()              // ← maps inline only if non-null
```

**Per-call heap savings**:

| Allocation | Before | After | Saved |
|---|---|---|---|
| `ArrayList` object header | 16 bytes | 0 | 16 bytes |
| Backing `Object[]` (default capacity 10) | 56 bytes | 0 | 56 bytes |
| Internal fields (size, modCount) | 8 bytes | 0 | 8 bytes |
| **Total per call** | **~80 bytes** | **0** | **~80 bytes** |

> At 100 req/min across 7 methods → **Before**: 700 ArrayList objects/min (~56 KB/min garbage) → **After**: 0

---

### Pattern B: `.count() > 0` → `.select(id).limit(1)` — 4 locations

**BEFORE** — generates `SELECT COUNT(*)` over all columns:
```sql
-- Generated SQL
SELECT COUNT(*) FROM accounts WHERE id = '...'
```

**AFTER** — probes for one row, one column:
```sql
-- Generated SQL
SELECT accounts.id FROM accounts WHERE accounts.id = '...' LIMIT 1
```

**Per-call difference**:

| Aspect | Before (`COUNT(*)`) | After (`SELECT id LIMIT 1`) |
|---|---|---|
| Columns fetched | All (`*`) | 1 (`id` only) |
| DB scan | Counts all matches | Stops at first match |
| Network payload | Larger (count result) | Smaller (single column) |

---

### Aggregate Impact

| Category | Before | After |
|---|---|---|
| ArrayList allocations per lookup | 1 per call (7 methods) | 0 |
| SQL exists-check efficiency | `SELECT COUNT(*)` all columns | `SELECT id LIMIT 1` |
| Garbage per minute (at 100 req/min) | ~56 KB/min of ArrayList garbage | ~0 |
| API response output | Identical | Identical ✅ |
| Compilation | — | BUILD SUCCESSFUL ✅ |
| Tests | — | BUILD SUCCESSFUL ✅ |
