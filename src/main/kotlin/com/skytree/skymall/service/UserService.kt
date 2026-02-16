package com.skytree.skymall.service

import com.skytree.skymall.dto.*
import com.skytree.skymall.entity.User
import com.skytree.skymall.exception.BusinessException
import com.skytree.skymall.exception.DuplicateException
import com.skytree.skymall.exception.EntityNotFoundException
import com.skytree.skymall.repository.SalesOrderRepository
import com.skytree.skymall.repository.UserRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 사용자 서비스
 *
 * UserRepository + SalesOrderRepository를 복합 사용.
 * 사용자 정보와 주문 이력을 결합한 비즈니스 로직.
 */
@Service
@Transactional(readOnly = true)
class UserService(
    private val userRepo: UserRepository,
    private val orderRepo: SalesOrderRepository
) {
    // ══════════════════════════════════════
    //  CRUD
    // ══════════════════════════════════════

    fun getAllUsers(pageable: Pageable): Page<UserResponse> =
        userRepo.findAll(pageable).map { UserResponse.from(it) }

    fun getUser(id: Int): UserResponse =
        UserResponse.from(findUserOrThrow(id))

    fun getUserByUsername(username: String): UserResponse {
        val user = userRepo.findByUsername(username)
            ?: throw EntityNotFoundException("사용자", username)
        return UserResponse.from(user)
    }

    @Transactional
    fun createUser(req: CreateUserRequest): UserResponse {
        // 비즈니스 검증: 중복 확인
        userRepo.findByUsername(req.username)?.let {
            throw DuplicateException("사용자명", req.username)
        }
        userRepo.findByEmail(req.email)?.let {
            throw DuplicateException("이메일", req.email)
        }

        val user = userRepo.save(
            User(
                username = req.username,
                email = req.email,
                password = req.password,
                role = req.role
            )
        )
        return UserResponse.from(user)
    }

    @Transactional
    fun updateUser(id: Int, req: UpdateUserRequest): UserResponse {
        val user = findUserOrThrow(id)
        req.alias?.let { user.alias = it }
        req.email?.let { newEmail ->
            // 비즈니스 검증: 다른 사용자와 이메일 중복
            userRepo.findByEmail(newEmail)?.let { existing ->
                if (existing.id != id) throw DuplicateException("이메일", newEmail)
            }
            user.email = newEmail
        }
        req.role?.let { user.role = it }
        req.isEnabled?.let { user.isEnabled = it }
        return UserResponse.from(user)
    }

    /**
     * 사용자 삭제 — 주문 이력이 있으면 삭제 불가 (비활성화 유도)
     * UserRepo + OrderRepo 복합 검증
     */
    @Transactional
    fun deleteUser(id: Int) {
        findUserOrThrow(id)
        val orderCount = orderRepo.countByUserId(id)
        if (orderCount > 0) {
            throw BusinessException(
                "주문 이력이 ${orderCount}건 있어 삭제할 수 없습니다. 비활성화를 사용하세요."
            )
        }
        userRepo.deleteById(id)
    }

    // ══════════════════════════════════════
    //  복합 조회 — UserRepo + OrderRepo
    // ══════════════════════════════════════

    /**
     * 사용자 프로필 + 주문 통계
     * User 정보에 주문 건수, 총 지출, 평균 주문금액을 결합
     */
    fun getUserProfile(id: Int): UserProfileResponse {
        val user = findUserOrThrow(id)
        return UserProfileResponse(
            user = UserResponse.from(user),
            orderCount = orderRepo.countByUserId(id),
            totalSpent = orderRepo.sumTotalAmountByUserId(id) ?: 0.0,
            avgOrderAmount = orderRepo.avgTotalAmountByUserId(id) ?: 0.0
        )
    }

    // ══════════════════════════════════════
    //  내부 헬퍼
    // ══════════════════════════════════════

    internal fun findUserOrThrow(id: Int): User =
        userRepo.findById(id)
            .orElseThrow { EntityNotFoundException("사용자", id) }
}
