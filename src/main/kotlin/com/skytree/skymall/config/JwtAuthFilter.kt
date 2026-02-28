package com.skytree.skymall.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * ================================================================
 * JWT 인증 필터 (JwtAuthFilter)
 * ================================================================
 *
 * 모든 HTTP 요청에 대해 JWT 토큰을 검사하는 서블릿 필터.
 *
 * ── 동작 흐름 ──
 *
 *   클라이언트 요청
 *     → [JwtAuthFilter] ← 여기서 토큰 검사!
 *       → [SecurityConfig의 권한 검사]
 *         → [Controller]
 *
 * 1. HTTP 요청에서 "Authorization: Bearer {토큰}" 헤더를 추출
 * 2. JwtProvider로 토큰 유효성 검증
 * 3. 토큰 타입이 "access"인지 확인 (refresh 토큰으로 API 접근 차단)
 * 4. 유효하면 → SecurityContext에 인증 정보 설정 → 컨트롤러로 전달
 * 5. 무효하면 → 인증 정보 없이 전달 → SecurityConfig에서 403/401 반환
 *
 * ── OncePerRequestFilter ──
 * 요청당 한 번만 실행되는 필터.
 * 일반 Filter는 forward/redirect 시 여러 번 실행될 수 있지만,
 * OncePerRequestFilter는 한 번만 실행이 보장된다.
 *
 * ── SecurityContextHolder ──
 * Spring Security의 인증 정보 저장소.
 * ThreadLocal 기반이므로 현재 스레드(요청)에서만 유효하다.
 *
 *   SecurityContextHolder
 *     └─ SecurityContext
 *          └─ Authentication (인증 객체)
 *               ├─ principal:   userId (Int)        ← 컨트롤러에서 사용
 *               ├─ credentials: null (비밀번호 불필요)
 *               ├─ authorities: [ROLE_USER] 등       ← 권한 검사에 사용
 *               └─ details:     username             ← 추가 정보
 */
@Component
class JwtAuthFilter(
    private val jwtProvider: JwtProvider
) : OncePerRequestFilter() {

    /**
     * 필터 핵심 로직 — 모든 HTTP 요청마다 실행된다
     *
     * @param request   HTTP 요청 객체
     * @param response  HTTP 응답 객체
     * @param filterChain 다음 필터로 전달하는 체인
     */
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        // 1. "Authorization: Bearer xxx" 헤더에서 토큰 추출
        val token = extractToken(request)

        // 2. 토큰 검증: 유효 + access 타입만 허용
        //    - validateToken(): 서명 + 만료 검증
        //    - getTokenType(): "access"만 API 접근 가능 (refresh 토큰 차단)
        if (token != null && jwtProvider.validateToken(token) && jwtProvider.getTokenType(token) == "access") {
            // 3. 토큰에서 사용자 정보 추출
            val userId = jwtProvider.getUserId(token)
            val username = jwtProvider.getUsername(token)
            val role = jwtProvider.getRole(token)

            // 4. Spring Security 인증 객체 생성
            //    UsernamePasswordAuthenticationToken(principal, credentials, authorities)
            //    - principal: 주체 = userId (컨트롤러에서 authentication.principal로 접근)
            //    - credentials: null (JWT 방식에서는 비밀번호 불필요)
            //    - authorities: 권한 목록 (ROLE_ 접두사 필수)
            val auth = UsernamePasswordAuthenticationToken(
                userId,        // principal = userId (Int)
                null,          // credentials (JWT에서는 불필요)
                listOf(SimpleGrantedAuthority("ROLE_$role"))  // 예: ROLE_USER, ROLE_ADMIN
            )

            // username을 details에 보관 (필요 시 사용)
            auth.details = username

            // 5. SecurityContext에 인증 정보 저장
            //    이후 @PreAuthorize, hasRole() 등에서 이 정보를 사용한다.
            SecurityContextHolder.getContext().authentication = auth
        }

        // 6. 다음 필터(또는 컨트롤러)로 요청 전달
        //    인증 정보가 없으면 SecurityConfig의 권한 규칙에 따라 401/403 반환
        filterChain.doFilter(request, response)
    }

    /**
     * Authorization 헤더에서 Bearer 토큰 추출
     *
     * 헤더 형식: "Authorization: Bearer eyJhbGciOiJIUzM4NCJ9..."
     * "Bearer " (7자)를 제거하고 순수 JWT 문자열만 반환한다.
     *
     * @return JWT 토큰 문자열, 또는 null (헤더가 없거나 형식이 다를 때)
     */
    private fun extractToken(request: HttpServletRequest): String? {
        val header = request.getHeader("Authorization") ?: return null
        return if (header.startsWith("Bearer ")) header.substring(7) else null
    }
}
