package com.solodev.fleet

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.config.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApplicationTest {

    private fun ApplicationTestBuilder.configureH2() {
        environment {
            config =
                    MapApplicationConfig(
                            "storage.jdbcUrl" to "jdbc:postgresql://127.0.0.1:5435/fleet_test",
                            "storage.username" to "fleet_user",
                            "storage.password" to "secret_123",
                            "storage.driverClassName" to "org.postgresql.Driver",
                            "storage.maximumPoolSize" to "2"
                    )
        }
    }

    @Test
    fun testHealthEndpoint() = testApplication {
        configureH2()
        application { module() }
        val response = client.get("/health")
        assertEquals(HttpStatusCode.OK, response.status)
        // Now returns JSON response envelope
        val body = response.bodyAsText()
        assertTrue(body.contains("\"success\"") && body.contains("true"))
        assertTrue(body.contains("\"status\"") && body.contains("\"OK\""))
    }

    @Test
    fun testRootEndpoint() = testApplication {
        configureH2()
        application { module() }
        val response = client.get("/")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("\"success\"") && body.contains("true"))
        assertTrue(body.contains("Fleet Management API v1"))
    }
}
