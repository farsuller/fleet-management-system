package com.solodev.fleet.modules.infrastructure.persistence

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.javatime.date
import org.jetbrains.exposed.sql.javatime.timestamp

object UsersTable : UUIDTable("users") {
    val email = varchar("email", 255).uniqueIndex()
    val passwordHash = varchar("password_hash", 255)
    val firstName = varchar("first_name", 100)
    val lastName = varchar("last_name", 100)
    val phone = varchar("phone", 20).nullable()
    val isActive = bool("is_active").default(true)
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
}

object RolesTable : UUIDTable("roles") {
    val name = varchar("name", 50).uniqueIndex()
    val description = text("description").nullable()
    val createdAt = timestamp("created_at")
}

object UserRolesTable : org.jetbrains.exposed.sql.Table("user_roles") {
    val userId = reference("user_id", UsersTable, onDelete = ReferenceOption.CASCADE)
    val roleId = reference("role_id", RolesTable, onDelete = ReferenceOption.CASCADE)
    val assignedAt = timestamp("assigned_at")
    override val primaryKey = PrimaryKey(userId, roleId)
}

object StaffProfilesTable : UUIDTable("staff_profiles") {
    val userId = reference("user_id", UsersTable, onDelete = ReferenceOption.CASCADE).uniqueIndex()
    val employeeId = varchar("employee_id", 50).uniqueIndex()
    val department = varchar("department", 100).nullable()
    val position = varchar("position", 100).nullable()
    val hireDate = date("hire_date")
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
}
