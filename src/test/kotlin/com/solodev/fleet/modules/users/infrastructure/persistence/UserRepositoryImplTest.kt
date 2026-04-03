package com.solodev.fleet.modules.users.infrastructure.persistence

import com.solodev.fleet.IntegrationTestBase
import com.solodev.fleet.modules.users.domain.model.*
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.util.*
import kotlinx.coroutines.runBlocking as krunBlocking

class UserRepositoryImplTest : IntegrationTestBase() {

    private val repository = UserRepositoryImpl()

    @BeforeEach
    fun setUp() {
        cleanDatabase()
    }

    @Test
    fun `should save and find user with roles and staff profile`() {
        // Arrange
        val userId = UUID.randomUUID()
        val profileId = UUID.randomUUID()
        
        // Use existing role if seeded by Flyway
        val roleId = transaction {
            RolesTable.selectAll().where { RolesTable.name eq "ADMIN" }
                .map { it[RolesTable.id].value }
                .firstOrNull() ?: UUID.randomUUID().also { newId ->
                    RolesTable.insert {
                        it[id] = newId
                        it[name] = "ADMIN"
                        it[description] = "Administrator"
                        it[createdAt] = Instant.now()
                    }
                }
        }

        val role = Role(id = RoleId(roleId.toString()), name = "ADMIN", description = "Administrator")
        val staffProfile = StaffProfile(
            id = profileId,
            userId = UserId(userId.toString()),
            employeeId = "EMP-001",
            department = "Fleet Management",
            position = "Manager",
            hireDate = LocalDate.now()
        )
        
        val user = User(
            id = UserId(userId.toString()),
            email = "test@fleet.ph",
            passwordHash = "hashed",
            firstName = "Test",
            lastName = "User",
            roles = listOf(role),
            staffProfile = staffProfile
        )

        // Act
        val saved = runBlocking { repository.save(user) }
        val found = runBlocking { repository.findById(UserId(userId.toString())) }

        // Assert
        assertThat(found).isNotNull
        assertThat(found?.email).isEqualTo("test@fleet.ph")
        assertThat(found?.roles).hasSize(1)
        assertThat(found?.roles?.first()?.name).isEqualTo("ADMIN")
        assertThat(found?.staffProfile).isNotNull
        assertThat(found?.staffProfile?.employeeId).isEqualTo("EMP-001")
        assertThat(found?.staffProfile?.department).isEqualTo("Fleet Management")
    }

    @Test
    fun `should enforce single-role assignment correctly via save`() {
        // Arrange
        val userId = UUID.randomUUID()
        
        val roleAdminId = transaction {
            RolesTable.selectAll().where { RolesTable.name eq "ADMIN" }
                .map { it[RolesTable.id].value }
                .firstOrNull() ?: UUID.randomUUID().also { newId ->
                    RolesTable.insert { it[id] = newId; it[name] = "ADMIN"; it[createdAt] = Instant.now() }
                }
        }
        val roleUserId = transaction {
            RolesTable.selectAll().where { RolesTable.name eq "USER" }
                .map { it[RolesTable.id].value }
                .firstOrNull() ?: UUID.randomUUID().also { newId ->
                    RolesTable.insert { it[id] = newId; it[name] = "USER"; it[createdAt] = Instant.now() }
                }
        }
        
        val adminRole = Role(id = RoleId(roleAdminId.toString()), name = "ADMIN")
        val userRole = Role(id = RoleId(roleUserId.toString()), name = "USER")
        
        val initialUser = User(
            id = UserId(userId.toString()),
            email = "test2@fleet.ph",
            passwordHash = "hash",
            firstName = "Multi",
            lastName = "Role",
            roles = listOf(adminRole)
        )
        
        // Act - Save with ADMIN
        runBlocking { repository.save(initialUser) }
        
        // Save with USER (replacing roles list)
        val updatedUser = initialUser.copy(roles = listOf(userRole))
        runBlocking { repository.save(updatedUser) }
        
        val found = runBlocking { repository.findById(UserId(userId.toString())) }
        
        // Assert
        assertThat(found?.roles).hasSize(1)
        assertThat(found?.roles?.first()?.name).isEqualTo("USER")
    }

    @Test
    fun `should update existing staff profile fields`() {
        // Arrange
        val userId = UUID.randomUUID()
        val profileId = UUID.randomUUID()
        
        val initialProfile = StaffProfile(
            id = profileId,
            userId = UserId(userId.toString()),
            employeeId = "EMP-999",
            department = "Old Dept",
            position = "Old Pos",
            hireDate = LocalDate.now()
        )
        
        val user = User(
            id = UserId(userId.toString()),
            email = "update@fleet.ph",
            passwordHash = "hash",
            firstName = "Update",
            lastName = "Test",
            staffProfile = initialProfile
        )
        
        runBlocking { repository.save(user) }
        
        val updatedProfile = initialProfile.copy(department = "New Dept", position = "New Pos")
        val updatedUser = user.copy(staffProfile = updatedProfile)
        
        // Act
        runBlocking { repository.save(updatedUser) }
        val found = runBlocking { repository.findById(UserId(userId.toString())) }
        
        // Assert
        assertThat(found?.staffProfile?.department).isEqualTo("New Dept")
        assertThat(found?.staffProfile?.position).isEqualTo("New Pos")
        assertThat(found?.staffProfile?.employeeId).isEqualTo("EMP-999")
    }
}

// Helper for coroutines in tests
fun <T> runBlocking(block: suspend () -> T): T = krunBlocking { block() }
