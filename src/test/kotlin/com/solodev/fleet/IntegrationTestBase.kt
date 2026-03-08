package com.solodev.fleet

import com.solodev.fleet.shared.utils.JwtService
import io.ktor.server.config.*
import io.ktor.server.testing.*
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

/**
 * Base class for HTTP-level integration tests.
 *
 * Provides:
 * - A singleton [PostgreSQLContainer] with PostGIS, shared across all suites in the JVM.
 * - [buildTestConfig] — MapApplicationConfig wiring the container into the Ktor app.
 * - [configurePostgres] — ApplicationTestBuilder extension for use inside testApplication { }.
 * - [tokenFor] — generates a signed JWT directly (no DB user required) for auth-required tests.
 * - [cleanDatabase] — TRUNCATE of all transactional tables; preserves seed data (roles, accounts).
 *
 * Each integration test class should:
 *   1. Extend this class.
 *   2. Call [cleanDatabase] in @BeforeEach.
 *   3. Use [configurePostgres] + [module] inside testApplication { }.
 *
 * Docker requirements (same as BaseSpatialTest):
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
         * Singleton container — starts once per JVM, reused by all integration test suites.
         * Flyway migrations run on first [module] startup and are not re-applied (idempotent).
         */
        val postgres: PostgreSQLContainer<Nothing> by lazy {
            PostgreSQLContainer<Nothing>(postgisImage).apply {
                withDatabaseName("fleet_test")
                withUsername("fleet_user")
                withPassword("test_password")
                start()
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
    }

    /**
     * Truncates all transactional tables in foreign-key safe order (CASCADE).
     * Seed/reference tables are deliberately preserved:
     *  - [roles] — seeded by V001
     *  - [accounts] — chart of accounts seeded by V011
     *  - [payment_methods] — seeded by V012
     *  - [routes], [geofences] — spatial reference data seeded by V018
     *
     * Call this from @BeforeEach in each integration test class to ensure a clean slate.
     */
    fun cleanDatabase() {
        postgres.createConnection("").use { conn ->
            conn.createStatement().execute(
                """
                TRUNCATE TABLE
                    location_history,
                    idempotency_keys,
                    dlq_messages,
                    inbox_processed_messages,
                    outbox_events,
                    payments,
                    invoice_line_items,
                    invoices,
                    ledger_entry_lines,
                    ledger_entries,
                    maintenance_schedules,
                    maintenance_parts,
                    maintenance_jobs,
                    rental_payments,
                    rental_charges,
                    rental_periods,
                    rentals,
                    customers,
                    odometer_readings,
                    vehicles,
                    verification_tokens,
                    staff_profiles,
                    user_roles,
                    users
                CASCADE
                """.trimIndent()
            )
        }
    }
}

/**
 * Configures the [testApplication] environment to use the shared PostGIS container.
 * Drop-in replacement for ApplicationTest's configureH2().
 *
 * Usage:
 * ```kotlin
 * @Test
 * fun shouldCreateVehicle_WhenAdminRequests() = testApplication {
 *     configurePostgres()
 *     application { module() }
 *     // ...
 * }
 * ```
 */
fun ApplicationTestBuilder.configurePostgres() {
    environment {
        config = IntegrationTestBase.buildTestConfig()
    }
}
