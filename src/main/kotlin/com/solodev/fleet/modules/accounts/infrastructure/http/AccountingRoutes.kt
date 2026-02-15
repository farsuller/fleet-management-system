package com.solodev.fleet.modules.accounts.infrastructure.http

import com.solodev.fleet.modules.accounts.application.ReconciliationService
import com.solodev.fleet.modules.accounts.application.dto.*
import com.solodev.fleet.modules.accounts.application.usecases.GenerateFinancialReportsUseCase
import com.solodev.fleet.modules.accounts.application.usecases.IssueInvoiceUseCase
import com.solodev.fleet.modules.accounts.application.usecases.ManageAccountUseCase
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
import java.time.Instant
import java.util.*

fun Route.accountingRoutes(
    invoiceRepository: InvoiceRepository,
    paymentRepository: PaymentRepository,
    accountRepository: AccountRepository,
    ledgerRepository: LedgerRepository,
    paymentMethodRepository: PaymentMethodRepository,
    reconciliationService: ReconciliationService
) {
    val issueInvoiceUseCase =
        IssueInvoiceUseCase(
            invoiceRepo = invoiceRepository,
            accountRepo = accountRepository,
            ledgerRepo = ledgerRepository
        )

    val payInvoiceUseCase =
        PayInvoiceUseCase(
            invoiceRepo = invoiceRepository,
            paymentRepo = paymentRepository,
            accountRepo = accountRepository,
            ledgerRepo = ledgerRepository,
            paymentMethodRepo = paymentMethodRepository
        )

    val manageAccountUseCase = ManageAccountUseCase(accountRepository = accountRepository)
    val reportsUseCase = GenerateFinancialReportsUseCase(
            accountRepo = accountRepository,
            ledgerRepo = ledgerRepository
    )

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
            val id = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest)
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
                    ApiResponse.error("PAYMENT_ERROR", e.message ?: "Failed", call.requestId)
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
            val code = call.parameters["code"] ?: return@get call.respond(HttpStatusCode.BadRequest)
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
                            balance = balance
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
                    ApiResponse.error(
                        "BAD_REQUEST",
                        e.message ?: "Invalid request",
                        call.requestId
                    )
                )
            }
        }

        put("/payment-methods/{id}") {
            val id = call.parameters["id"] ?: return@put call.respond(HttpStatusCode.BadRequest)
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
                call.respond(
                    ApiResponse.success(PaymentMethodResponse.fromDomain(saved), call.requestId)
                )
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ApiResponse.error(
                        "BAD_REQUEST",
                        e.message ?: "Invalid request",
                        call.requestId
                    )
                )
            }
        }

        delete("/payment-methods/{id}") {
            val id = call.parameters["id"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
            val deleted = paymentMethodRepository.delete(UUID.fromString(id))
            if (deleted) {
                call.respond(ApiResponse.success(mapOf("deleted" to true), call.requestId))
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        }

        // --- Payment CRUD (Delete) ---

        delete("/payments/{id}") {
            val id = call.parameters["id"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
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
                    ApiResponse.error(
                        "DELETE_FAILED",
                        e.message ?: "Failed to delete payment",
                        call.requestId
                    )
                )
            }
        }
    }

    route("/v1/accounts") {
        post {
            // Create a new account
            val request = call.receive<AccountRequest>()
            val account = manageAccountUseCase.create(request)
            call.respond(
                HttpStatusCode.Created,
                ApiResponse.success(AccountResponse.fromDomain(account, 0), call.requestId)
            )
        }

        put("/{id}") {
            // Update account details
            val id = call.parameters["id"] ?: return@put call.respond(HttpStatusCode.BadRequest)
            val request = call.receive<AccountRequest>()
            val account = manageAccountUseCase.update(id, request)
            call.respond(
                ApiResponse.success(AccountResponse.fromDomain(account, 0), call.requestId)
            )
        }

        delete("/{id}") {
            // Delete an account
            val id = call.parameters["id"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
            if (manageAccountUseCase.delete(id)) {
                call.respond(ApiResponse.success(mapOf("deleted" to true), call.requestId))
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        }
    }

    route("/v1/reports") {
        get("/revenue") {
            // Fetch revenue data for a date range
            val start = call.parameters["startDate"]?.let { Instant.parse(it) } ?: Instant.MIN
            val end = call.parameters["endDate"]?.let { Instant.parse(it) } ?: Instant.now()
            val report = reportsUseCase.revenueReport(start, end)
            call.respond(ApiResponse.success(report, call.requestId))
        }

        get("/balance-sheet") {
            // Fetch current financial position
            val asOf = call.parameters["asOf"]?.let { Instant.parse(it) } ?: Instant.now()
            val report = reportsUseCase.balanceSheet(asOf)
            call.respond(ApiResponse.success(report, call.requestId))
        }
    }

    route("/v1/reconciliation") {
        get("/invoices") {
            val mismatches = reconciliationService.verifyInvoices()
            call.respond(ApiResponse.success(mismatches, call.requestId))
        }

        get("/integrity") {
            val isBalanced = reconciliationService.verifyAccountingEquation()
            call.respond(ApiResponse.success(mapOf("isBalanced" to isBalanced), call.requestId))
        }
    }
}
