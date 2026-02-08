package com.solodev.fleet.modules.accounts.domain.repository

import com.solodev.fleet.modules.accounts.domain.model.Invoice
import java.util.UUID

interface InvoiceRepository {
    suspend fun save(invoice: Invoice): Invoice
    suspend fun findById(id: UUID): Invoice?
    suspend fun findByInvoiceNumber(invoiceNumber: String): Invoice?
}