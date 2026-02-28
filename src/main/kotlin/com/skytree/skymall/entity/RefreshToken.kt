package com.skytree.skymall.entity

import jakarta.persistence.*
import java.time.LocalDateTime

/**
 * ================================================================
 * 리프레시 토큰 엔티티 (RefreshToken Entity)
 * ================================================================
 *
 * JWT Refresh Token을 DB에 저장하여 관리하는 엔티티.
 *
 * ── 왜 별도 테이블이 필요한가? ──
 *
 * 1. 다중 디바이스 지원:
 *    User 테이블에 refresh_token 컬럼 1개만 두면, 한 기기에서 로그인할 때
 *    다른 기기의 토큰이 덮어써져 로그아웃된다.
 *    별도 테이블이면 기기마다 독립적인 토큰을 가질 수 있다.
 *
 * 2. 개별 토큰 폐기:
 *    특정 기기만 로그아웃시키거나, 의심스러운 세션만 끊을 수 있다.
 *
 * 3. 감사 추적:
 *    언제, 어디서 토큰이 발급되었는지 기록할 수 있다.
 *    (실무에서는 IP, User-Agent 등도 저장한다)
 *
 * ── 보안: 해시 저장 ──
 *
 * Refresh Token 원본을 DB에 저장하면, DB가 탈취될 때
 * 공격자가 토큰을 직접 사용할 수 있다.
 * 따라서 SHA-256 해시값만 저장한다.
 * (비밀번호를 BCrypt로 저장하는 것과 같은 원리)
 *
 * ┌────────────────────────────────────────────────────┐
 * │  refresh_tokens 테이블                               │
 * ├──────────────┬────────────┬──────────────────────────┤
 * │ id (PK)      │ BIGINT     │ 자동 증가                 │
 * │ user_id (FK) │ INT        │ 사용자 외래키              │
 * │ token_hash   │ VARCHAR(255)│ SHA-256 해시값           │
 * │ expires_at   │ DATETIME   │ 만료 시각                 │
 * │ created_at   │ DATETIME   │ 발급 시각                 │
 * │ revoked      │ BOOLEAN    │ 폐기 여부 (기본: false)    │
 * └──────────────┴────────────┴──────────────────────────┘
 *
 * ── 토큰 Rotation (회전) 흐름 ──
 *
 * 1. 로그인 → Refresh Token 발급, 해시를 DB에 저장 (revoked=false)
 * 2. 토큰 갱신 요청 →
 *    a. 기존 토큰의 해시로 DB 조회
 *    b. revoked=false인 레코드 확인
 *    c. 해당 레코드를 revoked=true로 변경 (이전 토큰 폐기)
 *    d. 새 Refresh Token 발급, 새 해시를 DB에 저장
 * 3. 이전 토큰으로 재시도 → revoked=true이므로 거부됨
 */
@Entity
@Table(name = "refresh_tokens")
class RefreshToken(
    /** 기본키 — BIGINT AUTO_INCREMENT */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    /**
     * 토큰 소유자 (다대일 관계)
     * ON DELETE CASCADE: 사용자 삭제 시 관련 토큰도 함께 삭제
     * LAZY: 토큰 조회 시 사용자 정보는 필요할 때만 로딩
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    /** Refresh Token의 SHA-256 해시값 (원본은 저장하지 않음!) */
    @Column(name = "token_hash", nullable = false)
    val tokenHash: String,

    /** 토큰 만료 시각 — 이 시각이 지나면 사용 불가 */
    @Column(name = "expires_at", nullable = false)
    val expiresAt: LocalDateTime,

    /** 토큰 발급 시각 */
    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    /**
     * 폐기 여부
     * true면 이 토큰은 더 이상 사용할 수 없다.
     * Rotation이나 로그아웃 시 true로 변경된다.
     */
    @Column(nullable = false)
    var revoked: Boolean = false
)
