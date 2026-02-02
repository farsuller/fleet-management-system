package com.solodev.fleet.modules.infrastructure.persistence

import com.solodev.fleet.modules.domain.models.*
import com.solodev.fleet.modules.domain.ports.UserRepository
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.Instant
import java.util.*

class UserRepositoryImpl : UserRepository {

    private suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }

    private fun ResultRow.toUser(roles: List<Role> = emptyList(), staffProfile: StaffProfile? = null) = User(
        id = UserId(this[UsersTable.id].value.toString()),
        email = this[UsersTable.email],
        passwordHash = this[UsersTable.passwordHash],
        firstName = this[UsersTable.firstName],
        lastName = this[UsersTable.lastName],
        phone = this[UsersTable.phone],
        isActive = this[UsersTable.isActive],
        roles = roles,
        staffProfile = staffProfile
    )

    private fun ResultRow.toRole() = Role(
        id = RoleId(this[RolesTable.id].value.toString()),
        name = this[RolesTable.name],
        description = this[RolesTable.description]
    )

    private fun ResultRow.toStaffProfile() = StaffProfile(
        id = this[StaffProfilesTable.id].value,
        userId = UserId(this[StaffProfilesTable.userId].value.toString()),
        employeeId = this[StaffProfilesTable.employeeId],
        department = this[StaffProfilesTable.department],
        position = this[StaffProfilesTable.position],
        hireDate = this[StaffProfilesTable.hireDate]
    )

    override suspend fun findById(id: UserId): User? = dbQuery {
        val userRow = UsersTable.select { UsersTable.id eq UUID.fromString(id.value) }.singleOrNull() ?: return@dbQuery null
        
        val roles = (UserRolesTable innerJoin RolesTable)
            .select { UserRolesTable.userId eq UUID.fromString(id.value) }
            .map { it.toRole() }
            
        val staffProfile = StaffProfilesTable
            .select { StaffProfilesTable.userId eq UUID.fromString(id.value) }
            .map { it.toStaffProfile() }
            .singleOrNull()
            
        userRow.toUser(roles, staffProfile)
    }

    override suspend fun findByEmail(email: String): User? = dbQuery {
        val userRow = UsersTable.select { UsersTable.email eq email }.singleOrNull() ?: return@dbQuery null
        val userId = userRow[UsersTable.id].value
        
        val roles = (UserRolesTable innerJoin RolesTable)
            .select { UserRolesTable.userId eq userId }
            .map { it.toRole() }
            
        val staffProfile = StaffProfilesTable
            .select { StaffProfilesTable.userId eq userId }
            .map { it.toStaffProfile() }
            .singleOrNull()
            
        userRow.toUser(roles, staffProfile)
    }

    override suspend fun save(user: User): User = dbQuery {
        val userUuid = UUID.fromString(user.id.value)
        val now = Instant.now()
        
        val exists = UsersTable.select { UsersTable.id eq userUuid }.count() > 0
        
        if (exists) {
            UsersTable.update({ UsersTable.id eq userUuid }) {
                it[email] = user.email
                it[passwordHash] = user.passwordHash
                it[firstName] = user.firstName
                it[lastName] = user.lastName
                it[phone] = user.phone
                it[isActive] = user.isActive
                it[updatedAt] = now
            }
        } else {
            UsersTable.insert {
                it[id] = userUuid
                it[email] = user.email
                it[passwordHash] = user.passwordHash
                it[firstName] = user.firstName
                it[lastName] = user.lastName
                it[phone] = user.phone
                it[isActive] = user.isActive
                it[createdAt] = now
                it[updatedAt] = now
            }
        }
        
        // Sync roles (simple wipe and re-insert for now)
        UserRolesTable.deleteWhere { userId eq userUuid }
        user.roles.forEach { role ->
            UserRolesTable.insert {
                it[userId] = userUuid
                it[roleId] = UUID.fromString(role.id.value)
                it[assignedAt] = now
            }
        }
        
        // Sync staff profile
        user.staffProfile?.let { profile ->
            val profileExists = StaffProfilesTable.select { StaffProfilesTable.id eq profile.id }.count() > 0
            if (profileExists) {
                StaffProfilesTable.update({ StaffProfilesTable.id eq profile.id }) {
                    it[employeeId] = profile.employeeId
                    it[department] = profile.department
                    it[position] = profile.position
                    it[hireDate] = profile.hireDate
                    it[updatedAt] = now
                }
            } else {
                StaffProfilesTable.insert {
                    it[id] = profile.id
                    it[userId] = userUuid
                    it[employeeId] = profile.employeeId
                    it[department] = profile.department
                    it[position] = profile.position
                    it[hireDate] = profile.hireDate
                    it[createdAt] = now
                    it[updatedAt] = now
                }
            }
        }
        
        user
    }

    override suspend fun deleteById(id: UserId): Boolean = dbQuery {
        UsersTable.deleteWhere { UsersTable.id eq UUID.fromString(id.value) } > 0
    }

    override suspend fun findAll(): List<User> = dbQuery {
        UsersTable.selectAll().map { userRow ->
            val userId = userRow[UsersTable.id].value
            val roles = (UserRolesTable innerJoin RolesTable)
                .select { UserRolesTable.userId eq userId }
                .map { it.toRole() }
            val staffProfile = StaffProfilesTable
                .select { StaffProfilesTable.userId eq userId }
                .map { it.toStaffProfile() }
                .singleOrNull()
            userRow.toUser(roles, staffProfile)
        }
    }

    override suspend fun findAllRoles(): List<Role> = dbQuery {
        RolesTable.selectAll().map { it.toRole() }
    }

    override suspend fun findRoleByName(name: String): Role? = dbQuery {
        RolesTable.select { RolesTable.name eq name }.map { it.toRole() }.singleOrNull()
    }

    override suspend fun updatePassword(id: UserId, newPasswordHash: String) {
        dbQuery {
            UsersTable.update({ UsersTable.id eq UUID.fromString(id.value) }) {
                it[passwordHash] = newPasswordHash
                it[updatedAt] = Instant.now()
            }
        }
    }
}
