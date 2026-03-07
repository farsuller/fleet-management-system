package com.solodev.fleet.modules.tracking.infrastructure.metrics

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import java.util.concurrent.atomic.AtomicInteger

/**
 * Tracks key performance metrics for spatial matching and broadcasting.
 */
class SpatialMetrics(private val registry: MeterRegistry) {
    private val snapTimer = Timer.builder("postgis.snap.duration")
        .description("Time taken to snap GPS coordinates to route (milliseconds)")
        .tag("operation", "snap_to_route")
        .register(registry)
    
    private val snapErrors = registry.counter(
        "postgis.snap.errors",
        "type", "snap_failure"
    )
    
    private val offRouteCounter = registry.counter(
        "postgis.vehicle.off_route",
        "reason", "distance_exceeded"
    )
    
    private val broadcastCounter = registry.counter(
        "tracking.delta.broadcasts",
        "type", "websocket"
    )
    
    private val activeSessions = registry.gauge(
        "tracking.websocket.active_sessions",
        AtomicInteger(0)
    ) { it.get().toDouble() }
    
    private val deltaEfficiency = registry.gauge(
        "tracking.delta.efficiency_percent",
        AtomicInteger(0)
    ) { (it.get() / 100.0) }

    fun recordSnapDuration(durationMs: Long) {
        snapTimer.record(durationMs, java.util.concurrent.TimeUnit.MILLISECONDS)
    }
    
    fun recordSnapError() {
        snapErrors.increment()
    }
    
    fun recordOffRoute() {
        offRouteCounter.increment()
    }
    
    fun recordBroadcast() {
        broadcastCounter.increment()
    }
    
    fun setActiveSessionCount(count: Int) {
        // Update gauge
    }
    
    fun setDeltaEfficiencyPercent(percent: Int) {
        // Update gauge (% of pings that resulted in broadcasts)
    }
}