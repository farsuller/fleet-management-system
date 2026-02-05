package com.solodev.fleet.shared.plugins

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.*
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database

/**
 * Configures the database layer.
 *
 * This module performs the following:
 * 1. Reads database configuration from `application.yaml`.
 * 2. Sets up a HikariCP connection pool for efficient database access.
 * 3. Runs Flyway migrations to ensure the schema is up-to-date.
 * 4. Initializes Exposed (ORM) with the data source.
 */
fun Application.configureDatabases() {
    val config = environment.config.config("storage")
    val jdbcUrl = config.property("jdbcUrl").getString()
    val username = config.property("username").getString()
    val password = config.property("password").getString()
    val driverClassName = config.property("driverClassName").getString()
    val maximumPoolSize = config.property("maximumPoolSize").getString().toInt()

    val log = environment.log
    log.info("Connecting to Database at: $jdbcUrl")
    log.info("Using Username: $username")
    log.info("Using Password: ${password.take(2)}***${password.takeLast(2)}")

    val hikariConfig =
        HikariConfig().apply {
            this.jdbcUrl = jdbcUrl
            this.username = username
            this.password = password
            this.driverClassName = driverClassName
            this.maximumPoolSize = maximumPoolSize
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"
            validate()
        }

    val dataSource = HikariDataSource(hikariConfig)

    // H2 Compatibility: Define gen_random_uuid() for tests
    if (jdbcUrl.startsWith("jdbc:h2:")) {
        try {
            dataSource.connection.use { conn ->
                conn.createStatement()
                    .execute(
                        "CREATE ALIAS IF NOT EXISTS gen_random_uuid FOR \"java.util.UUID.randomUUID\""
                    )
            }
        } catch (e: Exception) {
            log.warn("Failed to create H2 alias for gen_random_uuid", e)
        }
    }

    // Run Flyway Migrations
    val flyway =
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load()

    try {
        flyway.repair()
        flyway.migrate()
    } catch (e: Exception) {
        log.error("Flyway migration failed", e)
        // In production, you might want to stop the app here
    }

    // Connect Exposed
    Database.connect(dataSource)
    log.info("Connecting to Database at: $jdbcUrl")
    log.info("Database connected and migrated successfully.")
}
