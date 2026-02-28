package com.skytree.skymall.controller

import com.skytree.skymall.config.JwtProvider
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import tools.jackson.databind.ObjectMapper

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Auth API 통합 테스트")
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class AuthControllerTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var objectMapper: ObjectMapper
    @Autowired lateinit var jwtProvider: JwtProvider

    companion object {
        private var accessToken: String = ""
        private var refreshToken: String = ""
        private val testUsername = "authtest_${System.currentTimeMillis()}"
    }

    @Test
    @Order(1)
    @DisplayName("회원가입 → accessToken + refreshToken 모두 반환")
    fun register() {
        val body = """{"username":"$testUsername","email":"$testUsername@test.com","password":"pass1234"}"""

        val result = mockMvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.accessToken").isNotEmpty)
            .andExpect(jsonPath("$.refreshToken").isNotEmpty)
            .andExpect(jsonPath("$.username").value(testUsername))
            .andReturn()

        val json = objectMapper.readTree(result.response.contentAsByteArray)
        accessToken = json["accessToken"].asText()
        refreshToken = json["refreshToken"].asText()

        assertEquals("access", jwtProvider.getTokenType(accessToken))
        assertEquals("refresh", jwtProvider.getTokenType(refreshToken))
    }

    @Test
    @Order(2)
    @DisplayName("로그인 → accessToken + refreshToken 모두 반환")
    fun login() {
        val body = """{"username":"$testUsername","password":"pass1234"}"""

        val result = mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.accessToken").isNotEmpty)
            .andExpect(jsonPath("$.refreshToken").isNotEmpty)
            .andReturn()

        val json = objectMapper.readTree(result.response.contentAsByteArray)
        accessToken = json["accessToken"].asText()
        refreshToken = json["refreshToken"].asText()
    }

    @Test
    @Order(3)
    @DisplayName("Refresh Token으로 인증 API 호출 → 403 거부")
    fun refreshTokenCannotAccessApi() {
        mockMvc.perform(
            post("/api/auth/logout")
                .header("Authorization", "Bearer $refreshToken")
        )
            .andExpect(status().isForbidden)
    }

    @Test
    @Order(4)
    @DisplayName("토큰 갱신 → 새 토큰 쌍 반환 (Rotation)")
    fun refresh() {
        val body = """{"refreshToken":"$refreshToken"}"""

        val result = mockMvc.perform(
            post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.accessToken").isNotEmpty)
            .andExpect(jsonPath("$.refreshToken").isNotEmpty)
            .andReturn()

        val json = objectMapper.readTree(result.response.contentAsByteArray)
        val newAccess = json["accessToken"].asText()
        val newRefresh = json["refreshToken"].asText()

        assertNotEquals(accessToken, newAccess)
        assertNotEquals(refreshToken, newRefresh)

        accessToken = newAccess
        refreshToken = newRefresh
    }

    @Test
    @Order(5)
    @DisplayName("이전 Refresh Token으로 갱신 → 400 거부 (Rotation)")
    fun oldRefreshTokenRejected() {
        val loginBody = """{"username":"$testUsername","password":"pass1234"}"""
        val loginResult = mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody)
        ).andReturn()

        val loginJson = objectMapper.readTree(loginResult.response.contentAsByteArray)
        val rt = loginJson["refreshToken"].asText()
        accessToken = loginJson["accessToken"].asText()

        // 1차 refresh → 성공
        mockMvc.perform(
            post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"refreshToken":"$rt"}""")
        ).andExpect(status().isOk).andReturn()

        // 2차 refresh (동일 토큰) → Rotation에 의해 거부
        mockMvc.perform(
            post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"refreshToken":"$rt"}""")
        ).andExpect(status().isBadRequest)
    }

    @Test
    @Order(6)
    @DisplayName("로그아웃 → 204, 이후 Refresh 거부")
    fun logoutAndRefreshRejected() {
        val loginBody = """{"username":"$testUsername","password":"pass1234"}"""
        val loginResult = mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody)
        ).andReturn()

        val json = objectMapper.readTree(loginResult.response.contentAsByteArray)
        val at = json["accessToken"].asText()
        val rt = json["refreshToken"].asText()

        // 로그아웃
        mockMvc.perform(
            post("/api/auth/logout")
                .header("Authorization", "Bearer $at")
        ).andExpect(status().isNoContent)

        // 로그아웃 후 refresh → 거부
        mockMvc.perform(
            post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"refreshToken":"$rt"}""")
        ).andExpect(status().isBadRequest)
    }
}
