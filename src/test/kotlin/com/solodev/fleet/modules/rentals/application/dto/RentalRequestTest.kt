package com.solodev.fleet.modules.rentals.application.dto

import org.junit.jupiter.api.Test
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import java.time.format.DateTimeParseException

class RentalRequestTest {

    @Test
    fun shouldCreateRequest_WhenAllFieldsValid() {
        val request = RentalRequest(
            vehicleId = "veh-123",
            customerId = "cust-456",
            startDate = "2026-03-24T10:00:00Z",
            endDate = "2026-03-25T10:00:00Z",
            dailyRateAmount = 1500L
        )

        assertThat(request.vehicleId).isEqualTo("veh-123")
        assertThat(request.dailyRateAmount).isEqualTo(1500L)
    }

    @Test
    fun shouldThrowException_WhenDatesAreInvalidFormat() {
        assertThatThrownBy {
            RentalRequest(
                vehicleId = "veh-123",
                customerId = "cust-456",
                startDate = "2026-03-24", // Not ISO-8601 with time
                endDate = "2026-03-25T10:00:00Z"
            )
        }.isInstanceOf(DateTimeParseException::class.java)
    }

    @Test
    fun shouldThrowException_WhenEndDateIsBeforeStartDate() {
        assertThatThrownBy {
            RentalRequest(
                vehicleId = "veh-123",
                customerId = "cust-456",
                startDate = "2026-03-25T10:00:00Z",
                endDate = "2026-03-24T10:00:00Z"
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("End date must be after start date")
    }

    @Test
    fun shouldThrowException_WhenFieldsAreBlank() {
        assertThatThrownBy {
            RentalRequest(
                vehicleId = "",
                customerId = "cust-456",
                startDate = "2026-03-24T10:00:00Z",
                endDate = "2026-03-25T10:00:00Z"
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Vehicle ID cannot be blank")
    }
}
