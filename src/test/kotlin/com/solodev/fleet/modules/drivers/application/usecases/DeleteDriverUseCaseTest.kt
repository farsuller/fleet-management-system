package com.solodev.fleet.modules.drivers.application.usecases

import com.solodev.fleet.modules.drivers.domain.model.DriverId
import com.solodev.fleet.modules.drivers.domain.repository.DriverRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DeleteDriverUseCaseTest {
    private val repository = mockk<DriverRepository>()
    private val useCase = DeleteDriverUseCase(repository)

    @Test
    fun `should delete driver when id exists`() =
        runBlocking {
            val id = "driver-001"
            coEvery { repository.deleteById(DriverId(id)) } returns true

            val result = useCase.execute(id)

            assertThat(result).isTrue()
            coVerify { repository.deleteById(DriverId(id)) }
        }

    @Test
    fun `should return false when driver does not exist`() =
        runBlocking {
            val id = "unknown"
            coEvery { repository.deleteById(DriverId(id)) } returns false

            val result = useCase.execute(id)

            assertThat(result).isFalse()
            coVerify { repository.deleteById(DriverId(id)) }
        }
}
