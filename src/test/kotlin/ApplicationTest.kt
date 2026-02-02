package com.example

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.config.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals

class ApplicationTest {

    private fun ApplicationTestBuilder.configureH2() {
        environment {
            config =
                    MapApplicationConfig(
                            "storage.jdbcUrl" to "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1",
                            "storage.username" to "root",
                            "storage.password" to "",
                            "storage.driverClassName" to "org.h2.Driver",
                            "storage.maximumPoolSize" to "2"
                    )
        }
    }

    @Test
    fun testRoot() = testApplication {
        configureH2()
        application { module() }
        val response = client.get("/health")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("OK", response.bodyAsText())
    }
}
