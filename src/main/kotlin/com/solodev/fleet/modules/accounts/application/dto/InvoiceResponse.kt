package com.solodev.fleet.modules.accounts.application.dto

import com.solodev.fleet.modules.accounts.domain.model.Invoice
import com.solodev.fleet.modules.rentals.domain.model.Customer
import kotlinx.serialization.Serializable

/** Lightweight customer snapshot embedded in invoice responses. */
@Serializable
data class CustomerSummary(
        val id: String,
        val fullName: String,
        val email: String,
        val phoneNumber: String?
)

@Serializable
data class InvoiceResponse(
        val id: String,
        val invoiceNumber: String,
        val customer: CustomerSummary?,
        val rentalId: String?,
        val status: String,
        val subtotal: Int,
        val tax: Int,
        val total: Int,
        val paidAmount: Int,
        val balance: Int,
        val currencyCode: String,
        val issueDate: String,
        val dueDate: String,
        val paidDate: String?,
        val notes: String?
) {
        companion object {
                fun fromDomain(invoice: Invoice, customer: Customer? = null) =
                        InvoiceResponse(
                                id = invoice.id.toString(),
                                invoiceNumber = invoice.invoiceNumber,
                                customer = customer?.let {
                                        CustomerSummary(
                                                id = it.id.value,
                                                fullName = it.fullName,
                                                email = it.email,
                                                phoneNumber = it.phone
                                        )
                                },
                                rentalId = invoice.rentalId?.value,
                                status = invoice.status.name,
                                subtotal = invoice.subtotal,
                                tax = invoice.tax,
                                total = invoice.totalAmount,
                                paidAmount = invoice.paidAmount,
                                balance = invoice.balance,
                                currencyCode = invoice.currencyCode,
                                issueDate = invoice.issueDate.toString(),
                                dueDate = invoice.dueDate.toString(),
                                paidDate = invoice.paidDate?.toString(),
                                notes = invoice.notes
                        )
        }
}
