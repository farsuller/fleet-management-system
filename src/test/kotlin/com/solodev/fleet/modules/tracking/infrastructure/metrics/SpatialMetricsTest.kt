package com.solodev.fleet.modules.tracking.infrastructure.metrics

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SpatialMetricsTest {
    @Test
    fun `should record snap duration metric`() {
        val registry = SimpleMeterRegistry()
        val metrics = SpatialMetrics(registry)

        metrics.recordSnapDuration(50)
        metrics.recordSnapDuration(75)
        metrics.recordSnapDuration(100)

        val timer = registry.find("postgis.snap.duration").timer()
        assertNotNull(timer)
        assertEquals(3, timer.count())
        assertEquals(225.0, timer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS))
    }

    @Test
    fun `should increment snap error counter`() {
        val registry = SimpleMeterRegistry()
        val metrics = SpatialMetrics(registry)

        metrics.recordSnapError()
        metrics.recordSnapError()

        val counter = registry.find("postgis.snap.errors").counter()
        assertNotNull(counter)
        assertEquals(2.0, counter.count())
    }

    @Test
    fun `should track off-route events`() {
        val registry = SimpleMeterRegistry()
        val metrics = SpatialMetrics(registry)

        metrics.recordOffRoute()
        metrics.recordOffRoute()
        metrics.recordOffRoute()

        val counter = registry.find("postgis.vehicle.off_route").counter()
        assertNotNull(counter)
        assertEquals(3.0, counter.count())
    }

    @Test
    fun `should count broadcast events`() {
        val registry = SimpleMeterRegistry()
        val metrics = SpatialMetrics(registry)

        metrics.recordBroadcast()

        val counter = registry.find("tracking.delta.broadcasts").counter()
        assertNotNull(counter)
        assertEquals(1.0, counter.count())
    }
}

