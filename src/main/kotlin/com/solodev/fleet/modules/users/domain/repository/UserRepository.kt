package com.solodev.fleet.modules.users.domain.repository

import com.solodev.fleet.modules.users.domain.model.Role
import com.solodev.fleet.modules.users.domain.model.User
import com.solodev.fleet.modules.users.domain.model.UserId

interface UserRepository {
    suspend fun findById(id: UserId): User?
    suspend fun findByEmail(email: String): User?
    suspend fun save(user: User): User
    suspend fun deleteById(id: UserId): Boolean
    suspend fun findAll(): List<User>
    suspend fun findAllRoles(): List<Role>
    suspend fun findRoleByName(name: String): Role?
    suspend fun updatePassword(id: UserId, newPasswordHash: String)
}