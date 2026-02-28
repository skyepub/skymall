package com.skytree.skymall.dto

import com.skytree.skymall.entity.User
import com.skytree.skymall.entity.UserRole
import java.time.LocalDateTime

/**
 * ================================================================
 * 사용자 관련 DTO
 * ================================================================
 *
 * 사용자 정보 조회/수정에 사용되는 DTO 모음.
 *
 * 주의: password는 응답 DTO(UserResponse)에 절대 포함하지 않는다!
 * Entity의 모든 필드를 노출하는 게 아니라, 필요한 필드만 선별하는 것이
 * DTO를 사용하는 핵심 이유 중 하나이다.
 */

// ════════════════════════════════════════
//  Request DTO
// ════════════════════════════════════════

/**
 * 사용자 생성 요청
 * (관리자가 직접 사용자를 생성할 때 사용 — 회원가입은 AuthDto의 RegisterRequest)
 */
data class CreateUserRequest(
    val username: String,
    val email: String,
    val password: String,
    val role: UserRole = UserRole.USER
)

/**
 * 사용자 수정 요청 (Partial Update)
 *
 * 모든 필드가 nullable → 변경할 필드만 전송
 *
 * JSON 예시 (별명만 변경):
 * {"alias": "존"}
 *
 * JSON 예시 (역할 + 활성화 상태 변경):
 * {"role": "MANAGER", "isEnabled": false}
 */
data class UpdateUserRequest(
    val alias: String? = null,           // 별명
    val email: String? = null,           // 이메일 (변경 시 중복 검사)
    val role: UserRole? = null,          // 역할 변경
    val isEnabled: Boolean? = null       // 활성화/비활성화
)

// ════════════════════════════════════════
//  Response DTO
// ════════════════════════════════════════

/**
 * 사용자 응답 DTO
 *
 * password 필드가 없다! (보안상 절대 응답에 포함하지 않는다)
 *
 * JSON 예시:
 * {
 *   "id": 1,
 *   "username": "john",
 *   "email": "john@example.com",
 *   "alias": "존",
 *   "role": "USER",
 *   "isEnabled": true,
 *   "createdAt": "2026-01-15T09:30:00"
 * }
 */
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
        /**
         * User Entity → UserResponse DTO 변환
         * password는 의도적으로 제외한다.
         */
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

/**
 * 사용자 프로필 응답 DTO
 *
 * 사용자 기본 정보 + 주문 통계를 결합한 복합 DTO.
 * UserService에서 UserRepository + SalesOrderRepository를 함께 조회하여 구성한다.
 *
 * JSON 예시:
 * {
 *   "user": { ... },           ← UserResponse (중첩 DTO)
 *   "orderCount": 10,
 *   "totalSpent": 500000,
 *   "avgOrderAmount": 50000
 * }
 */
data class UserProfileResponse(
    val user: UserResponse,          // 사용자 기본 정보 (중첩 DTO)
    val orderCount: Long,            // 총 주문 건수
    val totalSpent: Double,          // 총 지출 금액
    val avgOrderAmount: Double       // 평균 주문 금액
)
