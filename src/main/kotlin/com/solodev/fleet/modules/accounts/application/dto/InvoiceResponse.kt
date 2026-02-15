package com.solodev.fleet.modules.accounts.application.dto

import com.solodev.fleet.modules.accounts.domain.model.Invoice
import kotlinx.serialization.Serializable

@Serializable
data class InvoiceResponse(
        val id: String,
        val invoiceNumber: String,
        val customerId: String,
        val status: String,
        val total: Int,
        val balance: Int,
        val dueDate: String
) {
        companion object {
                fun fromDomain(invoice: Invoice) =
                        InvoiceResponse(
                                id = invoice.id.toString(),
                                invoiceNumber = invoice.invoiceNumber,
                                customerId = invoice.customerId.value,
                                status = invoice.status.name,
                                total = invoice.totalAmount,
                                balance = invoice.balance,
                                dueDate = invoice.dueDate.toString()
                        )
        }
}
