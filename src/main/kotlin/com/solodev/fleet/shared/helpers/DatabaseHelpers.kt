package com.solodev.fleet.shared.helpers

import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

/**
 * Transaction-aware dbQuery helper. Participates in existing transaction if present, otherwise
 * starts a new one. Critical for atomic operations across multiple repositories.
 */
suspend fun <T> dbQuery(block: suspend () -> T): T =
        if (TransactionManager.currentOrNull() != null) {
            // Participate in existing transaction
            block()
        } else {
            // Start new transaction
            newSuspendedTransaction(Dispatchers.IO) { block() }
        }
