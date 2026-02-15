package com.solodev.fleet.modules.users.infrastructure.persistence

import com.solodev.fleet.modules.users.domain.model.Role
import com.solodev.fleet.modules.users.domain.model.RoleId
import com.solodev.fleet.modules.users.domain.model.StaffProfile
import com.solodev.fleet.modules.users.domain.model.User
import com.solodev.fleet.modules.users.domain.model.UserId
import com.solodev.fleet.modules.users.domain.repository.UserRepository
import com.solodev.fleet.shared.helpers.dbQuery
import java.time.Instant
import java.util.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

class UserRepositoryImpl : UserRepository {

    private fun ResultRow.toUser(
            roles: List<Role> = emptyList(),
            staffProfile: StaffProfile? = null
    ) =
            User(
                    id = UserId(this[UsersTable.id].value.toString()),
                    email = this[UsersTable.email],
                    passwordHash = this[UsersTable.passwordHash],
                    firstName = this[UsersTable.firstName],
                    lastName = this[UsersTable.lastName],
                    phone = this[UsersTable.phone],
                    isActive = this[UsersTable.isActive],
                    isVerified = this[UsersTable.isVerified],
                    roles = roles,
                    staffProfile = staffProfile
            )

    private fun ResultRow.toRole() =
            Role(
                    id = RoleId(this[RolesTable.id].value.toString()),
                    name = this[RolesTable.name],
                    description = this[RolesTable.description]
            )

    private fun ResultRow.toStaffProfile() =
            StaffProfile(
                    id = this[StaffProfilesTable.id].value,
                    userId = UserId(this[StaffProfilesTable.userId].value.toString()),
                    employeeId = this[StaffProfilesTable.employeeId],
                    department = this[StaffProfilesTable.department],
                    position = this[StaffProfilesTable.position],
                    hireDate = this[StaffProfilesTable.hireDate]
            )

    override suspend fun findById(id: UserId): User? = dbQuery {
        val userRow =
                UsersTable.selectAll()
                        .where { UsersTable.id eq UUID.fromString(id.value) }
                        .singleOrNull()
                        ?: return@dbQuery null

        val roles =
                (UserRolesTable innerJoin RolesTable)
                        .selectAll()
                        .where { UserRolesTable.userId eq UUID.fromString(id.value) }
                        .map { it.toRole() }

        val staffProfile =
                StaffProfilesTable.selectAll()
                        .where { StaffProfilesTable.userId eq UUID.fromString(id.value) }
                        .map { it.toStaffProfile() }
                        .singleOrNull()

        userRow.toUser(roles, staffProfile)
    }

    override suspend fun findByEmail(email: String): User? = dbQuery {
        val userRow =
                UsersTable.selectAll().where { UsersTable.email eq email }.singleOrNull()
                        ?: return@dbQuery null
        val userId = userRow[UsersTable.id].value

        val roles =
                (UserRolesTable innerJoin RolesTable)
                        .selectAll()
                        .where { UserRolesTable.userId eq userId }
                        .map { it.toRole() }

        val staffProfile =
                StaffProfilesTable.selectAll()
                        .where { StaffProfilesTable.userId eq userId }
                        .map { it.toStaffProfile() }
                        .singleOrNull()

        userRow.toUser(roles, staffProfile)
    }

    override suspend fun save(user: User): User = dbQuery {
        val userUuid = UUID.fromString(user.id.value)
        val now = Instant.now()

        val exists = UsersTable.selectAll().where { UsersTable.id eq userUuid }.count() > 0

        if (exists) {
            UsersTable.update({ UsersTable.id eq userUuid }) {
                it[email] = user.email
                it[passwordHash] = user.passwordHash
                it[firstName] = user.firstName
                it[lastName] = user.lastName
                it[phone] = user.phone
                it[isActive] = user.isActive
                it[isVerified] = user.isVerified
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
                it[isVerified] = user.isVerified
                it[createdAt] = now
                it[updatedAt] = now
            }
        }

        // Sync roles (simple wipe and re-insert for now)
        UserRolesTable.deleteWhere { UserRolesTable.userId eq userUuid }
        user.roles.forEach { role ->
            UserRolesTable.insert {
                it[userId] = userUuid
                it[roleId] = UUID.fromString(role.id.value)
                it[assignedAt] = now
            }
        }

        // Sync staff profile
        user.staffProfile?.let { profile ->
            val profileExists =
                    StaffProfilesTable.selectAll()
                            .where { StaffProfilesTable.id eq profile.id }
                            .count() > 0
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
            val roles =
                    (UserRolesTable innerJoin RolesTable)
                            .selectAll()
                            .where { UserRolesTable.userId eq userId }
                            .map { it.toRole() }
            val staffProfile =
                    StaffProfilesTable.selectAll()
                            .where { StaffProfilesTable.userId eq userId }
                            .map { it.toStaffProfile() }
                            .singleOrNull()
            userRow.toUser(roles, staffProfile)
        }
    }

    override suspend fun findAllRoles(): List<Role> = dbQuery {
        RolesTable.selectAll().map { it.toRole() }
    }

    override suspend fun findRoleByName(name: String): Role? = dbQuery {
        RolesTable.selectAll().where { RolesTable.name eq name }.map { it.toRole() }.singleOrNull()
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
