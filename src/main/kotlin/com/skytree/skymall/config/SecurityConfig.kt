package com.skytree.skymall.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

/**
 * ================================================================
 * Spring Security 설정 (SecurityConfig)
 * ================================================================
 *
 * 이 클래스에서 애플리케이션의 전체 보안 정책을 정의한다:
 * - 어떤 URL이 공개이고, 어떤 URL이 인증이 필요한지
 * - 어떤 역할이 어떤 URL에 접근할 수 있는지
 * - JWT 필터를 어디에 배치할지
 *
 * ── @Configuration ──
 * 이 클래스가 스프링 설정 클래스임을 선언한다.
 * @Bean 메서드의 반환값이 스프링 빈으로 등록된다.
 *
 * ── @EnableWebSecurity ──
 * Spring Security의 웹 보안 기능을 활성화한다.
 * 이 어노테이션이 없으면 Spring Security가 동작하지 않는다.
 *
 * ── SecurityFilterChain ──
 * Spring Security 5.x부터 도입된 방식.
 * 이전에는 WebSecurityConfigurerAdapter를 상속했지만, 현재는 @Bean 방식을 사용한다.
 *
 * ── 요청 처리 흐름 ──
 *
 *   HTTP 요청
 *     → [CSRF 필터 (비활성화)]
 *     → [JwtAuthFilter] ← 토큰 검증, SecurityContext 설정
 *     → [AuthorizationFilter] ← URL별 권한 검사
 *     → [Controller]
 */
@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val jwtAuthFilter: JwtAuthFilter
) {
    /**
     * Security 필터 체인 설정
     *
     * HttpSecurity를 사용하여 보안 규칙을 체인 형태로 정의한다.
     */
    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain =
        http
            // ── CSRF 비활성화 ──
            // CSRF(Cross-Site Request Forgery) 방지 토큰은
            // 세션 기반 인증에서 사용한다. JWT는 Stateless이므로 불필요.
            .csrf { it.disable() }

            // ── 세션 비활성화 ──
            // STATELESS: 서버가 세션을 생성하지 않는다.
            // JWT 방식에서는 서버에 상태를 저장하지 않으므로 세션이 불필요하다.
            // 이 설정이 없으면 Spring Security가 기본적으로 세션을 생성한다.
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }

            // ── URL별 접근 권한 설정 ──
            // 규칙은 위에서 아래로 평가되며, 먼저 매칭되는 규칙이 적용된다!
            // 따라서 구체적인 규칙을 먼저, 넓은 규칙을 나중에 선언해야 한다.
            .authorizeHttpRequests { auth ->
                auth
                    // ── 인증(Auth) 관련 ──

                    // 로그아웃은 인증 필요 (Access Token으로 사용자 식별)
                    // /api/auth/** (permitAll) 보다 먼저 선언해야 이 규칙이 우선 적용된다!
                    .requestMatchers("/api/auth/logout").authenticated()

                    // 로그인, 회원가입, 토큰 갱신은 공개 (인증 불필요)
                    .requestMatchers("/api/auth/**").permitAll()

                    // ── 상품/카테고리 ──

                    // GET (조회)은 공개 — 비회원도 상품을 볼 수 있어야 한다
                    .requestMatchers(HttpMethod.GET, "/api/products/**").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/categories/**").permitAll()

                    // POST, PATCH, DELETE (생성/수정/삭제)는 ADMIN, MANAGER만
                    .requestMatchers(HttpMethod.POST, "/api/products/**").hasAnyRole("ADMIN", "MANAGER")
                    .requestMatchers(HttpMethod.PATCH, "/api/products/**").hasAnyRole("ADMIN", "MANAGER")
                    .requestMatchers(HttpMethod.DELETE, "/api/products/**").hasAnyRole("ADMIN", "MANAGER")
                    .requestMatchers(HttpMethod.POST, "/api/categories/**").hasAnyRole("ADMIN", "MANAGER")
                    .requestMatchers(HttpMethod.DELETE, "/api/categories/**").hasAnyRole("ADMIN", "MANAGER")

                    // ── 사용자 관리 ──

                    // 삭제는 ADMIN만 가능
                    .requestMatchers(HttpMethod.DELETE, "/api/users/**").hasRole("ADMIN")
                    // 나머지 (목록, 조회, 수정)는 ADMIN, MANAGER
                    .requestMatchers("/api/users/**").hasAnyRole("ADMIN", "MANAGER")

                    // ── 매출 리포트 ──
                    .requestMatchers("/api/orders/report").hasAnyRole("ADMIN", "MANAGER")

                    // ── 나머지 모든 요청 ──
                    // 인증된 사용자만 접근 가능 (주문 등)
                    .anyRequest().authenticated()
            }

            // ── JWT 필터 등록 ──
            // JwtAuthFilter를 UsernamePasswordAuthenticationFilter 앞에 배치한다.
            // → Spring Security의 기본 폼 로그인 필터 대신 JWT 필터가 먼저 실행된다.
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter::class.java)
            .build()

    /**
     * 비밀번호 인코더 빈 등록
     *
     * BCryptPasswordEncoder: 비밀번호를 BCrypt 해시 알고리즘으로 암호화한다.
     * - 회원가입 시: passwordEncoder.encode("평문비밀번호") → "$2a$10$..."
     * - 로그인 시:  passwordEncoder.matches("입력비밀번호", "저장된해시") → true/false
     *
     * BCrypt의 특징:
     * - 같은 비밀번호도 매번 다른 해시가 생성된다 (salt 내장)
     * - 단방향 해시: 해시에서 원본 비밀번호를 복원할 수 없다
     * - 계산 비용 조절 가능 (기본 strength=10, 높을수록 느리지만 안전)
     */
    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()
}
