package com.solodev.fleet.modules.users.application.usecases

import com.solodev.fleet.modules.users.application.dto.StaffProfileUpdateRequest
import com.solodev.fleet.modules.users.application.dto.UserUpdateRequest
import com.solodev.fleet.modules.users.domain.model.StaffProfile
import com.solodev.fleet.modules.users.domain.model.User
import com.solodev.fleet.modules.users.domain.model.UserId
import com.solodev.fleet.modules.users.domain.repository.UserRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

class UpdateUserUseCaseTest {
    private val repository = mockk<UserRepository>()
    private val useCase = UpdateUserUseCase(repository)

    private val existingUser =
        User(
            id = UserId("user-1"),
            email = "juan@fleet.ph",
            passwordHash = "hashed-password",
            firstName = "Juan",
            lastName = "dela Cruz",
            phone = null,
            isActive = true,
        )

    @Test
    fun shouldUpdateName_WhenUserExistsAndNameProvided(): Unit =
        runBlocking {
            // Arrange
            val request = UserUpdateRequest(firstName = "Pedro", lastName = "Santos")
            val savedSlot = slot<User>()
            coEvery { repository.findById(UserId("user-1")) } returns existingUser
            coEvery { repository.save(capture(savedSlot)) } returnsArgument 0

            // Act
            val result = useCase.execute("user-1", request)

            // Assert
            assertThat(result).isNotNull()
            assertThat(result!!.firstName).isEqualTo("Pedro")
            assertThat(result.lastName).isEqualTo("Santos")
            assertThat(savedSlot.captured.firstName).isEqualTo("Pedro")
            assertThat(savedSlot.captured.email).isEqualTo("juan@fleet.ph") // unchanged
        }

    @Test
    fun shouldPreserveExistingFields_WhenPartialUpdateProvided(): Unit =
        runBlocking {
            // Arrange
            val request = UserUpdateRequest(phone = "+63-912-345-6789")
            coEvery { repository.findById(UserId("user-1")) } returns existingUser
            coEvery { repository.save(any()) } returnsArgument 0

            // Act
            val result = useCase.execute("user-1", request)

            // Assert
            assertThat(result).isNotNull()
            assertThat(result!!.firstName).isEqualTo("Juan") // unchanged
            assertThat(result.lastName).isEqualTo("dela Cruz") // unchanged
            assertThat(result.phone).isEqualTo("+63-912-345-6789")
        }

    @Test
    fun shouldDeactivateUser_WhenIsActiveFalseProvided(): Unit =
        runBlocking {
            // Arrange
            val request = UserUpdateRequest(isActive = false)
            val savedSlot = slot<User>()
            coEvery { repository.findById(UserId("user-1")) } returns existingUser
            coEvery { repository.save(capture(savedSlot)) } returnsArgument 0

            // Act
            val result = useCase.execute("user-1", request)

            // Assert
            assertThat(result).isNotNull()
            assertThat(savedSlot.captured.isActive).isFalse()
        }

    @Test
    fun shouldCreateStaffProfile_WhenNoProfileExistsAndProfileDataProvided(): Unit =
        runBlocking {
            // Arrange
            val request =
                UserUpdateRequest(
                    staffProfile = StaffProfileUpdateRequest(department = "Fleet Ops", position = "Driver"),
                )
            val savedSlot = slot<User>()
            coEvery { repository.findById(UserId("user-1")) } returns existingUser
            coEvery { repository.save(capture(savedSlot)) } returnsArgument 0

            // Act
            val result = useCase.execute("user-1", request)

            // Assert
            assertThat(result).isNotNull()
            assertThat(savedSlot.captured.staffProfile).isNotNull()
            assertThat(savedSlot.captured.staffProfile!!.department).isEqualTo("Fleet Ops")
            assertThat(savedSlot.captured.staffProfile!!.position).isEqualTo("Driver")
        }

    @Test
    fun shouldUpdateExistingStaffProfile_WhenProfileExistsAndNewDataProvided(): Unit =
        runBlocking {
            // Arrange
            val existingProfile =
                StaffProfile(
                    id = UUID.randomUUID(),
                    userId = UserId("user-1"),
                    employeeId = "EMP-001",
                    department = "IT",
                    position = "Developer",
                    hireDate = LocalDate.now(),
                )
            val userWithProfile = existingUser.copy(staffProfile = existingProfile)
            val request =
                UserUpdateRequest(
                    staffProfile = StaffProfileUpdateRequest(department = "Engineering", position = "Senior Developer"),
                )
            val savedSlot = slot<User>()
            coEvery { repository.findById(UserId("user-1")) } returns userWithProfile
            coEvery { repository.save(capture(savedSlot)) } returnsArgument 0

            // Act
            val result = useCase.execute("user-1", request)

            // Assert
            assertThat(result).isNotNull()
            assertThat(savedSlot.captured.staffProfile).isNotNull()
            assertThat(savedSlot.captured.staffProfile!!.department).isEqualTo("Engineering")
            assertThat(savedSlot.captured.staffProfile!!.position).isEqualTo("Senior Developer")
            assertThat(savedSlot.captured.staffProfile!!.employeeId).isEqualTo("EMP-001") // preserved
        }

    @Test
    fun shouldReturnNull_WhenUserNotFound(): Unit =
        runBlocking {
            // Arrange
            coEvery { repository.findById(UserId("unknown")) } returns null

            // Act
            val result = useCase.execute("unknown", UserUpdateRequest(firstName = "Pedro"))

            // Assert
            assertThat(result).isNull()
            coVerify(exactly = 0) { repository.save(any()) }
        }
}
