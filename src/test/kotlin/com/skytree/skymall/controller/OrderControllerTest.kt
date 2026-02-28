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
@DisplayName("Order API 통합 테스트")
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OrderControllerTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var objectMapper: ObjectMapper

    private var adminToken: String = ""
    private var userToken: String = ""
    private var userId: Int = 0
    private var productId: Int = 0
    private var orderId: Int = 0
    private val suffix = System.currentTimeMillis()

    @BeforeAll
    fun setup() {
        // ADMIN 계정
        val adminReg = mockMvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"username":"ordadmin_$suffix","email":"ordadmin_$suffix@test.com","password":"pass1234","role":"ADMIN"}""")
        ).andReturn()
        adminToken = objectMapper.readTree(adminReg.response.contentAsByteArray)["accessToken"].asText()

        // USER 계정
        val userReg = mockMvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"username":"orduser_$suffix","email":"orduser_$suffix@test.com","password":"pass1234"}""")
        ).andReturn()
        val userJson = objectMapper.readTree(userReg.response.contentAsByteArray)
        userToken = userJson["accessToken"].asText()
        userId = userJson["userId"].asInt()

        // 테스트 상품 생성
        val prodResult = mockMvc.perform(
            post("/api/products")
                .header("Authorization", "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"OrderTestProd_$suffix","price":5000.0,"stock":100}""")
        ).andReturn()
        productId = objectMapper.readTree(prodResult.response.contentAsByteArray)["id"].asInt()
    }

    @Test
    @Order(1)
    @DisplayName("주문 생성 → 201 + 재고 차감")
    fun createOrder() {
        val result = mockMvc.perform(
            post("/api/orders")
                .header("Authorization", "Bearer $userToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"userId":$userId,"items":[{"productId":$productId,"quantity":3}]}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.totalAmount").value(15000.0))
            .andExpect(jsonPath("$.items").isArray)
            .andExpect(jsonPath("$.items[0].quantity").value(3))
            .andExpect(jsonPath("$.items[0].pricePerItem").value(5000.0))
            .andReturn()

        orderId = objectMapper.readTree(result.response.contentAsByteArray)["id"].asInt()

        // 재고 차감 확인 (100 - 3 = 97)
        mockMvc.perform(get("/api/products/$productId"))
            .andExpect(jsonPath("$.stock").value(97))
    }

    @Test
    @Order(2)
    @DisplayName("재고 초과 주문 → 실패")
    fun createOrderInsufficientStock() {
        mockMvc.perform(
            post("/api/orders")
                .header("Authorization", "Bearer $userToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"userId":$userId,"items":[{"productId":$productId,"quantity":9999}]}""")
        ).andExpect(status().isUnprocessableEntity)
    }

    @Test
    @Order(3)
    @DisplayName("주문 목록 조회 (인증) → 200 + 페이징")
    fun getOrders() {
        mockMvc.perform(
            get("/api/orders")
                .header("Authorization", "Bearer $adminToken")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content").isArray)
            .andExpect(jsonPath("$.totalElements").isNumber)
    }

    @Test
    @Order(4)
    @DisplayName("주문 단건 조회 → 200")
    fun getOrderById() {
        mockMvc.perform(
            get("/api/orders/$orderId")
                .header("Authorization", "Bearer $userToken")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(orderId))
            .andExpect(jsonPath("$.totalAmount").value(15000.0))
    }

    @Test
    @Order(5)
    @DisplayName("내 주문 조회 → 200")
    fun getMyOrders() {
        mockMvc.perform(
            get("/api/orders/my")
                .header("Authorization", "Bearer $userToken")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content").isArray)
            .andExpect(jsonPath("$.content[0].userId").value(userId))
    }

    @Test
    @Order(6)
    @DisplayName("사용자별 주문 조회 → 200")
    fun getOrdersByUser() {
        mockMvc.perform(
            get("/api/orders/user/$userId")
                .header("Authorization", "Bearer $adminToken")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content").isArray)
    }

    @Test
    @Order(7)
    @DisplayName("상품별 주문 조회 → 200")
    fun getOrdersByProduct() {
        mockMvc.perform(
            get("/api/orders/product/$productId")
                .header("Authorization", "Bearer $adminToken")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content").isArray)
    }

    @Test
    @Order(8)
    @DisplayName("고액 주문 조회 → 200")
    fun getHighValueOrders() {
        mockMvc.perform(
            get("/api/orders/high-value")
                .param("minAmount", "10000")
                .header("Authorization", "Bearer $adminToken")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content").isArray)
    }

    @Test
    @Order(9)
    @DisplayName("사용자 주문 요약 → 200")
    fun getUserOrderSummary() {
        mockMvc.perform(
            get("/api/orders/user/$userId/summary")
                .header("Authorization", "Bearer $adminToken")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalOrders").value(1))
            .andExpect(jsonPath("$.totalRevenue").value(15000.0))
    }

    @Test
    @Order(10)
    @DisplayName("매출 리포트 (ADMIN) → 200")
    fun getSalesReport() {
        mockMvc.perform(
            get("/api/orders/report")
                .param("from", "2020-01-01T00:00:00")
                .param("to", "2030-12-31T23:59:59")
                .header("Authorization", "Bearer $adminToken")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.orderCount").isNumber)
            .andExpect(jsonPath("$.totalRevenue").isNumber)
    }

    @Test
    @Order(11)
    @DisplayName("매출 리포트 (USER) → 403 권한 없음")
    fun salesReportForbiddenForUser() {
        mockMvc.perform(
            get("/api/orders/report")
                .param("from", "2020-01-01T00:00:00")
                .param("to", "2030-12-31T23:59:59")
                .header("Authorization", "Bearer $userToken")
        ).andExpect(status().isForbidden)
    }

    @Test
    @Order(12)
    @DisplayName("인증 없이 주문 API → 403")
    fun ordersRequireAuth() {
        mockMvc.perform(get("/api/orders"))
            .andExpect(status().isForbidden)
    }

    @Test
    @Order(90)
    @DisplayName("주문 취소 → 204 + 재고 복원")
    fun cancelOrder() {
        mockMvc.perform(
            delete("/api/orders/$orderId")
                .header("Authorization", "Bearer $adminToken")
        ).andExpect(status().isNoContent)

        // 재고 복원 확인 (97 + 3 = 100)
        mockMvc.perform(get("/api/products/$productId"))
            .andExpect(jsonPath("$.stock").value(100))
    }

    @AfterAll
    fun cleanup() {
        // 테스트 상품 삭제
        mockMvc.perform(
            delete("/api/products/$productId")
                .header("Authorization", "Bearer $adminToken")
        )
    }
}
