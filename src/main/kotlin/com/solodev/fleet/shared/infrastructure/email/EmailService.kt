package com.solodev.fleet.shared.infrastructure.email

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import org.slf4j.LoggerFactory

interface EmailService {
    suspend fun sendVerificationEmail(
        email: String,
        token: String,
        isOtp: Boolean = false,
    )
}

class NuntlyEmailService(
    private val client: HttpClient,
    private val apiKey: String,
    private val sender: String,
    private val baseUrl: String = "https://api.nuntly.com",
) : EmailService {
    private val logger = LoggerFactory.getLogger(NuntlyEmailService::class.java)

    override suspend fun sendVerificationEmail(
        email: String,
        token: String,
        isOtp: Boolean,
    ) {
        val subject = if (isOtp) "Your Verification Code" else "Verify your Fleet Drive account"
        val htmlContent = if (isOtp) generateOtpTemplate(token) else generateLinkTemplate(token)

        try {
            val sanitizedKey = apiKey.trim()
            if (sanitizedKey != apiKey) {
                logger.warn("API Key had leading/trailing whitespace and was trimmed.")
            }
            logger.info("Sending email via Nuntly. Key length: ${sanitizedKey.length}, Prefix: ${sanitizedKey.take(5)}, Suffix: ${sanitizedKey.takeLast(4)}")
            
            val response: NuntlyEmailResponse =
                client
                    .post("$baseUrl/emails") {
                        header(HttpHeaders.Authorization, "Bearer $sanitizedKey")
                        // Fallback header used by some Nuntly versions
                        header("X-API-Key", sanitizedKey)
                        contentType(ContentType.Application.Json)
                        setBody(
                            NuntlyEmailRequest(
                                from = sender,
                                to = email,
                                subject = subject,
                                html = htmlContent,
                            ),
                        )
                    }.body()

            if (response.error != null) {
                val errorMsg = response.error.message ?: response.error.title ?: "Unknown error"
                logger.error("Failed to send email to $email: $errorMsg (Code: ${response.error.code}, Status: ${response.error.status})")
            } else {
                logger.info("Successfully sent email to $email. ID: ${response.data?.id}")
            }
        } catch (e: Exception) {
            logger.error("Error calling Nuntly API for user $email", e)
        }
    }

    private fun generateLinkTemplate(token: String): String {
        // Use the configured backend base URL for verification
        val link = "$baseUrl/v1/auth/verify?token=$token"
        return """
            <html>
                <body style="font-family: sans-serif; background-color: #f4f4f4; padding: 40px; text-align: center;">
                    <div style="max-width: 500px; margin: auto; background: white; padding: 30px; border-radius: 12px; box-shadow: 0 4px 6px rgba(0,0,0,0.1);">
                        <h2 style="color: #FFA824;">Welcome to Fleet Drive</h2>
                        <p style="color: #666; font-size: 16px;">Please verify your email address to activate your account.</p>
                        <a href="$link" style="display: inline-block; padding: 14px 28px; background-color: #FFA824; color: #161616; text-decoration: none; border-radius: 26px; font-weight: bold; margin-top: 20px;">Verify Account</a>
                        <p style="margin-top: 30px; font-size: 12px; color: #999;">If you didn't request this, you can safely ignore this email.</p>
                    </div>
                </body>
            </html>
            """.trimIndent()
    }

    private fun generateOtpTemplate(otp: String): String =
        """
        <html>
            <body style="font-family: sans-serif; background-color: #f4f4f4; padding: 40px; text-align: center;">
                <div style="max-width: 500px; margin: auto; background: white; padding: 30px; border-radius: 12px; box-shadow: 0 4px 6px rgba(0,0,0,0.1);">
                    <h2 style="color: #FFA824;">Verification Code</h2>
                    <p style="color: #666; font-size: 16px;">Use the code below to complete your verification:</p>
                    <div style="font-size: 42px; font-weight: bold; letter-spacing: 5px; color: #161616; margin: 20px 0;">$otp</div>
                    <p style="color: #999; font-size: 14px;">This code is valid for 5 minutes.</p>
                </div>
            </body>
        </html>
        """.trimIndent()
}
