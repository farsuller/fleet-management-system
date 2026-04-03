package com.solodev.fleet

import com.solodev.fleet.shared.utils.JwtService
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.ApplicationTestBuilder
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.sql.ResultSet

/**
 * Base class for all integration tests (HTTP-level and repository-level).
 *
 * Provides:
 * - A singleton [PostgreSQLContainer] with PostGIS, shared across all suites in the JVM.
 * - [buildTestConfig] — MapApplicationConfig wiring the container into the Ktor app.
 * - [configurePostgres] — ApplicationTestBuilder extension for use inside testApplication { }.
 * - [tokenFor] — generates a signed JWT directly (no DB user required) for auth-required tests.
 * - [cleanDatabase] — resilient per-table TRUNCATE using a PL/pgSQL DO block; preserves seed data.
 *
 * Each integration test class should:
 *   1. Extend this class.
 *   2. Call [cleanDatabase] in @BeforeEach.
 *   3. For HTTP tests: use [configurePostgres] + [module] inside testApplication { }.
 *   4. For repository tests: use Exposed [transaction] directly — Exposed is pre-wired in @BeforeAll.
 *
 * Docker requirements:
 *  - macOS/Linux: Docker Desktop or Docker Engine running
 *  - Windows: Docker Desktop with TCP socket exposed on localhost:2375
 *              (Settings → General → "Expose daemon on tcp://localhost:2375")
 *  - CI (GitHub Actions): works out of the box on ubuntu runners
 *
 * If Docker is not reachable the entire test class is SKIPPED (not failed).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class IntegrationTestBase {

    companion object {
        // Must match length requirements for HMAC-SHA256. Use a fixed string for reproducibility.
        const val JWT_SECRET = "test-secret-at-least-64-bytes-long-for-hmac-sha256-security-1234567890"
        const val JWT_ISSUER = "test-issuer"
        const val JWT_AUDIENCE = "test-audience"

        private val postgisImage =
            DockerImageName.parse("postgis/postgis:15-3.3-alpine")
                .asCompatibleSubstituteFor("postgres")

        /**
         * Seed / reference tables that must NEVER be truncated between tests.
         * Populated by Flyway migrations; truncating them breaks FK constraints.
         */
        private val SEED_TABLES = setOf(
            "flyway_schema_history",
            "spatial_ref_sys",
            "roles",
            "accounts",
            "payment_methods",
            "routes",
            "geofences",
        )

        /**
         * Singleton PostGIS container shared across all suites in the JVM.
         *
         * On first access:
         *  1. Starts the Docker container.
         *  2. Runs Flyway migrations (schema V001–V030).
         *  3. Calls [org.jetbrains.exposed.sql.Database.connect] so that Exposed
         *     [transaction] blocks work in repository-level test classes
         *     (e.g. UserRepositoryImplTest) without needing module() to have run first.
         */
        val postgres: PostgreSQLContainer<Nothing> by lazy {
            PostgreSQLContainer<Nothing>(postgisImage).apply {
                withDatabaseName("fleet_test")
                withUsername("fleet_user")
                withPassword("test_password")
                start()

                // Run Flyway migrations immediately so the schema is fully ready
                // before the first test's @BeforeEach fires.
                org.flywaydb.core.Flyway.configure()
                    .dataSource(jdbcUrl, username, password)
                    .locations("classpath:db/migration")
                    .baselineOnMigrate(true)
                    .load()
                    .migrate()

                // Wire Exposed ORM to this container.
                // Without this call, Exposed `transaction { }` inside repository
                // implementations would throw "No database connected" (or use a stale
                // connection from a previous test runner) in tests that never go through
                // testApplication { module() } → configureDatabases() → Database.connect().
                org.jetbrains.exposed.sql.Database.connect(
                    url      = jdbcUrl,
                    driver   = "org.postgresql.Driver",
                    user     = username,
                    password = password,
                )
            }
        }

        fun isDockerAvailable(): Boolean =
            try { DockerClientFactory.instance().isDockerAvailable } catch (_: Exception) { false }

        /**
         * Builds a [MapApplicationConfig] that routes the Ktor app to the test container.
         * Redis is disabled; all JWT values are fixed test constants.
         */
        fun buildTestConfig(): MapApplicationConfig = MapApplicationConfig(
            "storage.jdbcUrl"         to postgres.jdbcUrl,
            "storage.username"        to postgres.username,
            "storage.password"        to postgres.password,
            "storage.driverClassName" to "org.postgresql.Driver",
            "storage.maximumPoolSize" to "2",
            "jwt.secret"              to JWT_SECRET,
            "jwt.issuer"              to JWT_ISSUER,
            "jwt.audience"            to JWT_AUDIENCE,
            "jwt.realm"               to "test-realm",
            "jwt.expiresIn"           to "3600000",
            "redis.enabled"           to "false"
        )

        /**
         * Generates a signed JWT directly from the test secret.
         * No DB user registration or email verification required.
         *
         * Usage:
         * ```kotlin
         * val token = tokenFor("uuid-123", "admin@fleet.ph", "ADMIN")
         * client.get("/v1/vehicles") { bearerAuth(token) }
         * ```
         */
        fun tokenFor(id: String, email: String, vararg roles: String): String =
            JwtService(JWT_SECRET, JWT_ISSUER, JWT_AUDIENCE, expiresInMs = 3_600_000L)
                .generateToken(id, email, roles.toList())
    }

    @BeforeAll
    fun assumeDockerIsAvailable() {
        assumeTrue(
            isDockerAvailable(),
            "Skipping integration test — Docker not reachable. " +
                "On Windows: ensure Docker Desktop is running and the TCP socket is exposed " +
                "on localhost:2375 (Settings → General → Expose daemon on tcp://localhost:2375)."
        )
        // Force the postgres lazy property to initialise (starts container + Flyway + Exposed
        // connect) before any @BeforeEach or @Test method runs.  This guarantees cleanDatabase()
        // and repository transaction { } blocks have a live connection even for test classes that
        // never call testApplication { module() }.
        postgres
    }

    /**
     * Truncates all transactional tables using a resilient PL/pgSQL DO block.
     *
     * **Why a DO block instead of a single bulk TRUNCATE?**
     *
     * A single `TRUNCATE TABLE t1, t2, … CASCADE` fails with
     * `ERROR: relation "X" does not exist` if **any** table in the list is absent.
     * This happens when a Flyway migration partially ran — PostgreSQL rolls back a failed
     * migration's DDL transaction, so the table is gone, yet `information_schema.tables`
     * may still cache it until the connection is refreshed (timing window on Testcontainers).
     *
     * By wrapping each TRUNCATE in its own `BEGIN … EXCEPTION WHEN undefined_table … END` block,
     * absent tables are silently skipped and the remaining tables are still cleaned — the test
     * always starts from a known state regardless of which migrations succeeded.
     *
     * Seed / reference tables are preserved (see [SEED_TABLES]).
     */
    fun cleanDatabase() {
        postgres.createConnection("").use { conn ->
            // 1. Discover all BASE TABLEs present in the public schema.
            val tables = mutableListOf<String>()
            conn.createStatement().executeQuery(
                """
                SELECT table_name
                FROM   information_schema.tables
                WHERE  table_schema = 'public'
                  AND  table_type   = 'BASE TABLE'
                ORDER BY table_name
                """.trimIndent()
            ).use { rs: ResultSet ->
                while (rs.next()) tables.add(rs.getString("table_name"))
            }

            val tablesToTruncate = tables.filter { it !in SEED_TABLES }
            if (tablesToTruncate.isEmpty()) return

            // 2. Truncate each table inside its own PL/pgSQL sub-block so that a missing
            //    table (undefined_table / sqlstate 42P01) is caught and skipped rather than
            //    aborting the entire setup.
            //
            //    [Expected test-infrastructure behaviour — not a test assertion failure]
            val doBlock = buildString {
                append("DO \$\$\nBEGIN\n")
                for (table in tablesToTruncate) {
                    append(
                        "  BEGIN\n" +
                        "    TRUNCATE TABLE \"$table\" RESTART IDENTITY CASCADE;\n" +
                        "  EXCEPTION\n" +
                        "    WHEN undefined_table THEN NULL;\n" + // 42P01 — table not created yet
                        "    WHEN others          THEN NULL;\n" + // catch-all safety net
                        "  END;\n"
                    )
                }
                append("END \$\$;\n")
            }

            conn.createStatement().execute(doBlock)
        }
    }
}

/**
 * Configures the [testApplication] environment to use the shared PostGIS container.
 * Drop-in replacement for configureH2().
 *
 * Usage:
 * ```kotlin
 * @Test
 * fun shouldCreateVehicle() = testApplication {
 *     configurePostgres()
 *     application { module() }
 *     // …
 * }
 * ```
 */
fun ApplicationTestBuilder.configurePostgres() {
    environment {
        config = IntegrationTestBase.buildTestConfig()
    }
}
