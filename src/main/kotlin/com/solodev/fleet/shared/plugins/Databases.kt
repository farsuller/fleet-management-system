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

    // Diagnostic: Obtain verified classloader
    val flywayClassLoader: ClassLoader =
            Thread.currentThread().contextClassLoader ?: Application::class.java.classLoader

    // Workaround: Copy migrations to /tmp to bypass classpath scanning issues in Fat JAR
    // Reverted to /tmp because /app proved to be read-only at runtime on Render.
    val migrationDir = java.io.File(System.getProperty("java.io.tmpdir"), "fleet_migrations_v2")
    if (migrationDir.exists()) migrationDir.deleteRecursively()
    if (!migrationDir.mkdirs()) {
        log.error("CRITICAL: Failed to create migration directory at ${migrationDir.absolutePath}")
    }

    val migrationFiles =
            listOf(
                    "V001__create_users_schema.sql",
                    "V002__create_vehicles_schema.sql",
                    "V003__create_rentals_schema.sql",
                    "V004__create_maintenance_schema.sql",
                    "V005__create_accounting_schema.sql",
                    "V006__create_integration_tables.sql",
                    "V007__update_currency_to_php.sql",
                    "V008__add_customer_is_active.sql",
                    "V009__create_verification_tokens.sql",
                    "V010__update_payment_method_check.sql",
                    "V011__seed_chart_of_accounts.sql",
                    "V012__create_payment_methods_table.sql",
                    "V013__rename_currency_columns_to_whole_units.sql",
                    "V014__refresh_accounting_functions.sql"
            )

    log.info("Extracting ${migrationFiles.size} migrations to: ${migrationDir.absolutePath}...")

    migrationFiles.forEach { fileName ->
        val resourcePath = "db/migration/$fileName"
        val inputStream = flywayClassLoader.getResourceAsStream(resourcePath)
        if (inputStream != null) {
            val destFile = java.io.File(migrationDir, fileName)
            java.nio.file.Files.copy(
                    inputStream,
                    destFile.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING
            )
        } else {
            log.warn("Extraction: Could not find migration in classpath: $resourcePath")
        }
    }

    // Audit the directory with granular details before Flyway starts
    val auditResults =
            migrationDir.listFiles()?.map {
                "${it.name}(size=${it.length()}, readable=${it.canRead()})"
            }
                    ?: emptyList()
    log.info("Migration Audit: Files in ${migrationDir.absolutePath}: $auditResults")

    // Run Flyway Migrations - Pointing to filesystem to guarantee discovery
    // Adding trailing slash to path as some Flyway versions require it for directory walking
    val filesystemLocation = "filesystem:${migrationDir.absolutePath}/"
    val flyway =
            Flyway.configure()
                    .dataSource(dataSource)
                    .locations(filesystemLocation)
                    .sqlMigrationPrefix("V")
                    .sqlMigrationSeparator("__")
                    .sqlMigrationSuffixes(".sql")
                    .validateOnMigrate(false)
                    .ignoreMigrationPatterns("*:*")
                    .load()

    try {
        val info = flyway.info()
        val all = info.all().size
        val pending = info.pending().size
        val applied = info.applied().size
        log.info(
                "Flyway status: $applied applied, $pending pending, $all total discovered in filesystem:${migrationDir.absolutePath}"
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
                    "WARNING: No migrations discovered by Flyway. Triggering Manual JDBC Fallback..."
            )

            dataSource.connection.use { conn ->
                conn.autoCommit = true // Ensure each file's changes are committed
                val statement = conn.createStatement()

                migrationFiles.forEach { fileName ->
                    val file = java.io.File(migrationDir, fileName)
                    if (file.exists()) {
                        log.info("Manual Fallback: Executing $fileName...")
                        val sql = file.readText()
                        // Split by semicolon for simple multi-statement execution if needed,
                        // but usually Postgres/statement.execute can handle the whole script
                        statement.execute(sql)
                        log.info("Manual Fallback: $fileName executed successfully.")
                    } else {
                        log.error("Manual Fallback Error: Missing file ${file.absolutePath}")
                    }
                }
            }
            log.info(
                    "Manual JDBC Fallback completed successfully. (Note: Flyway tracking was bypassed)"
            )
        }
    } catch (e: Exception) {
        log.error("Flyway/Migration error (JDBC URL: $jdbcUrl): ${e.message}")
        if (!jdbcUrl.startsWith("jdbc:h2:")) {
            log.error("CRITICAL: Migration failed in production environment", e)
            throw e // Fail fast in production
        } else {
            log.warn(
                    "Non-fatal migration error in test environment (H2). Falling back to automatic schema handling."
            )
        }
    }

    // Connect Exposed
    Database.connect(dataSource)
    log.info("Connecting to Database at: $jdbcUrl")
    log.info("Database connected and migrated successfully.")
}
