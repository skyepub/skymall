package com.skytree.skymall.controller

import com.skytree.skymall.dto.LoginRequest
import com.skytree.skymall.dto.RefreshRequest
import com.skytree.skymall.dto.RegisterRequest
import com.skytree.skymall.dto.TokenResponse
import com.skytree.skymall.service.AuthService
import org.springframework.http.HttpStatus
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*

/**
 * ================================================================
 * 인증 컨트롤러 (AuthController)
 * ================================================================
 *
 * JWT 기반 인증 API 엔드포인트를 정의한다.
 *
 * ── 엔드포인트 요약 ──
 *
 * POST /api/auth/register  — 회원가입 (공개)
 * POST /api/auth/login     — 로그인 (공개)
 * POST /api/auth/refresh   — 토큰 갱신 (공개, Refresh Token 필요)
 * POST /api/auth/logout    — 로그아웃 (인증 필요, Access Token 필요)
 *
 * ── @RestController ──
 * @Controller + @ResponseBody의 합성 어노테이션.
 * - @Controller: 이 클래스가 HTTP 요청을 처리하는 컨트롤러임을 선언
 * - @ResponseBody: 반환값을 JSON으로 직렬화하여 HTTP 응답 본문에 포함
 *
 * 일반 @Controller는 View(HTML)를 반환하지만,
 * @RestController는 데이터(JSON)를 직접 반환한다.
 *
 * ── @RequestMapping("/api/auth") ──
 * 이 컨트롤러의 모든 엔드포인트에 공통 URL 접두사를 지정한다.
 * 예: @PostMapping("/login") → 실제 URL: POST /api/auth/login
 *
 * ── 생성자 주입 (Constructor Injection) ──
 * AuthService를 생성자 파라미터로 선언하면 Spring이 자동으로 주입한다.
 * Kotlin에서는 @Autowired 어노테이션이 필요 없다 (단일 생성자인 경우).
 *
 * ── Controller 계층의 역할 ──
 * 1. HTTP 요청 수신 (URL 매핑, 파라미터 바인딩)
 * 2. Service 호출 (비즈니스 로직은 Service에 위임)
 * 3. HTTP 응답 반환 (상태 코드, JSON 본문)
 *
 * Controller에는 비즈니스 로직을 넣지 않는다!
 * 단순히 요청을 받아서 Service에 전달하고 결과를 반환하는 "얇은 계층"이다.
 */
@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authService: AuthService
) {
    /**
     * 회원가입 — POST /api/auth/register
     *
     * @RequestBody: HTTP 요청의 JSON 본문을 RegisterRequest 객체로 변환
     *   Spring의 Jackson이 자동으로 JSON -> Kotlin 객체 역직렬화를 수행한다.
     *   예: {"username":"john","email":"john@test.com","password":"1234"} -> RegisterRequest
     *
     * @ResponseStatus(HttpStatus.CREATED): 성공 시 201 Created 반환
     *   기본값은 200 OK이지만, 리소스 생성은 201이 RESTful 관례이다.
     *
     * 반환: TokenResponse (accessToken + refreshToken + 사용자 정보)
     */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    fun register(@RequestBody req: RegisterRequest): TokenResponse =
        authService.register(req)

    /**
     * 로그인 — POST /api/auth/login
     *
     * 성공 시 200 OK + TokenResponse 반환
     * 실패 시 400 Bad Request + ErrorResponse (GlobalExceptionHandler 처리)
     */
    @PostMapping("/login")
    fun login(@RequestBody req: LoginRequest): TokenResponse =
        authService.login(req)

    /**
     * 토큰 갱신 — POST /api/auth/refresh
     *
     * Access Token이 만료되었을 때 Refresh Token으로 새 토큰 쌍을 발급받는다.
     * 이 엔드포인트는 공개(permitAll)이므로 Authorization 헤더가 필요 없다.
     * (만료된 Access Token으로는 인증 자체가 불가능하므로)
     *
     * 성공: 200 OK + 새 TokenResponse
     * 실패: 400 Bad Request (잘못된/만료된/이미 사용된 Refresh Token)
     */
    @PostMapping("/refresh")
    fun refresh(@RequestBody req: RefreshRequest): TokenResponse =
        authService.refresh(req)

    /**
     * 로그아웃 — POST /api/auth/logout
     *
     * SecurityConfig에서 이 URL만 .authenticated()로 설정했다.
     * (나머지 /api/auth/ 는 permitAll)
     * -> Access Token이 필요하다 (Authorization: Bearer {accessToken})
     *
     * @ResponseStatus(HttpStatus.NO_CONTENT): 204 No Content
     *   응답 본문이 없을 때 사용하는 HTTP 상태 코드.
     *   삭제/로그아웃처럼 반환할 데이터가 없는 경우에 적합하다.
     *
     * SecurityContextHolder에서 현재 인증된 사용자의 userId를 추출한다.
     * JwtAuthFilter에서 authentication.principal = userId로 설정했으므로
     * 여기서 그대로 꺼내 사용한다.
     */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun logout() {
        // SecurityContext에서 현재 인증된 사용자의 userId 추출
        // JwtAuthFilter에서 principal = userId (Int)로 설정했음
        val userId = SecurityContextHolder.getContext().authentication!!.principal as Int
        authService.logout(userId)
    }
}
