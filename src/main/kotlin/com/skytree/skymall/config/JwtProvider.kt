package com.skytree.skymall.config

import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.*
import javax.crypto.SecretKey

/**
 * ================================================================
 * JWT 토큰 생성 및 검증 (JwtProvider)
 * ================================================================
 *
 * JWT(JSON Web Token)는 서버가 세션을 유지하지 않는 Stateless 인증 방식이다.
 *
 * ── JWT 구조 ──
 *
 * JWT는 점(.)으로 구분된 3개 파트로 구성된다:
 *
 *   eyJhbGciOiJIUzM4NCJ9.eyJzdWIiOiIxIiwidXNl...  .R3UhTiIJSVTY9I9v46Plm...
 *   ├─ Header ─┤          ├──── Payload ────┤       ├──── Signature ────┤
 *
 * Header:  서명 알고리즘 (HS384 등)
 * Payload: 사용자 정보 (Claims) — userId, username, role, type 등
 * Signature: Header + Payload를 비밀키로 서명한 값 (위변조 방지)
 *
 * ── Access Token vs Refresh Token ──
 *
 * Access Token (15분):
 *   - API 호출 시 사용 (Authorization: Bearer {token})
 *   - 짧은 수명 → 탈취되어도 피해가 제한적
 *   - type claim: "access"
 *
 * Refresh Token (7일):
 *   - Access Token이 만료되면 새 토큰 쌍을 발급받는 데 사용
 *   - API 직접 접근 불가 (JwtAuthFilter에서 차단)
 *   - type claim: "refresh"
 *
 * ── jti (JWT ID) ──
 * 모든 토큰에 UUID를 넣어 동일 시점에 생성되어도 고유한 토큰을 보장한다.
 * Refresh Token Rotation에서 이전 토큰과 새 토큰을 구분하는 데 필수적이다.
 *
 * ── @Value 어노테이션 ──
 * application.yaml의 설정값을 필드에 주입한다.
 * "${jwt.secret:기본값}" — jwt.secret 값이 없으면 기본값을 사용
 *
 * ── @Component ──
 * 이 클래스를 스프링 빈으로 등록한다.
 * 다른 클래스에서 생성자 주입으로 사용할 수 있다.
 */
@Component
class JwtProvider(
    /** JWT 서명에 사용할 비밀키 (HMAC-SHA 알고리즘) */
    @Value("\${jwt.secret:skymall-secret-key-must-be-at-least-32-bytes-long}")
    private val secret: String,

    /** Access Token 만료 시간 (밀리초) — 기본 15분 */
    @Value("\${jwt.access-expiration:900000}")
    private val accessExpirationMs: Long,

    /** Refresh Token 만료 시간 (밀리초) — 기본 7일 */
    @Value("\${jwt.refresh-expiration:604800000}")
    private val refreshExpirationMs: Long
) {
    /**
     * HMAC-SHA 서명키 객체
     *
     * by lazy: 처음 사용할 때 한 번만 생성 (지연 초기화)
     * Keys.hmacShaKeyFor(): 문자열을 HMAC 서명키로 변환
     * 비밀키는 최소 32바이트 이상이어야 한다 (256비트).
     */
    private val key: SecretKey by lazy {
        Keys.hmacShaKeyFor(secret.toByteArray())
    }

    /**
     * Access Token 생성
     * API 호출 시 인증에 사용되는 짧은 수명의 토큰
     */
    fun generateAccessToken(userId: Int, username: String, role: String): String =
        buildToken(userId, username, role, "access", accessExpirationMs)

    /**
     * Refresh Token 생성
     * Access Token 갱신에 사용되는 긴 수명의 토큰
     */
    fun generateRefreshToken(userId: Int, username: String, role: String): String =
        buildToken(userId, username, role, "refresh", refreshExpirationMs)

    /** Refresh Token 만료 시간 getter (AuthService에서 DB 저장 시 사용) */
    fun getRefreshExpirationMs(): Long = refreshExpirationMs

    /**
     * JWT 토큰 빌드 (내부 메서드)
     *
     * JWT의 Payload에 담기는 정보 (Claims):
     * - jti:      고유 ID (UUID) — 토큰 식별용
     * - sub:      subject = userId (JWT 표준)
     * - username: 사용자명
     * - role:     권한 역할
     * - type:     "access" 또는 "refresh"
     * - iat:      issued at = 발급 시각 (JWT 표준)
     * - exp:      expiration = 만료 시각 (JWT 표준)
     *
     * signWith(key): 비밀키로 서명 → 위변조 감지 가능
     * compact(): 최종 JWT 문자열 생성
     */
    private fun buildToken(userId: Int, username: String, role: String, type: String, expirationMs: Long): String =
        Jwts.builder()
            .id(UUID.randomUUID().toString())                          // jti: 고유 토큰 ID
            .subject(userId.toString())                                // sub: 사용자 ID
            .claim("username", username)                               // 커스텀 클레임: 사용자명
            .claim("role", role)                                       // 커스텀 클레임: 역할
            .claim("type", type)                                       // 커스텀 클레임: 토큰 종류
            .issuedAt(Date())                                          // iat: 발급 시각
            .expiration(Date(System.currentTimeMillis() + expirationMs)) // exp: 만료 시각
            .signWith(key)                                             // 비밀키로 서명
            .compact()                                                 // JWT 문자열 생성

    /**
     * 토큰 유효성 검증
     *
     * getClaims()가 성공하면 유효, 예외 발생하면 무효.
     * 검증 항목:
     * 1. 서명이 올바른가 (위변조 확인)
     * 2. 만료 시각이 지나지 않았는가
     * 3. JWT 형식이 올바른가
     */
    fun validateToken(token: String): Boolean =
        try {
            getClaims(token)
            true
        } catch (e: Exception) {
            false
        }

    /** 토큰에서 사용자 ID 추출 (sub 클레임) */
    fun getUserId(token: String): Int =
        getClaims(token).subject.toInt()

    /** 토큰에서 사용자명 추출 */
    fun getUsername(token: String): String =
        getClaims(token)["username"] as String

    /** 토큰에서 역할 추출 */
    fun getRole(token: String): String =
        getClaims(token)["role"] as String

    /**
     * 토큰 종류 추출 ("access" 또는 "refresh")
     * type 클레임이 없는 이전 버전 토큰은 "access"로 간주한다.
     */
    fun getTokenType(token: String): String =
        getClaims(token)["type"] as? String ?: "access"

    /**
     * JWT 파싱 — Claims(페이로드) 추출
     *
     * 이 메서드가 성공하면 토큰이 유효하다는 뜻이다.
     * 서명 검증 + 만료 검사가 여기서 한 번에 수행된다.
     *
     * verifyWith(key): 서명을 검증할 키 지정
     * parseSignedClaims(): 서명된 JWT를 파싱하여 Claims 반환
     *
     * 실패 시 예외:
     * - ExpiredJwtException: 만료됨
     * - SignatureException: 서명 불일치
     * - MalformedJwtException: 형식 오류
     */
    private fun getClaims(token: String): Claims =
        Jwts.parser().verifyWith(key).build()
            .parseSignedClaims(token).payload
}
