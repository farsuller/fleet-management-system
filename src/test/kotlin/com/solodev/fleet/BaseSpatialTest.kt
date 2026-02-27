package com.solodev.fleet

import com.solodev.fleet.modules.tracking.infrastructure.persistence.GeofencesTable
import com.solodev.fleet.modules.tracking.infrastructure.persistence.RoutesTable
import com.solodev.fleet.modules.vehicles.infrastructure.persistence.VehiclesTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

/**
 * Base class for integration tests requiring PostGIS. Sets up a real PostgreSQL container with the
 * PostGIS extension.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
open class BaseSpatialTest {

    companion object {
        private val postgisImage =
                DockerImageName.parse("postgis/postgis:15-3.3")
                        .asCompatibleSubstituteFor("postgres")

        val container =
                PostgreSQLContainer<Nothing>(postgisImage).apply {
                    withDatabaseName("fleet_test")
                    withUsername("test")
                    withPassword("test")
                    start()
                }
    }

    @BeforeAll
    fun setup() {
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
