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
                            "storage.jdbcUrl" to
                                    "jdbc:h2:mem:test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                            "storage.username" to "sa",
                            "storage.password" to "",
                            "storage.driverClassName" to "org.h2.Driver",
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
