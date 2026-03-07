package com.solodev.fleet.modules.accounts.domain.model

import com.solodev.fleet.modules.rentals.domain.model.CustomerId
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Test
import kotlin.test.*

class InvoiceTest {

    @Test
    fun `totalAmount is subtotal plus tax`() {
        val invoice = sampleInvoice(subtotal = 10000, tax = 1200)
        assertEquals(11200, invoice.totalAmount)
    }

    @Test
    fun `balance is totalAmount minus paidAmount`() {
        val invoice = sampleInvoice(subtotal = 10000, tax = 1200, paidAmount = 5000)
        assertEquals(6200, invoice.balance)
    }

    @Test
    fun `balance is zero when fully paid`() {
        val invoice = sampleInvoice(subtotal = 10000, tax = 0, paidAmount = 10000)
        assertEquals(0, invoice.balance)
    }

    @Test
    fun `new invoice starts with ISSUED status`() {
        val invoice = sampleInvoice()
        assertEquals(InvoiceStatus.ISSUED, invoice.status)
    }

    @Test
    fun `subtotal cannot be negative`() {
        assertFailsWith<IllegalArgumentException> {
            sampleInvoice(subtotal = -1000)
        }
    }

    @Test
    fun `tax cannot be negative`() {
        assertFailsWith<IllegalArgumentException> {
            sampleInvoice(tax = -100)
        }
    }

    private fun sampleInvoice(
        subtotal: Int = 10000,
        tax: Int = 1200,
        paidAmount: Int = 0
    ) = Invoice(
        id = UUID.randomUUID(),
        invoiceNumber = "INV-2026-001",
        customerId = CustomerId("cust-001"),
        subtotal = subtotal,
        tax = tax,
        paidAmount = paidAmount,
        status = InvoiceStatus.ISSUED,
        issueDate = Instant.now(),
        dueDate = Instant.parse("2026-12-31T23:59:59Z")
    )
}