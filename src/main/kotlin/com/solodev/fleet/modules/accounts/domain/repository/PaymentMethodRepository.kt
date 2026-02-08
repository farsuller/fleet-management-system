package com.solodev.fleet.modules.accounts.domain.repository

import com.solodev.fleet.modules.accounts.domain.model.PaymentMethod
import java.util.UUID

interface PaymentMethodRepository {
    suspend fun findById(id: UUID): PaymentMethod?
    suspend fun findByCode(code: String): PaymentMethod?
    suspend fun findAll(): List<PaymentMethod>
    suspend fun findAllActive(): List<PaymentMethod>
    suspend fun save(paymentMethod: PaymentMethod): PaymentMethod
    suspend fun delete(id: UUID): Boolean
}
