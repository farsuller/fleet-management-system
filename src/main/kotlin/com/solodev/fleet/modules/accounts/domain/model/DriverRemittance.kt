package com.solodev.fleet.modules.accounts.domain.model

import java.time.Instant
import java.util.UUID

/** Lifecycle state of a driver remittance. */
enum class RemittanceStatus {
    PENDING,      // created but driver has not physically handed over funds yet
    SUBMITTED,    // driver submitted; awaiting back-office verification
    VERIFIED,     // back-office verified amounts match; GL entries posted
    DISCREPANCY   // submitted amount differs from recorded collections; requires manual review
}

/**
 * Driver remittance aggregate.
 *
 * Represents the batch hand-over event where a driver physically delivers
 * collected customer payments to the back-office. Clearing a remittance
 * triggers GL postings for all associated payments.
 */
data class DriverRemittance(
    val id: UUID,
    val remittanceNumber: String,
    val driverId: UUID,
    val remittanceDate: Instant,
    val totalAmount: Int,
    val status: RemittanceStatus,
    val paymentIds: List<UUID>,
    val notes: String?
) {
    init {
        require(remittanceNumber.isNotBlank()) { "Remittance number cannot be blank" }
        require(totalAmount > 0) { "Total amount must be positive" }
        require(paymentIds.isNotEmpty()) { "Remittance must reference at least one payment" }
    }
}
