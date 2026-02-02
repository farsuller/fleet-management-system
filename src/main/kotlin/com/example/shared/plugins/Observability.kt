package com.example.shared.plugins

import io.ktor.server.application.*
import io.ktor.server.metrics.micrometer.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.request.*
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import org.slf4j.event.Level

/**
 * Configures application observability features.
 *
 * - Sets up **CallLogging** to log HTTP requests (filtered for valid paths).
 * - Initializes **Micrometer** with a Prometheus registry for metrics collection.
 */
fun Application.configureObservability() {
    install(CallLogging) {
        level = Level.INFO
        filter { call -> call.request.path().startsWith("/") }
    }

    val appMicrometerRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    install(MicrometerMetrics) {
        registry = appMicrometerRegistry
        // distinct metrics by path, method, and response status
        meterBinders = emptyList()
    }

    // In a real app we'd expose /metrics endpoint, but restricting it to internal ops usually.
    // implementation details for routing /metrics can be added here or in Routing.kt
}
