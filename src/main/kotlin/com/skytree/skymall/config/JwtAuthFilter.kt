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
 * JWT 인증 필터
 *
 * Authorization: Bearer {token} 헤더에서 토큰을 추출하고
 * 유효하면 SecurityContext에 인증 정보를 설정한다.
 */
@Component
class JwtAuthFilter(
    private val jwtProvider: JwtProvider
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val token = extractToken(request)

        if (token != null && jwtProvider.validateToken(token)) {
            val userId = jwtProvider.getUserId(token)
            val username = jwtProvider.getUsername(token)
            val role = jwtProvider.getRole(token)

            val auth = UsernamePasswordAuthenticationToken(
                userId,        // principal = userId
                null,
                listOf(SimpleGrantedAuthority("ROLE_$role"))
            )
            // username을 details에 보관
            auth.details = username
            SecurityContextHolder.getContext().authentication = auth
        }

        filterChain.doFilter(request, response)
    }

    private fun extractToken(request: HttpServletRequest): String? {
        val header = request.getHeader("Authorization") ?: return null
        return if (header.startsWith("Bearer ")) header.substring(7) else null
    }
}
