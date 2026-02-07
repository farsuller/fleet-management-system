# Accounting Module - Test Implementation Guide

This document details the testing strategy and implementations for the Accounting module, focused on financial accuracy and ledger integrity.

---

## 1. Testing (The Quality Shield)

### Unit Test: Issue Invoice (AAA Pattern)
```kotlin
class IssueInvoiceUseCaseTest {
    private val repository = mockk<AccountingRepository>()
    private val useCase = IssueInvoiceUseCase(repository)

    @Test
    fun `should correctly calculate total and balance when issuing invoice`() = runBlocking {
        // Arrange
        val request = InvoiceRequest(
            customerId = "cust-1",
            subtotalCents = 10000, // 100 PHP
            taxCents = 1200,       // 12 PHP
            dueDate = "2026-12-31T23:59:59Z"
        )
        coEvery { repository.saveInvoice(any()) } returnsArgument 0

        // Act
        val result = useCase.execute(request)

        // Assert
        assertEquals(11200, result.totalCents)
        assertEquals(11200, result.balanceCents)
        assertEquals(InvoiceStatus.ISSUED, result.status)
        coVerify { repository.saveInvoice(match { it.totalCents == 11200 }) }
    }
}
```
