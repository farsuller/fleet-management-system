package com.solodev.fleet

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.config.*
import io.ktor.server.testing.*
import kotlin.test.*

class MigrationTest {
    private fun ApplicationTestBuilder.configureH2() {
        environment {
            config =
                    MapApplicationConfig(
                            "storage.jdbcUrl" to
                                    "jdbc:h2:mem:test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                            "storage.username" to "sa",
                            "storage.password" to "",
                            "storage.driverClassName" to "org.h2.Driver",
                            "storage.maximumPoolSize" to "2",
                            "jwt.secret" to
                                    "test-secret-at-least-64-bytes-long-for-hmac-sha256-security-1234567890",
                            "jwt.issuer" to "test-issuer",
                            "jwt.audience" to "test-audience",
                            "jwt.realm" to "test-realm",
                            "jwt.expiresIn" to "3600000"
                    )
        }
    }

    @Test
    fun testMigrationsRun() = testApplication {
        configureH2()
        application { module() }

        // If the application starts, migrations ran successfully
        val response = client.get("/health")
        assertEquals(HttpStatusCode.OK, response.status)
    }
}
