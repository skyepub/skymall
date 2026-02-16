package com.skytree.skymall.repository

import com.skytree.skymall.entity.User
import com.skytree.skymall.entity.UserRole
import org.springframework.data.jpa.repository.JpaRepository

interface UserRepository : JpaRepository<User, Int> {
    fun findByUsername(username: String): User?
    fun findByEmail(email: String): User?
    fun findByRole(role: UserRole): List<User>
}
