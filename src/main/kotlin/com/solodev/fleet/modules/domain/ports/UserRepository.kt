package com.solodev.fleet.modules.domain.ports

import com.solodev.fleet.modules.domain.models.User
import com.solodev.fleet.modules.domain.models.UserId
import com.solodev.fleet.modules.domain.models.Role
import com.solodev.fleet.modules.domain.models.RoleId

interface UserRepository {
    suspend fun findById(id: UserId): User?
    suspend fun findByEmail(email: String): User?
    suspend fun save(user: User): User
    suspend fun deleteById(id: UserId): Boolean
    suspend fun findAll(): List<User>
    
    // Role management
    suspend fun findAllRoles(): List<Role>
    suspend fun findRoleByName(name: String): Role?
    
    // Auth specific
    suspend fun updatePassword(id: UserId, newPasswordHash: String)
}
