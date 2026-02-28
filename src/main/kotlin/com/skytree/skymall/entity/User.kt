package com.skytree.skymall.entity

import jakarta.persistence.*
import java.time.LocalDateTime

/**
 * ================================================================
 * 사용자 역할 열거형 (Enum)
 * ================================================================
 *
 * 시스템 내 사용자 권한을 3단계로 구분한다.
 * - ADMIN:   전체 관리 권한 (사용자 삭제, 매출 리포트 등)
 * - MANAGER: 상품/카테고리 관리 권한
 * - USER:    일반 사용자 (주문, 내 주문 조회)
 *
 * DB에는 @Enumerated(EnumType.STRING)으로 문자열로 저장된다.
 * 예: "ADMIN", "MANAGER", "USER"
 */
enum class UserRole { ADMIN, MANAGER, USER }

/**
 * ================================================================
 * 사용자 엔티티 (User Entity)
 * ================================================================
 *
 * DB의 "users" 테이블과 매핑되는 JPA 엔티티 클래스.
 *
 * ┌──────────────────────────────────────────────────┐
 * │  users 테이블                                      │
 * ├──────────────┬───────────┬────────────────────────┤
 * │ user_id (PK) │ INT       │ 자동 증가               │
 * │ username     │ VARCHAR(50)│ NOT NULL, UNIQUE       │
 * │ email        │ VARCHAR(100)│ NOT NULL, UNIQUE      │
 * │ password     │ VARCHAR   │ NOT NULL (BCrypt 해시)   │
 * │ alias        │ VARCHAR(100)│ 별명 (nullable)       │
 * │ role         │ VARCHAR   │ NOT NULL (ADMIN/MANAGER/USER) │
 * │ is_enabled   │ BOOLEAN   │ NOT NULL (기본: true)    │
 * │ created_at   │ DATETIME  │ 가입 시각               │
 * │ last_login_at│ DATETIME  │ 마지막 로그인 (nullable) │
 * └──────────────┴───────────┴────────────────────────┘
 *
 * ── JPA 핵심 어노테이션 설명 ──
 *
 * @Entity
 *   이 클래스가 JPA 엔티티임을 선언한다.
 *   Hibernate가 이 클래스를 DB 테이블과 매핑한다.
 *
 * @Table(name = "users")
 *   매핑할 테이블 이름을 지정한다.
 *   생략하면 클래스 이름(User)이 테이블 이름이 된다.
 *   "user"는 SQL 예약어이므로 "users"로 지정한다.
 *
 * @Id
 *   이 필드가 테이블의 기본키(Primary Key)임을 선언한다.
 *
 * @GeneratedValue(strategy = GenerationType.IDENTITY)
 *   기본키 생성 전략: DB의 AUTO_INCREMENT를 사용한다.
 *   MySQL에서 가장 일반적인 방식이다.
 *   - IDENTITY: DB가 자동 증가값을 생성 (MySQL)
 *   - SEQUENCE: DB 시퀀스 사용 (Oracle, PostgreSQL)
 *   - TABLE: 키 생성용 별도 테이블 사용
 *   - AUTO: DB에 맞게 자동 선택
 *
 * @Column
 *   컬럼의 세부 설정을 지정한다.
 *   - name: DB 컬럼 이름 (생략하면 필드 이름 사용)
 *   - nullable: NULL 허용 여부 (기본: true)
 *   - unique: 유니크 제약조건
 *   - length: 문자열 최대 길이 (기본: 255)
 *
 * @Enumerated(EnumType.STRING)
 *   Enum을 DB에 저장하는 방식:
 *   - EnumType.STRING:  "ADMIN" 문자열로 저장 (권장)
 *   - EnumType.ORDINAL: 0, 1, 2 숫자로 저장 (비권장 - Enum 순서 변경 시 깨짐)
 *
 * ── val vs var ──
 * - val (읽기 전용): id, createdAt 등 생성 후 변경 불가
 * - var (변경 가능): username, email 등 업데이트 가능
 *   JPA의 dirty checking이 var 필드의 변경을 감지하여 자동으로 UPDATE 쿼리를 실행한다.
 */
@Entity
@Table(name = "users")
class User(
    /** 기본키 — DB에서 AUTO_INCREMENT로 자동 생성 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    val id: Int = 0,

    /** 사용자명 — 로그인에 사용, 중복 불가 */
    @Column(nullable = false, unique = true, length = 50)
    var username: String,

    /** 이메일 — 중복 불가 */
    @Column(nullable = false, unique = true, length = 100)
    var email: String,

    /** 비밀번호 — BCrypt로 해시되어 저장된다 (평문 저장 절대 금지!) */
    @Column(nullable = false)
    var password: String,

    /** 별명 — 선택 사항 (nullable) */
    @Column(length = 100)
    var alias: String? = null,

    /** 사용자 역할 — DB에 "ADMIN", "USER" 등 문자열로 저장 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var role: UserRole = UserRole.USER,

    /**
     * 계정 활성화 여부
     * false로 설정하면 로그인이 차단된다.
     * 회원 탈퇴 대신 비활성화를 사용할 수 있다 (소프트 삭제).
     */
    @Column(name = "is_enabled", nullable = false)
    var isEnabled: Boolean = true,

    /** 가입 시각 — 객체 생성 시 현재 시간으로 자동 설정 */
    @Column(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now(),

    /** 마지막 로그인 시각 — 로그인할 때마다 갱신 */
    @Column(name = "last_login_at")
    var lastLoginAt: LocalDateTime? = null
)
