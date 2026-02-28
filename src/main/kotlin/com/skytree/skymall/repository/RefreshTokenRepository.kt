package com.skytree.skymall.repository

import com.skytree.skymall.entity.RefreshToken
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

/**
 * ================================================================
 * 리프레시 토큰 리포지토리 (RefreshTokenRepository)
 * ================================================================
 *
 * JWT Refresh Token의 저장/조회/폐기를 담당한다.
 *
 * ── 토큰 검증 흐름 ──
 *
 * 1. 클라이언트가 Refresh Token을 전송
 * 2. 서비스에서 SHA-256 해시 계산
 * 3. findByTokenHashAndRevokedFalse()로 유효한 토큰인지 조회
 * 4. 조회 성공 → 새 토큰 발급, 기존 토큰 revoked=true
 * 5. 조회 실패 → 이미 사용된 토큰이므로 거부
 */
interface RefreshTokenRepository : JpaRepository<RefreshToken, Long> {

    /**
     * 해시값으로 유효한(폐기되지 않은) 토큰 조회
     *
     * 파생 쿼리 분해:
     *   findBy          → WHERE
     *   TokenHash       → token_hash = ?
     *   And             → AND
     *   RevokedFalse    → revoked = false
     *
     * SQL: SELECT * FROM refresh_tokens WHERE token_hash = ? AND revoked = false
     */
    fun findByTokenHashAndRevokedFalse(tokenHash: String): RefreshToken?

    /**
     * 특정 사용자의 모든 유효한 토큰을 폐기 (벌크 UPDATE)
     *
     * @Modifying: 이 쿼리가 SELECT가 아닌 UPDATE/DELETE임을 선언
     *   → Spring Data JPA는 기본적으로 모든 @Query를 SELECT로 간주한다.
     *   → UPDATE, DELETE 쿼리에는 반드시 @Modifying을 붙여야 한다.
     *
     * @Query에서 JPQL UPDATE 문법:
     *   UPDATE 엔티티 별칭 SET 필드 = 값 WHERE 조건
     *
     * 용도: 로그아웃 시 해당 사용자의 모든 토큰을 한 번에 무효화
     * → 모든 기기에서 로그아웃되는 효과
     */
    @Modifying
    @Query("UPDATE RefreshToken rt SET rt.revoked = true WHERE rt.user.id = :userId AND rt.revoked = false")
    fun revokeAllByUserId(userId: Int)
}
