package com.solodev.fleet.modules.accounts.infrastructure.http

import com.solodev.fleet.modules.accounts.application.ReconciliationService
import com.solodev.fleet.modules.accounts.application.dto.*
import com.solodev.fleet.modules.accounts.application.usecases.*
import com.solodev.fleet.modules.accounts.domain.model.PaymentMethod
import com.solodev.fleet.modules.accounts.domain.repository.*
import com.solodev.fleet.modules.rentals.domain.model.CustomerId
import com.solodev.fleet.modules.rentals.domain.repository.CustomerRepository
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
    customerRepository: CustomerRepository,
    remittanceRepository: DriverRemittanceRepository,
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

    val recordDriverCollectionUseCase = RecordDriverCollectionUseCase(
        invoiceRepository = invoiceRepository,
        paymentRepository = paymentRepository
    )

    val recordDriverRemittanceUseCase = RecordDriverRemittanceUseCase(
        paymentRepository = paymentRepository,
        invoiceRepository = invoiceRepository,
        accountRepository = accountRepository,
        ledgerRepository = ledgerRepository,
        paymentMethodRepository = paymentMethodRepository,
        remittanceRepository = remittanceRepository
    )

    val manageAccountUseCase = ManageAccountUseCase(accountRepository = accountRepository)
    val reportsUseCase = GenerateFinancialReportsUseCase(
            accountRepo = accountRepository,
            ledgerRepo = ledgerRepository
    )

    /** Helper: load customer snapshot for a given UUID string; returns null if not found. */
    suspend fun customerSummaryFor(customerIdValue: String) =
        customerRepository.findById(CustomerId(customerIdValue))

    route("/v1/accounting") {
        // List all invoices (with customer snapshot)
        get("/invoices") {
            val invoices = invoiceRepository.findAll()
            val responses = invoices.map { invoice ->
                val customer = customerSummaryFor(invoice.customerId.value)
                InvoiceResponse.fromDomain(invoice, customer)
            }
            call.respond(ApiResponse.success(responses, call.requestId))
        }

        // List invoices for a specific customer
        get("/invoices/customer/{customerId}") {
            val customerId = call.parameters["customerId"]
                ?: return@get call.respond(HttpStatusCode.BadRequest)
            try {
                val customer = customerRepository.findById(CustomerId(customerId))
                val invoices = invoiceRepository.findByCustomerId(UUID.fromString(customerId))
                val responses = invoices.map { InvoiceResponse.fromDomain(it, customer) }
                call.respond(ApiResponse.success(responses, call.requestId))
            } catch (e: IllegalArgumentException) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ApiResponse.error("INVALID_ID", "Invalid Customer ID", call.requestId)
                )
            }
        }

        // Issue New Invoice
        post("/invoices") {
            try {
                val request = call.receive<InvoiceRequest>()
                val invoice = issueInvoiceUseCase.execute(request)
                val customer = customerSummaryFor(invoice.customerId.value)
                call.respond(
                    HttpStatusCode.Created,
                    ApiResponse.success(InvoiceResponse.fromDomain(invoice, customer), call.requestId)
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
        // Pay Invoice (returns a formal Receipt)
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

        // Record a driver-collected payment (field collection; PENDING until remitted)
        post("/payments/driver-collection") {
            try {
                val request = call.receive<DriverCollectionRequest>()
                val payment = recordDriverCollectionUseCase.execute(request)
                call.respond(
                    HttpStatusCode.Created,
                    ApiResponse.success(PaymentResponse.fromDomain(payment), call.requestId)
                )
            } catch (e: IllegalArgumentException) {
                call.respond(
                    HttpStatusCode.UnprocessableEntity,
                    ApiResponse.error("COLLECTION_ERROR", e.message ?: "Invalid request", call.requestId)
                )
            }
        }

        // List PENDING collections for a driver (not yet remitted)
        get("/payments/driver/{driverId}/pending") {
            val driverId = call.parameters["driverId"]
                ?: return@get call.respond(HttpStatusCode.BadRequest)
            try {
                val payments = paymentRepository.findPendingByDriverId(UUID.fromString(driverId))
                call.respond(
                    ApiResponse.success(
                        payments.map { PaymentResponse.fromDomain(it) },
                        call.requestId
                    )
                )
            } catch (e: IllegalArgumentException) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ApiResponse.error("INVALID_ID", "Invalid Driver ID", call.requestId)
                )
            }
        }

        // List all payments (including remitted) for a driver
        get("/payments/driver/{driverId}/all") {
            val driverId = call.parameters["driverId"]
                ?: return@get call.respond(HttpStatusCode.BadRequest)
            try {
                val payments = paymentRepository.findByDriverId(UUID.fromString(driverId))
                call.respond(
                    ApiResponse.success(
                        payments.map { PaymentResponse.fromDomain(it) },
                        call.requestId
                    )
                )
            } catch (e: IllegalArgumentException) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ApiResponse.error("INVALID_ID", "Invalid Driver ID", call.requestId)
                )
            }
        }

        // Submit a driver remittance (clears PENDING payments, posts GL)
        post("/remittances") {
            try {
                val request = call.receive<DriverRemittanceRequest>()
                val remittance = recordDriverRemittanceUseCase.execute(request)
                call.respond(
                    HttpStatusCode.Created,
                    ApiResponse.success(DriverRemittanceResponse.fromDomain(remittance), call.requestId)
                )
            } catch (e: IllegalArgumentException) {
                call.respond(
                    HttpStatusCode.UnprocessableEntity,
                    ApiResponse.error("REMITTANCE_ERROR", e.message ?: "Invalid request", call.requestId)
                )
            }
        }

        // List all remittances for a driver
        get("/remittances/driver/{driverId}") {
            val driverId = call.parameters["driverId"]
                ?: return@get call.respond(HttpStatusCode.BadRequest)
            try {
                val remittances = remittanceRepository.findByDriverId(UUID.fromString(driverId))
                call.respond(
                    ApiResponse.success(
                        remittances.map { DriverRemittanceResponse.fromDomain(it) },
                        call.requestId
                    )
                )
            } catch (e: IllegalArgumentException) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ApiResponse.error("INVALID_ID", "Invalid Driver ID", call.requestId)
                )
            }
        }

        // Get a single remittance by ID
        get("/remittances/{remittanceId}") {
            val remittanceId = call.parameters["remittanceId"]
                ?: return@get call.respond(HttpStatusCode.BadRequest)
            val remittance = remittanceRepository.findById(UUID.fromString(remittanceId))
                ?: return@get call.respond(
                    HttpStatusCode.NotFound,
                    ApiResponse.error("NOT_FOUND", "Remittance not found", call.requestId)
                )
            call.respond(ApiResponse.success(DriverRemittanceResponse.fromDomain(remittance), call.requestId))
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
