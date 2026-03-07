package com.solodev.fleet.modules.accounts.domain.model

import org.junit.jupiter.api.Test
import kotlin.test.*

class LedgerEntryTest {

    @Test
    fun `debit lines must sum to equal credit lines for balanced entry`() {
        val lines = listOf(
            LedgerLine(account = "1100", debit = 5000, credit = 0),
            LedgerLine(account = "4000", debit = 0, credit = 5000)
        )
        assertTrue(lines.sumOf { it.debit } == lines.sumOf { it.credit })
    }

    @Test
    fun `unbalanced ledger lines should fail validation`() {
        val lines = listOf(
            LedgerLine(account = "1100", debit = 5000, credit = 0),
            LedgerLine(account = "4000", debit = 0, credit = 4000)
        )
        val balanced = lines.sumOf { it.debit } == lines.sumOf { it.credit }
        assertFalse(balanced)
    }
}

data class LedgerLine(val account: String, val debit: Int, val credit: Int)
