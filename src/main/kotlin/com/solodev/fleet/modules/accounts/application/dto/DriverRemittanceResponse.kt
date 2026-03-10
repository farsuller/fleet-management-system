package com.solodev.fleet.modules.accounts.application.dto

import com.solodev.fleet.modules.accounts.domain.model.DriverRemittance
import kotlinx.serialization.Serializable

@Serializable
data class DriverRemittanceResponse(
    val id: String,
    val remittanceNumber: String,
    val driverId: String,
    val remittanceDate: String,
    val totalAmount: Int,
    val status: String,
    val paymentIds: List<String>,
    val notes: String?
) {
    companion object {
        fun fromDomain(remittance: DriverRemittance) = DriverRemittanceResponse(
            id = remittance.id.toString(),
            remittanceNumber = remittance.remittanceNumber,
            driverId = remittance.driverId.toString(),
            remittanceDate = remittance.remittanceDate.toString(),
            totalAmount = remittance.totalAmount,
            status = remittance.status.name,
            paymentIds = remittance.paymentIds.map { it.toString() },
            notes = remittance.notes
        )
    }
}
