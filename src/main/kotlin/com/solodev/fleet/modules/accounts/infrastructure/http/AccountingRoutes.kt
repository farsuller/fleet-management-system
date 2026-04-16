package com.solodev.fleet.modules.accounts.infrastructure.http

import com.solodev.fleet.modules.accounts.application.ReconciliationService
import com.solodev.fleet.modules.accounts.application.dto.AccountBalanceResponse
import com.solodev.fleet.modules.accounts.application.dto.AccountRequest
import com.solodev.fleet.modules.accounts.application.dto.AccountResponse
import com.solodev.fleet.modules.accounts.application.dto.DriverCollectionRequest
import com.solodev.fleet.modules.accounts.application.dto.DriverRemittanceRequest
import com.solodev.fleet.modules.accounts.application.dto.DriverRemittanceResponse
import com.solodev.fleet.modules.accounts.application.dto.InvoiceLineItemResponse
import com.solodev.fleet.modules.accounts.application.dto.InvoiceRequest
import com.solodev.fleet.modules.accounts.application.dto.InvoiceResponse
import com.solodev.fleet.modules.accounts.application.dto.PaymentMethodRequest
import com.solodev.fleet.modules.accounts.application.dto.PaymentMethodResponse
import com.solodev.fleet.modules.accounts.application.dto.PaymentReceiptResponse
import com.solodev.fleet.modules.accounts.application.dto.PaymentRequest
import com.solodev.fleet.modules.accounts.application.dto.PaymentResponse
import com.solodev.fleet.modules.accounts.application.usecases.GenerateArAgingUseCase
import com.solodev.fleet.modules.accounts.application.usecases.GenerateFinancialReportsUseCase
import com.solodev.fleet.modules.accounts.application.usecases.IssueInvoiceUseCase
import com.solodev.fleet.modules.accounts.application.usecases.ManageAccountUseCase
import com.solodev.fleet.modules.accounts.application.usecases.PayInvoiceUseCase
import com.solodev.fleet.modules.accounts.application.usecases.RecordDriverCollectionUseCase
import com.solodev.fleet.modules.accounts.application.usecases.RecordDriverRemittanceUseCase
import com.solodev.fleet.modules.accounts.domain.model.InvoiceCategory
import com.solodev.fleet.modules.accounts.domain.model.PaymentMethod
import com.solodev.fleet.modules.accounts.domain.model.PaymentMethodStatus
import com.solodev.fleet.modules.accounts.domain.repository.AccountRepository
import com.solodev.fleet.modules.accounts.domain.repository.DriverRemittanceRepository
import com.solodev.fleet.modules.accounts.domain.repository.InvoiceRepository
import com.solodev.fleet.modules.accounts.domain.repository.LedgerRepository
import com.solodev.fleet.modules.accounts.domain.repository.PaymentMethodRepository
import com.solodev.fleet.modules.accounts.domain.repository.PaymentRepository
import com.solodev.fleet.modules.accounts.infrastructure.persistence.InvoiceLineItemsTable
import com.solodev.fleet.modules.rentals.domain.model.CustomerId
import com.solodev.fleet.modules.rentals.domain.repository.CustomerRepository
import com.solodev.fleet.modules.rentals.domain.repository.RentalRepository
import com.solodev.fleet.modules.vehicles.domain.repository.VehicleRepository
import com.solodev.fleet.shared.helpers.dbQuery
import com.solodev.fleet.shared.models.ApiResponse
import com.solodev.fleet.shared.plugins.Idempotency
import com.solodev.fleet.shared.plugins.requestId
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import org.jetbrains.exposed.sql.selectAll
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.TemporalAdjusters
import java.util.UUID

fun Route.accountingRoutes(
    invoiceRepository: InvoiceRepository,
    paymentRepository: PaymentRepository,
    accountRepository: AccountRepository,
    ledgerRepository: LedgerRepository,
    paymentMethodRepository: PaymentMethodRepository,
    customerRepository: CustomerRepository,
    rentalRepository: RentalRepository,
    vehicleRepository: VehicleRepository,
    remittanceRepository: DriverRemittanceRepository,
    reconciliationService: ReconciliationService,
) {
    val issueInvoiceUseCase =
        IssueInvoiceUseCase(
            invoiceRepo = invoiceRepository,
            accountRepo = accountRepository,
            ledgerRepo = ledgerRepository,
        )

    val payInvoiceUseCase =
        PayInvoiceUseCase(
            invoiceRepo = invoiceRepository,
            paymentRepo = paymentRepository,
            accountRepo = accountRepository,
            ledgerRepo = ledgerRepository,
            paymentMethodRepo = paymentMethodRepository,
        )

    val recordDriverCollectionUseCase =
        RecordDriverCollectionUseCase(
            invoiceRepository = invoiceRepository,
            paymentRepository = paymentRepository,
        )

    val recordDriverRemittanceUseCase =
        RecordDriverRemittanceUseCase(
            paymentRepository = paymentRepository,
            invoiceRepository = invoiceRepository,
            accountRepository = accountRepository,
            ledgerRepository = ledgerRepository,
            paymentMethodRepository = paymentMethodRepository,
            remittanceRepository = remittanceRepository,
        )

    val manageAccountUseCase = ManageAccountUseCase(accountRepository = accountRepository)
    val reportsUseCase =
        GenerateFinancialReportsUseCase(
            accountRepo = accountRepository,
            ledgerRepo = ledgerRepository,
        )
    val arAgingUseCase =
        GenerateArAgingUseCase(
            invoiceRepo = invoiceRepository,
            customerRepo = customerRepository,
        )

    fun parsePaymentMethodStatus(raw: String?): PaymentMethodStatus =
        when (raw?.uppercase()) {
            "ACTIVE" -> PaymentMethodStatus.ACTIVE
            "INACTIVE" -> PaymentMethodStatus.INACTIVE
            "DEPRECATED", "MAINTENANCE" -> PaymentMethodStatus.DEPRECATED
            else -> PaymentMethodStatus.ACTIVE
        }

    /** Helper: load customer snapshot for a given UUID string; returns null if not found. */
    suspend fun customerSummaryFor(customerIdValue: String) = customerRepository.findById(CustomerId(customerIdValue))

    suspend fun mapToInvoiceResponse(invoice: com.solodev.fleet.modules.accounts.domain.model.Invoice): InvoiceResponse {
        val customer = customerSummaryFor(invoice.customerId.value)
        val rentalSummary =
            invoice.rentalId?.let { rid ->
                rentalRepository.findById(rid)?.let { rental ->
                    val vehicle = vehicleRepository.findById(rental.vehicleId)
                    com.solodev.fleet.modules.accounts.application.dto.RentalSummary(
                        id = rental.id.value,
                        rentalNumber = rental.rentalNumber,
                        vehiclePlate = vehicle?.licensePlate ?: "UNKNOWN",
                        startDate = rental.startDate.toString(),
                        endDate = rental.endDate.toString(),
                    )
                }
            }
        val lineItems =
            dbQuery {
                InvoiceLineItemsTable
                    .selectAll()
                    .where { InvoiceLineItemsTable.invoiceId eq invoice.id }
                    .map { row ->
                        InvoiceLineItemResponse(
                            id = row[InvoiceLineItemsTable.id].value.toString(),
                            description = row[InvoiceLineItemsTable.description],
                            quantity = row[InvoiceLineItemsTable.quantity].toDouble(),
                            unitPrice = row[InvoiceLineItemsTable.unitPrice],
                            totalAmount = (row[InvoiceLineItemsTable.quantity].toDouble() * row[InvoiceLineItemsTable.unitPrice]).toInt(),
                            currencyCode = row[InvoiceLineItemsTable.currencyCode],
                        )
                    }
            }
        return InvoiceResponse.fromDomain(invoice, customer, rentalSummary).copy(lineItems = lineItems)
    }

    route("/v1/accounting") {
        // List all invoices (with customer snapshot)
        get("/invoices") {
            val categoryParam = call.request.queryParameters["category"]
            val invoices =
                if (categoryParam != null) {
                    val category =
                        runCatching { InvoiceCategory.valueOf(categoryParam) }.getOrElse {
                            return@get call.respond(
                                HttpStatusCode.BadRequest,
                                ApiResponse.error(
                                    "INVALID_CATEGORY",
                                    "Unknown category: $categoryParam. Valid values: ${InvoiceCategory.entries.joinToString()}",
                                    call.requestId,
                                ),
                            )
                        }
                    invoiceRepository.findByCategory(category)
                } else {
                    invoiceRepository.findAll()
                }

            val responses = invoices.map { mapToInvoiceResponse(it) }
            call.respond(ApiResponse.success(responses, call.requestId))
        }

        // Get single invoice by ID (with line items)
        get("/invoices/{id}") {
            val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            try {
                val invoiceId = UUID.fromString(id)
                val invoice =
                    invoiceRepository.findById(invoiceId)
                        ?: return@get call.respond(
                            HttpStatusCode.NotFound,
                            ApiResponse.error("NOT_FOUND", "Invoice $id not found", call.requestId),
                        )
                call.respond(ApiResponse.success(mapToInvoiceResponse(invoice), call.requestId))
            } catch (e: IllegalArgumentException) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ApiResponse.error("INVALID_ID", "Invalid UUID: $id", call.requestId),
                )
            }
        }

        // List invoices for a specific customer
        get("/invoices/customer/{customerId}") {
            val customerId =
                call.parameters["customerId"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest)
            try {
                val invoices = invoiceRepository.findByCustomerId(UUID.fromString(customerId))
                val responses = invoices.map { mapToInvoiceResponse(it) }
                call.respond(ApiResponse.success(responses, call.requestId))
            } catch (e: IllegalArgumentException) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ApiResponse.error("INVALID_ID", "Invalid Customer ID", call.requestId),
                )
            }
        }

        // Issue New Invoice
        post("/invoices") {
            try {
                val request = call.receive<InvoiceRequest>()
                val invoice = issueInvoiceUseCase.execute(request)
                call.respond(
                    HttpStatusCode.Created,
                    ApiResponse.success(mapToInvoiceResponse(invoice), call.requestId),
                )
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.UnprocessableEntity,
                    ApiResponse.error(
                        "INVOICE_ERROR",
                        e.message ?: "Invalid request",
                        call.requestId,
                    ),
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
                        notes = request.notes,
                    )
                call.respond(
                    ApiResponse.success(
                        PaymentReceiptResponse.fromDomain(receipt),
                        call.requestId,
                    ),
                )
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.Conflict,
                    ApiResponse.error("PAYMENT_ERROR", e.message ?: "Failed", call.requestId),
                )
            }
        }

        // List All Payments (optionally filtered by invoiceId and/or category)
        get("/payments") {
            val invoiceIdParam = call.request.queryParameters["invoiceId"]
            val categoryParam = call.request.queryParameters["category"]
            val payments =
                when {
                    invoiceIdParam != null && categoryParam != null -> {
                        val invoiceUuid =
                            try {
                                UUID.fromString(invoiceIdParam)
                            } catch (e: IllegalArgumentException) {
                                return@get call.respond(
                                    HttpStatusCode.BadRequest,
                                    ApiResponse.error("INVALID_INVOICE_ID", "Invalid UUID: $invoiceIdParam", call.requestId),
                                )
                            }
                        val category =
                            runCatching { InvoiceCategory.valueOf(categoryParam) }.getOrElse {
                                return@get call.respond(
                                    HttpStatusCode.BadRequest,
                                    ApiResponse.error(
                                        "INVALID_CATEGORY",
                                        "Unknown category: $categoryParam. Valid: ${InvoiceCategory.entries.joinToString()}",
                                        call.requestId,
                                    ),
                                )
                            }
                        paymentRepository.findByCategoryAndInvoiceId(category.name, invoiceUuid)
                    }
                    invoiceIdParam != null -> {
                        try {
                            paymentRepository.findByInvoiceId(UUID.fromString(invoiceIdParam))
                        } catch (e: IllegalArgumentException) {
                            return@get call.respond(
                                HttpStatusCode.BadRequest,
                                ApiResponse.error("INVALID_INVOICE_ID", "Invalid UUID: $invoiceIdParam", call.requestId),
                            )
                        }
                    }
                    categoryParam != null -> {
                        runCatching { InvoiceCategory.valueOf(categoryParam) }.getOrElse {
                            return@get call.respond(
                                HttpStatusCode.BadRequest,
                                ApiResponse.error(
                                    "INVALID_CATEGORY",
                                    "Unknown category: $categoryParam. Valid: ${InvoiceCategory.entries.joinToString()}",
                                    call.requestId,
                                ),
                            )
                        }
                        paymentRepository.findByCategory(categoryParam)
                    }
                    else -> paymentRepository.findAll()
                }
            call.respond(
                ApiResponse.success(
                    payments.map { PaymentResponse.fromDomain(it) },
                    call.requestId,
                ),
            )
        }

        // List Payments by Invoice ID (dedicated route)
        get("/payments/invoice/{id}") {
            val invoiceId =
                call.parameters["id"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest)
            try {
                val payments = paymentRepository.findByInvoiceId(UUID.fromString(invoiceId))
                call.respond(
                    ApiResponse.success(
                        payments.map { PaymentResponse.fromDomain(it) },
                        call.requestId,
                    ),
                )
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, ApiResponse.error("INVALID_ID", e.message ?: "Invalid ID", call.requestId))
            }
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
                        call.requestId,
                    ),
                )
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ApiResponse.error("INVALID_ID", "Invalid Customer ID", call.requestId),
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
                    ApiResponse.success(PaymentResponse.fromDomain(payment), call.requestId),
                )
            } catch (e: IllegalArgumentException) {
                call.respond(
                    HttpStatusCode.UnprocessableEntity,
                    ApiResponse.error(
                        "COLLECTION_ERROR",
                        e.message ?: "Invalid request",
                        call.requestId,
                    ),
                )
            }
        }

        // List PENDING collections for a driver (not yet remitted)
        get("/payments/driver/{driverId}/pending") {
            val driverId =
                call.parameters["driverId"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest)
            try {
                val payments = paymentRepository.findPendingByDriverId(UUID.fromString(driverId))
                call.respond(
                    ApiResponse.success(
                        payments.map { PaymentResponse.fromDomain(it) },
                        call.requestId,
                    ),
                )
            } catch (e: IllegalArgumentException) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ApiResponse.error("INVALID_ID", "Invalid Driver ID", call.requestId),
                )
            }
        }

        // List all payments (including remitted) for a driver
        get("/payments/driver/{driverId}/all") {
            val driverId =
                call.parameters["driverId"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest)
            try {
                val payments = paymentRepository.findByDriverId(UUID.fromString(driverId))
                call.respond(
                    ApiResponse.success(
                        payments.map { PaymentResponse.fromDomain(it) },
                        call.requestId,
                    ),
                )
            } catch (e: IllegalArgumentException) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ApiResponse.error("INVALID_ID", "Invalid Driver ID", call.requestId),
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
                    ApiResponse.success(
                        DriverRemittanceResponse.fromDomain(remittance),
                        call.requestId,
                    ),
                )
            } catch (e: IllegalArgumentException) {
                call.respond(
                    HttpStatusCode.UnprocessableEntity,
                    ApiResponse.error(
                        "REMITTANCE_ERROR",
                        e.message ?: "Invalid request",
                        call.requestId,
                    ),
                )
            }
        }

        // List all remittances for a driver
        get("/remittances/driver/{driverId}") {
            val driverId =
                call.parameters["driverId"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest)
            try {
                val remittances = remittanceRepository.findByDriverId(UUID.fromString(driverId))
                call.respond(
                    ApiResponse.success(
                        remittances.map { DriverRemittanceResponse.fromDomain(it) },
                        call.requestId,
                    ),
                )
            } catch (e: IllegalArgumentException) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ApiResponse.error("INVALID_ID", "Invalid Driver ID", call.requestId),
                )
            }
        }

        // Get a single remittance by ID
        get("/remittances/{remittanceId}") {
            val remittanceId =
                call.parameters["remittanceId"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest)
            val remittance =
                remittanceRepository.findById(UUID.fromString(remittanceId))
                    ?: return@get call.respond(
                        HttpStatusCode.NotFound,
                        ApiResponse.error(
                            "NOT_FOUND",
                            "Remittance not found",
                            call.requestId,
                        ),
                    )
            call.respond(
                ApiResponse.success(
                    DriverRemittanceResponse.fromDomain(remittance),
                    call.requestId,
                ),
            )
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
                            call.requestId,
                        ),
                    )

            try {
                val balance =
                    ledgerRepository.calculateAccountBalance(
                        account.id,
                        java.time.Instant.now(),
                    )
                call.respond(
                    ApiResponse.success(
                        AccountBalanceResponse(
                            account = account.accountName,
                            balance = balance,
                        ),
                        call.requestId,
                    ),
                )
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ApiResponse.error(
                        "BALANCE_QUERY_ERROR",
                        e.message ?: "Failed to calculate balance",
                        call.requestId,
                    ),
                )
            }
        }

        // --- Payment Methods CRUD ---

        get("/payment-methods") {
            val methods = paymentMethodRepository.findAll()
            call.respond(
                ApiResponse.success(
                    methods.map { PaymentMethodResponse.fromDomain(it) },
                    call.requestId,
                ),
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
                        status = parsePaymentMethodStatus(request.status),
                        description = request.description,
                    )
                val saved = paymentMethodRepository.save(method)
                call.respond(
                    HttpStatusCode.Created,
                    ApiResponse.success(
                        PaymentMethodResponse.fromDomain(saved),
                        call.requestId,
                    ),
                )
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ApiResponse.error(
                        "BAD_REQUEST",
                        e.message ?: "Invalid request",
                        call.requestId,
                    ),
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
                        status = parsePaymentMethodStatus(request.status),
                        description = request.description,
                        updatedAt = java.time.Instant.now(),
                    )
                val saved = paymentMethodRepository.save(updated)
                call.respond(
                    ApiResponse.success(
                        PaymentMethodResponse.fromDomain(saved),
                        call.requestId,
                    ),
                )
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ApiResponse.error(
                        "BAD_REQUEST",
                        e.message ?: "Invalid request",
                        call.requestId,
                    ),
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
                        call.requestId,
                    ),
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
                ApiResponse.success(AccountResponse.fromDomain(account, 0), call.requestId),
            )
        }

        put("/{id}") {
            // Update account details
            val id = call.parameters["id"] ?: return@put call.respond(HttpStatusCode.BadRequest)
            val request = call.receive<AccountRequest>()
            val account = manageAccountUseCase.update(id, request)
            call.respond(
                ApiResponse.success(AccountResponse.fromDomain(account, 0), call.requestId),
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

    route("/v1/accounting/reports") {
        get("/revenue-kpis") {
            val kpis = reportsUseCase.revenueKpis()
            call.respond(ApiResponse.success(kpis, call.requestId))
        }

        get("/revenue-quarterly") {
            val quarterly = reportsUseCase.revenueQuarterly()
            call.respond(ApiResponse.success(quarterly, call.requestId))
        }

        get("/revenue") {
            // Accept both date-only (yyyy-MM-dd) and full ISO-8601 timestamps
            fun parseDate(
                raw: String,
                endOfDay: Boolean = false,
            ): Instant =
                runCatching { Instant.parse(raw) }.getOrElse {
                    val ld = LocalDate.parse(raw)
                    if (endOfDay) {
                        ld.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()
                    } else {
                        ld.atStartOfDay(ZoneOffset.UTC).toInstant()
                    }
                }
            val start = call.parameters["startDate"]?.let { parseDate(it) } ?: Instant.MIN
            val end = call.parameters["endDate"]?.let { parseDate(it, endOfDay = true) } ?: Instant.now()
            val report = reportsUseCase.revenueReport(start, end)
            call.respond(ApiResponse.success(report, call.requestId))
        }

        get("/revenue-timeseries") {
            fun parseDate(
                raw: String,
                endOfDay: Boolean = false,
            ): Instant =
                runCatching { Instant.parse(raw) }.getOrElse {
                    val ld = LocalDate.parse(raw)
                    if (endOfDay) {
                        ld.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()
                    } else {
                        ld.atStartOfDay(ZoneOffset.UTC).toInstant()
                    }
                }
            val groupBy = call.parameters["groupBy"] ?: "monthly"
            val start =
                call.parameters["startDate"]?.let { parseDate(it) }
                    ?: LocalDate
                        .now(ZoneOffset.UTC)
                        .with(TemporalAdjusters.firstDayOfYear())
                        .atStartOfDay(ZoneOffset.UTC)
                        .toInstant()
            val end = call.parameters["endDate"]?.let { parseDate(it, endOfDay = true) } ?: Instant.now()
            val series = reportsUseCase.revenueTimeSeries(groupBy, start, end)
            call.respond(ApiResponse.success(series, call.requestId))
        }

        get("/balance-sheet") {
            // Accept both date-only (yyyy-MM-dd) and full ISO-8601 timestamps
            fun parseDate(raw: String): Instant =
                runCatching { Instant.parse(raw) }.getOrElse {
                    LocalDate
                        .parse(raw)
                        .plusDays(1)
                        .atStartOfDay(ZoneOffset.UTC)
                        .toInstant()
                }
            val asOf = call.parameters["asOf"]?.let { parseDate(it) } ?: Instant.now()
            val report = reportsUseCase.balanceSheet(asOf)
            call.respond(ApiResponse.success(report, call.requestId))
        }

        get("/ar-aging") {
            fun parseDate(raw: String): Instant =
                runCatching { Instant.parse(raw) }.getOrElse {
                    LocalDate
                        .parse(raw)
                        .plusDays(1)
                        .atStartOfDay(ZoneOffset.UTC)
                        .toInstant()
                }
            val asOf = call.parameters["asOf"]?.let { parseDate(it) } ?: Instant.now()
            val report = arAgingUseCase.arAging(asOf)
            call.respond(ApiResponse.success(report, call.requestId))
        }

        get("/profit-loss") {
            fun parseDate(
                raw: String,
                endOfDay: Boolean = false,
            ): Instant =
                runCatching { Instant.parse(raw) }.getOrElse {
                    val ld = LocalDate.parse(raw)
                    if (endOfDay) {
                        ld.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()
                    } else {
                        ld.atStartOfDay(ZoneOffset.UTC).toInstant()
                    }
                }
            val start =
                call.parameters["from"]?.let { parseDate(it) }
                    ?: LocalDate
                        .now(ZoneOffset.UTC)
                        .with(TemporalAdjusters.firstDayOfYear())
                        .atStartOfDay(ZoneOffset.UTC)
                        .toInstant()
            val end = call.parameters["to"]?.let { parseDate(it, endOfDay = true) } ?: Instant.now()
            val report = reportsUseCase.profitLoss(start, end)
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
