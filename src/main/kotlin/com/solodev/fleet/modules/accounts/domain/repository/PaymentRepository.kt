package com.solodev.fleet.modules.accounts.domain.repository

import com.solodev.fleet.modules.accounts.domain.model.Payment
import java.util.UUID

interface PaymentRepository {
    suspend fun save(payment: Payment): Payment
    suspend fun findById(id: UUID): Payment?
    suspend fun findByInvoiceId(invoiceId: UUID): List<Payment>
    suspend fun findByCustomerId(customerId: UUID): List<Payment>
    suspend fun findAll(): List<Payment>
    suspend fun delete(id: UUID): Boolean
}
