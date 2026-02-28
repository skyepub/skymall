package com.skytree.skymall.config

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("JwtProvider 단위 테스트")
class JwtProviderTest {

    private lateinit var jwtProvider: JwtProvider

    @BeforeEach
    fun setUp() {
        jwtProvider = JwtProvider(
            secret = "skymall-test-secret-key-must-be-at-least-32-bytes-long",
            accessExpirationMs = 900_000,    // 15분
            refreshExpirationMs = 604_800_000 // 7일
        )
    }

    @Test
    @DisplayName("Access Token 생성 및 검증")
    fun generateAccessToken() {
        val token = jwtProvider.generateAccessToken(1, "testuser", "USER")

        assertTrue(jwtProvider.validateToken(token))
        assertEquals(1, jwtProvider.getUserId(token))
        assertEquals("testuser", jwtProvider.getUsername(token))
        assertEquals("USER", jwtProvider.getRole(token))
        assertEquals("access", jwtProvider.getTokenType(token))
    }

    @Test
    @DisplayName("Refresh Token 생성 및 검증")
    fun generateRefreshToken() {
        val token = jwtProvider.generateRefreshToken(1, "testuser", "USER")

        assertTrue(jwtProvider.validateToken(token))
        assertEquals("refresh", jwtProvider.getTokenType(token))
        assertEquals(1, jwtProvider.getUserId(token))
    }

    @Test
    @DisplayName("Access Token과 Refresh Token의 type이 다르다")
    fun tokenTypesAreDifferent() {
        val access = jwtProvider.generateAccessToken(1, "user", "USER")
        val refresh = jwtProvider.generateRefreshToken(1, "user", "USER")

        assertEquals("access", jwtProvider.getTokenType(access))
        assertEquals("refresh", jwtProvider.getTokenType(refresh))
        assertNotEquals(access, refresh)
    }

    @Test
    @DisplayName("동일 파라미터로 생성해도 jti 덕분에 항상 다른 토큰")
    fun tokensAreUniqueByJti() {
        val token1 = jwtProvider.generateRefreshToken(1, "user", "USER")
        val token2 = jwtProvider.generateRefreshToken(1, "user", "USER")

        assertNotEquals(token1, token2)
    }

    @Test
    @DisplayName("잘못된 토큰은 검증 실패")
    fun invalidTokenFails() {
        assertFalse(jwtProvider.validateToken("invalid.token.here"))
        assertFalse(jwtProvider.validateToken(""))
    }

    @Test
    @DisplayName("다른 시크릿으로 서명된 토큰은 검증 실패")
    fun differentSecretFails() {
        val otherProvider = JwtProvider(
            secret = "another-secret-key-that-is-at-least-32-bytes-long!",
            accessExpirationMs = 900_000,
            refreshExpirationMs = 604_800_000
        )
        val token = otherProvider.generateAccessToken(1, "user", "USER")

        assertFalse(jwtProvider.validateToken(token))
    }

    @Test
    @DisplayName("만료된 토큰은 검증 실패")
    fun expiredTokenFails() {
        val shortLivedProvider = JwtProvider(
            secret = "skymall-test-secret-key-must-be-at-least-32-bytes-long",
            accessExpirationMs = -1000, // 이미 만료
            refreshExpirationMs = 604_800_000
        )
        val token = shortLivedProvider.generateAccessToken(1, "user", "USER")

        assertFalse(jwtProvider.validateToken(token))
    }
}
