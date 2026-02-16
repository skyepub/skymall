package com.skytree.skymall.dto

import com.skytree.skymall.entity.User
import com.skytree.skymall.entity.UserRole
import java.time.LocalDateTime

// ── Request ──

data class CreateUserRequest(
    val username: String,
    val email: String,
    val password: String,
    val role: UserRole = UserRole.USER
)

data class UpdateUserRequest(
    val alias: String? = null,
    val email: String? = null,
    val role: UserRole? = null,
    val isEnabled: Boolean? = null
)

// ── Response ──

data class UserResponse(
    val id: Int,
    val username: String,
    val email: String,
    val alias: String?,
    val role: UserRole,
    val isEnabled: Boolean,
    val createdAt: LocalDateTime
) {
    companion object {
        fun from(user: User) = UserResponse(
            id = user.id,
            username = user.username,
            email = user.email,
            alias = user.alias,
            role = user.role,
            isEnabled = user.isEnabled,
            createdAt = user.createdAt
        )
    }
}

data class UserProfileResponse(
    val user: UserResponse,
    val orderCount: Long,
    val totalSpent: Double,
    val avgOrderAmount: Double
)
