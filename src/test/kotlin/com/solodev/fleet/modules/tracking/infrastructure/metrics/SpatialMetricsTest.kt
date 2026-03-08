package com.solodev.fleet.modules.tracking.infrastructure.metrics

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SpatialMetricsTest {

    @Test
    fun shouldRecordSnapDurationMetric_WhenRecordSnapDurationCalled() {
        // Arrange
        val registry = SimpleMeterRegistry()
        val metrics = SpatialMetrics(registry)

        // Act
        metrics.recordSnapDuration(50)
        metrics.recordSnapDuration(75)
        metrics.recordSnapDuration(100)

        // Assert
        val timer = registry.find("postgis.snap.duration").timer()
        assertThat(timer).isNotNull()
        assertThat(timer!!.count()).isEqualTo(3L)
        assertThat(timer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS)).isEqualTo(225.0)
    }

    @Test
    fun shouldIncrementSnapErrorCounter_WhenRecordSnapErrorCalled() {
        // Arrange
        val registry = SimpleMeterRegistry()
        val metrics = SpatialMetrics(registry)

        // Act
        metrics.recordSnapError()
        metrics.recordSnapError()

        // Assert
        val counter = registry.find("postgis.snap.errors").counter()
        assertThat(counter).isNotNull()
        assertThat(counter!!.count()).isEqualTo(2.0)
    }

    @Test
    fun shouldTrackOffRouteEvents_WhenRecordOffRouteCalled() {
        // Arrange
        val registry = SimpleMeterRegistry()
        val metrics = SpatialMetrics(registry)

        // Act
        metrics.recordOffRoute()
        metrics.recordOffRoute()
        metrics.recordOffRoute()

        // Assert
        val counter = registry.find("postgis.vehicle.off_route").counter()
        assertThat(counter).isNotNull()
        assertThat(counter!!.count()).isEqualTo(3.0)
    }

    @Test
    fun shouldCountBroadcastEvents_WhenRecordBroadcastCalled() {
        // Arrange
        val registry = SimpleMeterRegistry()
        val metrics = SpatialMetrics(registry)

        // Act
        metrics.recordBroadcast()

        // Assert
        val counter = registry.find("tracking.delta.broadcasts").counter()
        assertThat(counter).isNotNull()
        assertThat(counter!!.count()).isEqualTo(1.0)
    }
}

