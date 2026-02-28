package com.skytree.skymall.repository

import com.skytree.skymall.entity.User
import com.skytree.skymall.entity.UserRole
import org.springframework.data.jpa.repository.JpaRepository

/**
 * ================================================================
 * 사용자 리포지토리 (UserRepository)
 * ================================================================
 *
 * JPA Repository는 인터페이스만 정의하면 Spring Data JPA가
 * 런타임에 구현체를 자동 생성해준다.
 *
 * ── JpaRepository<User, Int> ──
 *
 * 첫 번째 타입 파라미터: 엔티티 클래스 (User)
 * 두 번째 타입 파라미터: 기본키 타입 (Int = user_id의 타입)
 *
 * JpaRepository가 기본 제공하는 메서드들:
 *   save(entity)          — INSERT 또는 UPDATE (id 유무로 판단)
 *   findById(id)          — SELECT ... WHERE user_id = ?
 *   findAll()             — SELECT * FROM users
 *   findAll(pageable)     — SELECT ... LIMIT ? OFFSET ? (페이징)
 *   deleteById(id)        — DELETE FROM users WHERE user_id = ?
 *   count()               — SELECT COUNT(*) FROM users
 *   existsById(id)        — SELECT EXISTS(...)
 *
 * ── 파생 쿼리 (Derived Query) ──
 *
 * 메서드 이름만으로 SQL이 자동 생성되는 Spring Data의 핵심 기능.
 * "findBy" + "필드명" 패턴으로 WHERE 조건이 만들어진다.
 *
 * 규칙:
 *   findBy{필드명}           → WHERE 필드 = ?
 *   findBy{필드명}And{필드명}  → WHERE 필드1 = ? AND 필드2 = ?
 *   findBy{필드명}OrderBy{필드명}Desc → WHERE ... ORDER BY ... DESC
 *   countBy{필드명}          → SELECT COUNT(*) WHERE ...
 *
 * 반환 타입:
 *   User?                  → 0~1개 결과 (없으면 null)
 *   List<User>             → 0~N개 결과
 *   Page<User>             → 페이징된 결과 (Pageable 파라미터 필요)
 */
interface UserRepository : JpaRepository<User, Int> {

    /**
     * 사용자명으로 조회
     * 생성되는 SQL: SELECT * FROM users WHERE username = ?
     * 반환: 해당 사용자 또는 null
     * 용도: 로그인 시 사용자 검증, 중복 체크
     */
    fun findByUsername(username: String): User?

    /**
     * 이메일로 조회
     * 생성되는 SQL: SELECT * FROM users WHERE email = ?
     * 용도: 회원가입 시 이메일 중복 체크
     */
    fun findByEmail(email: String): User?

    /**
     * 역할별 사용자 목록 조회
     * 생성되는 SQL: SELECT * FROM users WHERE role = ?
     * 예: findByRole(UserRole.ADMIN) → 관리자 목록
     */
    fun findByRole(role: UserRole): List<User>
}
