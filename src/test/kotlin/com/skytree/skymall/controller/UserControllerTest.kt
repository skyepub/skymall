package com.skytree.skymall.controller

import org.junit.jupiter.api.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import tools.jackson.databind.ObjectMapper

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("User API 통합 테스트")
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UserControllerTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var objectMapper: ObjectMapper

    private var adminToken: String = ""
    private var targetUserId: Int = 0
    private val suffix = System.currentTimeMillis()
    private val targetUsername = "usrtarget_$suffix"

    @BeforeAll
    fun setup() {
        // ADMIN 계정
        val adminReg = mockMvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"username":"usradmin_$suffix","email":"usradmin_$suffix@test.com","password":"pass1234","role":"ADMIN"}""")
        ).andReturn()
        adminToken = objectMapper.readTree(adminReg.response.contentAsByteArray)["accessToken"].asText()

        // 테스트 대상 USER 계정
        val userReg = mockMvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"username":"$targetUsername","email":"$targetUsername@test.com","password":"pass1234"}""")
        ).andReturn()
        targetUserId = objectMapper.readTree(userReg.response.contentAsByteArray)["userId"].asInt()
    }

    @Test
    @Order(1)
    @DisplayName("사용자 목록 조회 (ADMIN) → 200 + 페이징")
    fun getUsers() {
        mockMvc.perform(
            get("/api/users")
                .header("Authorization", "Bearer $adminToken")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content").isArray)
            .andExpect(jsonPath("$.totalElements").isNumber)
    }

    @Test
    @Order(2)
    @DisplayName("사용자 단건 조회 (ADMIN) → 200")
    fun getUserById() {
        mockMvc.perform(
            get("/api/users/$targetUserId")
                .header("Authorization", "Bearer $adminToken")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(targetUserId))
            .andExpect(jsonPath("$.username").value(targetUsername))
    }

    @Test
    @Order(3)
    @DisplayName("username으로 사용자 조회 (ADMIN) → 200")
    fun getUserByUsername() {
        mockMvc.perform(
            get("/api/users/username/$targetUsername")
                .header("Authorization", "Bearer $adminToken")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.username").value(targetUsername))
    }

    @Test
    @Order(4)
    @DisplayName("사용자 프로필 조회 (ADMIN) → 200")
    fun getUserProfile() {
        mockMvc.perform(
            get("/api/users/$targetUserId/profile")
                .header("Authorization", "Bearer $adminToken")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.user.id").value(targetUserId))
            .andExpect(jsonPath("$.orderCount").value(0))
            .andExpect(jsonPath("$.totalSpent").value(0.0))
    }

    @Test
    @Order(5)
    @DisplayName("사용자 수정 (ADMIN) → 200")
    fun updateUser() {
        mockMvc.perform(
            patch("/api/users/$targetUserId")
                .header("Authorization", "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"alias":"테스트별명","email":"newemail_$suffix@test.com"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.alias").value("테스트별명"))
            .andExpect(jsonPath("$.email").value("newemail_$suffix@test.com"))
    }

    @Test
    @Order(6)
    @DisplayName("존재하지 않는 사용자 조회 → 404")
    fun getUserNotFound() {
        mockMvc.perform(
            get("/api/users/999999")
                .header("Authorization", "Bearer $adminToken")
        ).andExpect(status().isNotFound)
    }

    @Test
    @Order(7)
    @DisplayName("인증 없이 사용자 API → 403")
    fun usersRequireAuth() {
        mockMvc.perform(get("/api/users"))
            .andExpect(status().isForbidden)
    }

    @Test
    @Order(90)
    @DisplayName("사용자 삭제 (ADMIN) → 204")
    fun deleteUser() {
        mockMvc.perform(
            delete("/api/users/$targetUserId")
                .header("Authorization", "Bearer $adminToken")
        ).andExpect(status().isNoContent)
    }

    @Test
    @Order(91)
    @DisplayName("삭제된 사용자 조회 → 404")
    fun deletedUserNotFound() {
        mockMvc.perform(
            get("/api/users/$targetUserId")
                .header("Authorization", "Bearer $adminToken")
        ).andExpect(status().isNotFound)
    }
}
