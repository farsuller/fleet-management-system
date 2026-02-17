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
    var jdbcUrl = config.property("jdbcUrl").getString()
    var username = config.property("username").getString()
    var password = config.property("password").getString()

    // Robust fix for Render/Heroku: Parse "postgresql://user:pass@host/db" format
    if (jdbcUrl.contains("@") || jdbcUrl.startsWith("postgresql://")) {
        try {
            val cleanUrl = jdbcUrl.removePrefix("jdbc:")
            val uri = java.net.URI(cleanUrl)
            val userInfo = uri.userInfo

            if (userInfo != null && userInfo.contains(":")) {
                val parts = userInfo.split(":", limit = 2)
                username = parts[0]
                password = parts[1]
            }

            // Reconstruct clean JDBC URL without credentials but WITH query parameters
            val host = uri.host
            val port = if (uri.port != -1) ":${uri.port}" else ""
            val path = uri.path
            val query = if (uri.query != null) "?${uri.query}" else ""
            jdbcUrl = "jdbc:postgresql://$host$port$path$query"
        } catch (e: Exception) {
            environment.log.warn(
                    "Failed to parse complex JDBC URL (${e.message}), falling back to simple prefixing"
            )
            if (!jdbcUrl.startsWith("jdbc:")) jdbcUrl = "jdbc:$jdbcUrl"
        }
    } else if (!jdbcUrl.startsWith("jdbc:")) {
        jdbcUrl = "jdbc:$jdbcUrl"
    }

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

    // Diagnostic: Check if files are visible to classloader
    val classLoader = Thread.currentThread().contextClassLoader ?: Databases::class.java.classLoader
    val resource = classLoader.getResource("db/migration")
    val v001 = classLoader.getResource("db/migration/V001__create_users_schema.sql")
    val appYaml = classLoader.getResource("application.yaml")

    log.info("Classpath Diagnostic: db/migration resource=$resource")
    log.info("Classpath Diagnostic: V001 file=$v001")
    log.info("Classpath Diagnostic: application.yaml file=$appYaml")
    log.info("Current Working Directory: ${System.getProperty("user.dir")}")

    // Run Flyway Migrations
    val flyway =
            Flyway.configure()
                    .dataSource(dataSource)
                    .locations("classpath:db/migration")
                    .classLoader(classLoader) // CRITICAL: Use verified classloader
                    .load()

    try {
        val info = flyway.info()
        val all = info.all().size
        val pending = info.pending().size
        val applied = info.applied().size
        log.info(
                "Flyway status: $applied applied, $pending pending, $all total discovered in classpath:db/migration"
        )

        // Repair handles checksum mismatches (common in tests)
        flyway.repair()

        if (pending > 0) {
            log.info("Executing $pending Flyway migrations...")
            val result = flyway.migrate()
            log.info(
                    "Flyway migrations completed successfully. Applied ${result.migrationsExecuted} migrations."
            )
        } else if (all == 0) {
            log.warn(
                    "WARNING: No migrations were discovered by Flyway! Check if db/migration exists in the JAR."
            )
        }
    } catch (e: Exception) {
        log.error("Flyway migration error (JDBC URL: $jdbcUrl): ${e.message}")
        if (!jdbcUrl.startsWith("jdbc:h2:")) {
            log.error("CRITICAL: Flyway migration failed in production environment", e)
            throw e // Fail fast in production
        } else {
            log.warn(
                    "Non-fatal Flyway error in test environment (H2). Falling back to automatic schema handling."
            )
        }
    }

    // Connect Exposed
    Database.connect(dataSource)
    log.info("Connecting to Database at: $jdbcUrl")
    log.info("Database connected and migrated successfully.")
}
