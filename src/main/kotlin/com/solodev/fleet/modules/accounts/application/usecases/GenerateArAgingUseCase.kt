package com.solodev.fleet.modules.accounts.application.usecases

import com.solodev.fleet.modules.accounts.application.dto.ArAgingResponse
import com.solodev.fleet.modules.accounts.application.dto.ArAgingRow
import com.solodev.fleet.modules.accounts.domain.model.InvoiceStatus
import com.solodev.fleet.modules.accounts.domain.repository.InvoiceRepository
import com.solodev.fleet.modules.rentals.domain.repository.CustomerRepository
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Generates an Accounts Receivable Aging report as of a given date.
 *
 * Open invoices (ISSUED or OVERDUE with balance > 0) are grouped by customer and bucketed by
 * how many days past their due date they are:
 *   - 0–30  days (includes not-yet-due)
 *   - 31–60 days
 *   - 61–90 days
 *   - 91+   days
 */
class GenerateArAgingUseCase(
    private val invoiceRepo: InvoiceRepository,
    private val customerRepo: CustomerRepository,
) {
    suspend fun arAging(asOf: Instant): ArAgingResponse {
        val openStatuses = setOf(InvoiceStatus.ISSUED, InvoiceStatus.OVERDUE)
        val openInvoices = invoiceRepo.findAll().filter { it.status in openStatuses && it.balance > 0 }

        val byCustomer = openInvoices.groupBy { it.customerId }

        val rows =
            byCustomer
                .map { (customerId, invoices) ->
                    val customer = customerRepo.findById(customerId)
                    var b0to30 = 0L
                    var b31to60 = 0L
                    var b61to90 = 0L
                    var b91plus = 0L

                    invoices.forEach { inv ->
                        val balance = inv.balance.toLong()
                        val daysOverdue = ChronoUnit.DAYS.between(inv.dueDate, asOf).coerceAtLeast(0L)
                        when {
                            daysOverdue <= 30 -> b0to30 += balance
                            daysOverdue <= 60 -> b31to60 += balance
                            daysOverdue <= 90 -> b61to90 += balance
                            else -> b91plus += balance
                        }
                    }

                    ArAgingRow(
                        customerId = customerId.value,
                        customerName = customer?.fullName ?: "Unknown",
                        bucket0to30 = b0to30,
                        bucket31to60 = b31to60,
                        bucket61to90 = b61to90,
                        bucket91plus = b91plus,
                        total = b0to30 + b31to60 + b61to90 + b91plus,
                    )
                }.sortedByDescending { it.total }

        return ArAgingResponse(
            asOfDate = asOf.toString(),
            rows = rows,
            totalBucket0to30 = rows.sumOf { it.bucket0to30 },
            totalBucket31to60 = rows.sumOf { it.bucket31to60 },
            totalBucket61to90 = rows.sumOf { it.bucket61to90 },
            totalBucket91plus = rows.sumOf { it.bucket91plus },
            grandTotal = rows.sumOf { it.total },
        )
    }
}
