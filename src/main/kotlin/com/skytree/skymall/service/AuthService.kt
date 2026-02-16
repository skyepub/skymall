package com.skytree.skymall.service

import com.skytree.skymall.config.JwtProvider
import com.skytree.skymall.dto.LoginRequest
import com.skytree.skymall.dto.RegisterRequest
import com.skytree.skymall.dto.TokenResponse
import com.skytree.skymall.entity.User
import com.skytree.skymall.exception.BusinessException
import com.skytree.skymall.exception.DuplicateException
import com.skytree.skymall.repository.UserRepository
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
@Transactional(readOnly = true)
class AuthService(
    private val userRepo: UserRepository,
    private val jwtProvider: JwtProvider,
    private val passwordEncoder: PasswordEncoder
) {
    @Transactional
    fun register(req: RegisterRequest): TokenResponse {
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
                password = passwordEncoder.encode(req.password)!!,
                role = req.role
            )
        )
        return toTokenResponse(user)
    }

    @Transactional
    fun login(req: LoginRequest): TokenResponse {
        val user = userRepo.findByUsername(req.username)
            ?: throw BusinessException("잘못된 사용자명 또는 비밀번호입니다")

        if (!user.isEnabled) {
            throw BusinessException("비활성화된 계정입니다")
        }

        if (!passwordEncoder.matches(req.password, user.password)) {
            throw BusinessException("잘못된 사용자명 또는 비밀번호입니다")
        }

        user.lastLoginAt = LocalDateTime.now()
        return toTokenResponse(user)
    }

    private fun toTokenResponse(user: User): TokenResponse {
        val token = jwtProvider.generateToken(user.id, user.username, user.role.name)
        return TokenResponse(
            accessToken = token,
            userId = user.id,
            username = user.username,
            role = user.role
        )
    }
}
