# Phase 6 — PostGIS Spatial Extensions

## Status
- Overall: **Planned**
- Implementation Date: TBD
- **Verification Responsibility**:
    - **Lead Developer (USER)**: Write/Run Testcontainers integration tests & Flyway migration validation.
    - **Tech Lead (Antigravity)**: Audit `PostGISColumnType` implementation and spatial SQL performance.

---

## Purpose
Enable spatial intelligence in the Fleet Management backend by integrating PostGIS. This provides the foundational "Source of Truth" for route geometries and high-performance spatial queries (snapping, distance, geofencing).

---

## Technical Strategy (Senior Level)

### 1. Clean Architecture Alignment
- **Domain Layer**: `Location` and `Polyline` as Value Objects in the `shared` module.
- **Use Case Layer**: 
    - `UpdateVehicleLocationUseCase`: Orchestrates the persistence of raw coordinates and the snapping logic.
- **Infrastructure Layer**: 
    - `PostGISAdapter`: Conceals the `ST_` SQL complexity from the domain layer.

### 2. Observability (Golden Signals)
- **Latency**: Track duration of `ST_LineLocatePoint` calls via Micrometer.
- **Errors**: Monitor GIST index misses or null geometries during snapping.
- **Saturation**: Measure DB CPU usage during peak ingestion (spatial queries are CPU-intensive).

---

---

## Dependencies & Setup

### build.gradle.kts
```kotlin
dependencies {
    // --- PostGIS Spatial Support ---
    implementation("org.postgis:postgis-jdbc:2.5.0") // Needed for PG driver to parse Geometry types
}
```

### Database Migration (Flyway)
In `src/main/resources/db/migration/V4__Add_PostGIS.sql`:
```sql
CREATE EXTENSION IF NOT EXISTS postgis;

ALTER TABLE routes ADD COLUMN polyline GEOMETRY(LineString, 4326);
CREATE INDEX idx_routes_polyline ON routes USING GIST (polyline);

ALTER TABLE vehicles ADD COLUMN last_location GEOMETRY(Point, 4326);
CREATE INDEX idx_vehicles_location ON vehicles USING GIST (last_location);
```

---

## Technical Risks & Code-Level Solutions

### 1. H2 Testing Conflict (Testcontainers Solution)
** blocker**: H2 does not support PostGIS.
**Solution**: Use `testcontainers-postgresql` with PostGIS image.

#### Base Integration Test Skeleton
```kotlin
abstract class BaseSpatialTest {
    companion object {
        private val container = PostgreSQLContainer<Nothing>("postgis/postgis:15-3.3").apply {
            withDatabaseName("fleet_test")
            withUsername("test")
            withPassword("test")
            start()
        }

        val database = Database.connect(
            url = container.jdbcUrl,
            driver = "org.postgresql.Driver",
            user = container.username,
            password = container.password
        )
    }
}
```

### 2. Dependency Management (Catalog Updates)
**Missing Dependencies** to add to `libs.versions.toml`:
```toml
[versions]
postgis-jdbc = "2.5.0"
testcontainers = "1.19.3"

[libraries]
postgis-jdbc = { module = "org.postgis:postgis-jdbc", version.ref = "postgis-jdbc" }
testcontainers-postgresql = { module = "org.testcontainers:postgresql", version.ref = "testcontainers" }
```

---

## Code Implementation: PostGIS + Exposed
...

### Custom Geometry Column Type
We need to teach Exposed how to handle `PGgeometry` objects.

```kotlin
class PostGISColumnType : ColumnType() {
    override fun sqlType(): String = "GEOMETRY"
    override fun valueFromDB(value: Any): Any = (value as? PGgeometry) ?: value
    override fun notNullValueToDB(value: Any): Any = (value as? PGgeometry) ?: value
}

fun Table.geometry(name: String): Column<PGgeometry> = registerColumn(name, PostGISColumnType())
```

### Spatial Query Wrapper
```kotlin
object SpatialFunctions {
    fun stLineLocatePoint(line: Expression<*>, point: Expression<*>): Function<Double> =
        CustomFunction("ST_LineLocatePoint", DoubleColumnType(), line, point)
}
```

---

## Application Method

1. **Initialization**: Flyway automatically enables the extension and creates columns.
2. **Persistence**: Use the `geometry` column in your `Routes` and `Vehicles` table definitions.
3. **Usage**:
    ```kotlin
    val progress = Routes.select { ... }
        .adjustSlice { slice(SpatialFunctions.stLineLocatePoint(polyline, locationPoint)) }
        .single()[0] as Double
    ```

---

## Technical Risks & Blockers

### 1. H2 Testing Conflict
- **Issue**: Our current test suite uses H2 for speed. H2 does NOT support PostGIS functions (`ST_LineLocatePoint`, etc.) natively.
- **Mitigation**: 
    - Use **Testcontainers** with a real PostgreSQL/PostGIS image for spatial integration tests.
    - Continue using H2 for non-spatial unit tests to maintain speed.

### 2. Dependency Management
- **Issue**: `ktor-server-websockets` and `postgis-jdbc` are missing from `gradle/libs.versions.toml`.
- **Mitigation**: Add them to the version catalog before implementation.

---

## Implementation Steps
1. [ ] **DB Migration**: Create and run the Flyway migration for PostGIS.
2. [ ] **Exposed Mapping**: Implement `PostGISColumnType` in `shared/persistence`.
3. [ ] **Utility Layer**: Implement `stLineLocatePoint` function wrapper.
4. [ ] **Seeding**: Update seed data with actual coordinate polylines for testing.

---

## 🏁 Definition of Done (Phase 6)
- [ ] PostGIS extension active in production and local environments.
- [ ] `LineString` geometries stored correctly for at least 3 test routes.
- [ ] Spatial queries (`ST_Distance`) return accurate results in unit tests.
