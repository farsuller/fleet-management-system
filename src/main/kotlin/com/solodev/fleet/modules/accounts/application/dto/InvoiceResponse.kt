package com.solodev.fleet.modules.accounts.application.dto

import com.solodev.fleet.modules.accounts.domain.model.Invoice
import kotlinx.serialization.Serializable

@Serializable
data class InvoiceResponse(
        val id: String,
        val invoiceNumber: String,
        val customerId: String,
        val status: String,
        val total: Double,
        val balance: Double,
        val dueDate: String
) {
    companion object {
        fun fromDomain(invoice: Invoice) =
                InvoiceResponse(
                        id = invoice.id.toString(),
                        invoiceNumber = invoice.invoiceNumber,
                        customerId = invoice.customerId.value,
                        status = invoice.status.name,
                        total = invoice.totalCents / 100.0,
                        balance = invoice.balanceCents / 100.0,
                        dueDate = invoice.dueDate.toString()
                )
    }
}
