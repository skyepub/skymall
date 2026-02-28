package com.skytree.skymall.controller

import com.skytree.skymall.dto.UpdateUserRequest
import com.skytree.skymall.dto.UserProfileResponse
import com.skytree.skymall.dto.UserResponse
import com.skytree.skymall.service.UserService
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

/**
 * ================================================================
 * 사용자 컨트롤러 (UserController)
 * ================================================================
 *
 * 사용자 관리 REST API 엔드포인트.
 *
 * ── 엔드포인트 요약 ──
 *
 * GET    /api/users                    — 전체 사용자 목록 (ADMIN, MANAGER)
 * GET    /api/users/{id}               — 사용자 상세 (ADMIN, MANAGER)
 * GET    /api/users/username/{username} — 이름으로 조회 (ADMIN, MANAGER)
 * GET    /api/users/{id}/profile       — 프로필 + 주문 통계 (ADMIN, MANAGER)
 * PATCH  /api/users/{id}               — 사용자 수정 (ADMIN, MANAGER)
 * DELETE /api/users/{id}               — 사용자 삭제 (ADMIN만)
 *
 * ── 권한 규칙 (SecurityConfig에서 설정) ──
 * DELETE: ADMIN만 가능 (가장 위험한 작업)
 * 나머지: ADMIN + MANAGER
 * 일반 USER는 접근 불가 (자기 정보는 /api/orders/my 등으로 조회)
 *
 * ── 컨트롤러의 얇은 계층 패턴 ──
 *
 * 이 컨트롤러의 모든 메서드는 Service를 호출하고 결과를 반환하는 것뿐이다.
 * 비즈니스 로직(중복 검사, 삭제 가능 여부 등)은 모두 UserService에 있다.
 *
 * 이 패턴의 장점:
 * - Controller 테스트가 단순해진다 (HTTP 레이어만 검증)
 * - 비즈니스 로직을 Service 단위 테스트로 검증할 수 있다
 * - 같은 비즈니스 로직을 다른 Controller에서도 재사용할 수 있다
 */
@RestController
@RequestMapping("/api/users")
class UserController(
    private val userService: UserService
) {
    /**
     * 전체 사용자 목록 — 페이징
     *
     * 반환되는 Page<UserResponse> JSON에는
     * password가 포함되지 않는다 (UserResponse에 없으므로).
     */
    @GetMapping
    fun getAllUsers(
        @PageableDefault(size = 20) pageable: Pageable
    ): Page<UserResponse> = userService.getAllUsers(pageable)

    /** 사용자 ID로 조회 — 없으면 404 */
    @GetMapping("/{id}")
    fun getUser(@PathVariable id: Int): UserResponse =
        userService.getUser(id)

    /**
     * 사용자명으로 조회
     * 예: GET /api/users/username/john
     */
    @GetMapping("/username/{username}")
    fun getUserByUsername(@PathVariable username: String): UserResponse =
        userService.getUserByUsername(username)

    /**
     * 사용자 프로필 (기본 정보 + 주문 통계)
     * UserResponse + 주문 건수/총액/평균을 결합한 복합 응답
     */
    @GetMapping("/{id}/profile")
    fun getUserProfile(@PathVariable id: Int): UserProfileResponse =
        userService.getUserProfile(id)

    /**
     * 사용자 수정 — Partial Update (PATCH)
     * 변경할 필드만 JSON에 포함하면 된다.
     * 예: {"alias": "새별명"} → alias만 변경, 나머지 유지
     */
    @PatchMapping("/{id}")
    fun updateUser(
        @PathVariable id: Int,
        @RequestBody req: UpdateUserRequest
    ): UserResponse = userService.updateUser(id, req)

    /**
     * 사용자 삭제 — ADMIN만 (SecurityConfig에서 설정)
     * 주문 이력이 있으면 삭제 불가 (Service에서 BusinessException)
     * 204 No Content: 삭제 성공, 응답 본문 없음
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteUser(@PathVariable id: Int) =
        userService.deleteUser(id)
}
