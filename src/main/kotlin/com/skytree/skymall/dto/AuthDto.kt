package com.skytree.skymall.dto

import com.skytree.skymall.entity.UserRole

/**
 * ================================================================
 * 인증 관련 DTO (Data Transfer Object)
 * ================================================================
 *
 * DTO는 계층 간 데이터를 전달하는 객체이다.
 * Controller ↔ Service ↔ Client 사이에서 데이터를 주고받을 때 사용한다.
 *
 * ── 왜 Entity를 직접 반환하지 않는가? ──
 *
 * 1. 보안: Entity에는 password 같은 민감 정보가 있다. DTO로 필요한 필드만 노출.
 * 2. 순환 참조: Entity의 양방향 관계가 JSON 직렬화 시 무한 루프를 유발할 수 있다.
 * 3. API 안정성: DB 스키마가 변경되어도 API 응답 구조를 유지할 수 있다.
 * 4. 관심사 분리: Entity는 DB 매핑, DTO는 API 통신 — 각각의 역할이 다르다.
 *
 * ── Kotlin data class ──
 * data class는 equals(), hashCode(), toString(), copy()를 자동 생성한다.
 * 불변(val) 필드를 사용하여 DTO의 데이터가 변경되지 않도록 보장한다.
 */

/**
 * 로그인 요청 DTO
 * 클라이언트 → 서버
 *
 * JSON 예시:
 * {
 *   "username": "john",
 *   "password": "mypassword123"
 * }
 */
data class LoginRequest(
    val username: String,  // 사용자명
    val password: String   // 비밀번호 (평문 — 서버에서 BCrypt로 비교)
)

/**
 * 회원가입 요청 DTO
 *
 * JSON 예시:
 * {
 *   "username": "john",
 *   "email": "john@example.com",
 *   "password": "mypassword123",
 *   "role": "USER"           ← 생략 시 기본값 USER
 * }
 */
data class RegisterRequest(
    val username: String,
    val email: String,
    val password: String,
    val role: UserRole = UserRole.USER  // 기본값: USER (Kotlin 기본 인자)
)

/**
 * 인증 성공 응답 DTO
 * 서버 → 클라이언트
 *
 * 로그인/회원가입 성공 시 반환된다.
 * Access Token과 Refresh Token을 모두 포함한다.
 *
 * JSON 예시:
 * {
 *   "accessToken": "eyJhbGciOiJIUzM4NCJ9...",     ← API 호출에 사용 (15분)
 *   "refreshToken": "eyJhbGciOiJIUzM4NCJ9...",    ← 토큰 갱신에 사용 (7일)
 *   "userId": 1,
 *   "username": "john",
 *   "role": "USER"
 * }
 *
 * 클라이언트는 accessToken을 "Authorization: Bearer {토큰}" 헤더로 전송한다.
 */
data class TokenResponse(
    val accessToken: String,    // API 호출용 (짧은 수명)
    val refreshToken: String,   // 토큰 갱신용 (긴 수명)
    val userId: Int,
    val username: String,
    val role: UserRole
)

/**
 * 토큰 갱신 요청 DTO
 *
 * Access Token이 만료되었을 때, Refresh Token으로 새 토큰 쌍을 요청한다.
 *
 * JSON 예시:
 * {
 *   "refreshToken": "eyJhbGciOiJIUzM4NCJ9..."
 * }
 */
data class RefreshRequest(
    val refreshToken: String   // 이전에 발급받은 Refresh Token
)
