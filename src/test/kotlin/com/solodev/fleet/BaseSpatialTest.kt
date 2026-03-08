package com.solodev.fleet

import com.solodev.fleet.modules.tracking.infrastructure.persistence.GeofencesTable
import com.solodev.fleet.modules.tracking.infrastructure.persistence.RoutesTable
import com.solodev.fleet.modules.vehicles.infrastructure.persistence.VehiclesTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

/**
 * Base class for integration tests requiring PostGIS. Sets up a real PostgreSQL container with the
 * PostGIS extension via Testcontainers.
 *
 * Requirements:
 *  - macOS/Linux: Docker Desktop or Docker Engine running
 *  - Windows: Docker Desktop with WSL2 backend + a WSL2 distro (`wsl --install Ubuntu`)
 *  - CI (GitHub Actions): Docker pre-installed on ubuntu runners — works out of the box
 *
 * If Docker is not reachable the test class is SKIPPED (not failed).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
open class BaseSpatialTest {

    companion object {
        private val postgisImage =
                DockerImageName.parse("postgis/postgis:15-3.3")
                        .asCompatibleSubstituteFor("postgres")

        val container by lazy {
            PostgreSQLContainer<Nothing>(postgisImage).apply {
                withDatabaseName("fleet_test")
                withUsername("test")
                withPassword("test")
                start()
            }
        }

        /**
         * Uses Testcontainers' own client factory to probe Docker — accurately reflects
         * whether containers can actually start (not just whether a port accepts TCP).
         */
        fun isDockerAvailable(): Boolean =
            try { DockerClientFactory.instance().isDockerAvailable } catch (_: Exception) { false }
    }

    @BeforeAll
    fun setup() {
        assumeTrue(
            isDockerAvailable(),
            "Skipping PostGIS integration test — Docker not reachable. " +
            "On Windows: install a WSL2 distro (wsl --install Ubuntu) and enable Docker Desktop WSL2 integration."
        )

        Database.connect(
                url = container.jdbcUrl,
                driver = "org.postgresql.Driver",
                user = container.username,
                password = container.password
        )

        transaction {
            exec("CREATE EXTENSION IF NOT EXISTS postgis;")
            SchemaUtils.create(VehiclesTable, RoutesTable, GeofencesTable)
        }
    }
}

