package com.solodev.fleet.modules.accounts.infrastructure.http

import com.solodev.fleet.modules.accounts.application.dto.*
import com.solodev.fleet.modules.accounts.application.usecases.IssueInvoiceUseCase
import com.solodev.fleet.modules.accounts.application.usecases.PayInvoiceUseCase
import com.solodev.fleet.modules.accounts.domain.model.PaymentMethod
import com.solodev.fleet.modules.accounts.domain.repository.*
import com.solodev.fleet.shared.models.ApiResponse
import com.solodev.fleet.shared.plugins.Idempotency
import com.solodev.fleet.shared.plugins.requestId
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.*

fun Route.accountingRoutes(
    invoiceRepository: InvoiceRepository,
    paymentRepository: PaymentRepository,
    accountRepository: AccountRepository,
    ledgerRepository: LedgerRepository,
    paymentMethodRepository: PaymentMethodRepository
) {
    val issueInvoiceUseCase = IssueInvoiceUseCase(invoiceRepository, accountRepository, ledgerRepository)
    val payInvoiceUseCase =
        PayInvoiceUseCase(
            invoiceRepository,
            paymentRepository,
            accountRepository,
            ledgerRepository,
            paymentMethodRepository
        )

    authenticate("auth-jwt") {
        route("/v1/accounting") {
            // Issue New Invoice
            post("/invoices") {
                try {
                    val request = call.receive<InvoiceRequest>()
                    val invoice = issueInvoiceUseCase.execute(request)
                    call.respond(
                        HttpStatusCode.Created,
                        ApiResponse.success(InvoiceResponse.fromDomain(invoice), call.requestId)
                    )
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.UnprocessableEntity,
                        ApiResponse.error(
                            "INVOICE_ERROR",
                            e.message ?: "Invalid request",
                            call.requestId
                        )
                    )
                }
            }

            install(Idempotency)
            // Pay Invoice (Updated to return a formal Receipt)
            post("/invoices/{id}/pay") {

                val id =
                    call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest)
                val request = call.receive<PaymentRequest>()
                try {
                    val receipt =
                        payInvoiceUseCase.execute(
                            invoiceId = id,
                            amount = request.amount,
                            paymentMethod = request.paymentMethod,
                            notes = request.notes
                        )

                    // Return the data-rich receipt response
                    call.respond(
                        ApiResponse.success(
                            PaymentReceiptResponse.fromDomain(receipt),
                            call.requestId
                        )
                    )
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.Conflict,
                        ApiResponse.error(
                            "PAYMENT_ERROR",
                            e.message ?: "Failed",
                            call.requestId
                        )
                    )
                }
            }

            // List All Payments
            get("/payments") {
                val payments = paymentRepository.findAll()
                call.respond(
                    ApiResponse.success(
                        payments.map { PaymentResponse.fromDomain(it) },
                        call.requestId
                    )
                )
            }

            // List Payments by Customer
            get("/payments/customer/{id}") {
                val customerId =
                    call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                try {
                    val payments = paymentRepository.findByCustomerId(UUID.fromString(customerId))
                    call.respond(
                        ApiResponse.success(
                            payments.map { PaymentResponse.fromDomain(it) },
                            call.requestId
                        )
                    )
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiResponse.error("INVALID_ID", "Invalid Customer ID", call.requestId)
                    )
                }
            }

            // --- Chart of Accounts ---

            // List All Accounts
            get("/accounts") {
                val accounts = accountRepository.findAll()
                val now = java.time.Instant.now()
                val accountResponses =
                    accounts.map { account ->
                        val balance = ledgerRepository.calculateAccountBalance(account.id, now)
                        AccountResponse.fromDomain(account, balance)
                    }
                call.respond(ApiResponse.success(accountResponses, call.requestId))
            }

            // Get Account Balance (General Ledger lookup)
            get("/accounts/{code}/balance") {
                val code =
                    call.parameters["code"]
                        ?: return@get call.respond(HttpStatusCode.BadRequest)
                val account =
                    accountRepository.findByCode(code)
                        ?: return@get call.respond(
                            HttpStatusCode.NotFound,
                            ApiResponse.error(
                                "ACCOUNT_NOT_FOUND",
                                "Account not found",
                                call.requestId
                            )
                        )

                try {
                    val balance =
                        ledgerRepository.calculateAccountBalance(
                            account.id,
                            java.time.Instant.now()
                        )
                    call.respond(
                        ApiResponse.success(
                            AccountBalanceResponse(
                                account = account.accountName,
                                balance = balance / 100.0
                            ),
                            call.requestId
                        )
                    )
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ApiResponse.error(
                            "BALANCE_QUERY_ERROR",
                            e.message ?: "Failed to calculate balance",
                            call.requestId
                        )
                    )
                }
            }

            // --- Payment Methods CRUD ---

            get("/payment-methods") {
                val methods = paymentMethodRepository.findAll()
                call.respond(
                    ApiResponse.success(
                        methods.map { PaymentMethodResponse.fromDomain(it) },
                        call.requestId
                    )
                )
            }

            post("/payment-methods") {
                try {
                    val request = call.receive<PaymentMethodRequest>()
                    val method =
                        PaymentMethod(
                            code = request.code,
                            displayName = request.displayName,
                            targetAccountCode = request.targetAccountCode,
                            isActive = request.isActive,
                            description = request.description
                        )
                    val saved = paymentMethodRepository.save(method)
                    call.respond(
                        HttpStatusCode.Created,
                        ApiResponse.success(PaymentMethodResponse.fromDomain(saved), call.requestId)
                    )
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiResponse.error("BAD_REQUEST", e.message ?: "Invalid request", call.requestId)
                    )
                }
            }

            put("/payment-methods/{id}") {
                val id =
                    call.parameters["id"] ?: return@put call.respond(HttpStatusCode.BadRequest)
                try {
                    val request = call.receive<PaymentMethodRequest>()
                    val existing =
                        paymentMethodRepository.findById(UUID.fromString(id))
                            ?: return@put call.respond(HttpStatusCode.NotFound)
                    val updated =
                        existing.copy(
                            code = request.code,
                            displayName = request.displayName,
                            targetAccountCode = request.targetAccountCode,
                            isActive = request.isActive,
                            description = request.description,
                            updatedAt = java.time.Instant.now()
                        )
                    val saved = paymentMethodRepository.save(updated)
                    call.respond(ApiResponse.success(PaymentMethodResponse.fromDomain(saved), call.requestId))
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiResponse.error("BAD_REQUEST", e.message ?: "Invalid request", call.requestId)
                    )
                }
            }

            delete("/payment-methods/{id}") {
                val id =
                    call.parameters["id"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
                val deleted = paymentMethodRepository.delete(UUID.fromString(id))
                if (deleted) {
                    call.respond(ApiResponse.success(mapOf("deleted" to true), call.requestId))
                } else {
                    call.respond(HttpStatusCode.NotFound)
                }
            }

            // --- Payment CRUD (Delete) ---

            delete("/payments/{id}") {
                val id =
                    call.parameters["id"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
                try {
                    val deleted = paymentRepository.delete(UUID.fromString(id))
                    if (deleted) {
                        call.respond(ApiResponse.success(mapOf("deleted" to true), call.requestId))
                    } else {
                        call.respond(HttpStatusCode.NotFound)
                    }
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ApiResponse.error("DELETE_FAILED", e.message ?: "Failed to delete payment", call.requestId)
                    )
                }
            }
        }
    }
}
