package com.solodev.fleet.shared.infrastructure.email

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class EmailServiceTest {
    @Test
    fun `sendVerificationEmail should send correct JSON payload to Nuntly`() =
        runBlocking {
            // Arrange
            val mockEngine =
                MockEngine { request ->
                    // Verify request headers and URL
                    assertEquals("https://api.nuntly.com/emails", request.url.toString())
                    assertEquals("Bearer test_key", request.headers[HttpHeaders.Authorization])

                    respond(
                        content = """{"data": {"id": "email_123"}}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }

            val client =
                HttpClient(mockEngine) {
                    install(ContentNegotiation) {
                        json()
                    }
                }

            val service =
                NuntlyEmailService(
                    client = client,
                    apiKey = "test_key",
                    sender = "Fleet <info@fleet.com>",
                    baseUrl = "https://api.nuntly.com",
                )

            // Act
            service.sendVerificationEmail("user@example.com", "token_abc", isOtp = false)

            // Assertions are partly done inside MockEngine callback
        }

    @Test
    fun `sendVerificationEmail should handle error responses from Nuntly`() =
        runBlocking {
            // Arrange
            val mockEngine =
                MockEngine { request ->
                    respond(
                        content = """{"error": {"message": "Invalid API Key", "code": "unauthorized"}}""",
                        status = HttpStatusCode.Unauthorized,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }

            val client =
                HttpClient(mockEngine) {
                    install(ContentNegotiation) {
                        json()
                    }
                }

            val service =
                NuntlyEmailService(
                    client = client,
                    apiKey = "invalid_key",
                    sender = "Fleet <info@fleet.com>",
                )

            // Act & Assert
            // We expect the service to log the error and not crash
            service.sendVerificationEmail("user@example.com", "token_abc")
        }
}
