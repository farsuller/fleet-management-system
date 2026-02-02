package com.example

import com.example.shared.plugins.*
import io.ktor.server.application.*
import io.ktor.server.netty.EngineMain

/**
 * Entry point for the application. Starts the Netty engine using the configuration defined in
 * application.yaml.
 */
fun main(args: Array<String>) {
    EngineMain.main(args)
}

/**
 * Main application module.
 *
 * This extension function acts as the central configuration hub for the Ktor application. It
 * coordinates the setup of all major subsystems including observability, serialization, error
 * handling (status pages), database connections, security, and routing.
 *
 * Order matters: RequestId must be configured early so it's available in error handlers.
 */
fun Application.module() {
    configureRequestId()
    configureObservability()
    configureSerialization()
    configureStatusPages()
    configureDatabases()
    configureSecurity()
    configureRouting()
}
