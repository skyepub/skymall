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
@DisplayName("Catalog API 통합 테스트")
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CatalogControllerTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var objectMapper: ObjectMapper

    private var adminToken: String = ""
    private var userToken: String = ""
    private var testCategoryId: Int = 0
    private var testProductId: Int = 0
    private val suffix = System.currentTimeMillis()

    @BeforeAll
    fun setup() {
        // ADMIN 계정 생성
        val adminReg = mockMvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"username":"catadmin_$suffix","email":"catadmin_$suffix@test.com","password":"pass1234","role":"ADMIN"}""")
        ).andReturn()
        val adminJson = objectMapper.readTree(adminReg.response.contentAsByteArray)
        adminToken = adminJson["accessToken"].asText()

        // USER 계정 생성
        val userReg = mockMvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"username":"catuser_$suffix","email":"catuser_$suffix@test.com","password":"pass1234"}""")
        ).andReturn()
        val userJson = objectMapper.readTree(userReg.response.contentAsByteArray)
        userToken = userJson["accessToken"].asText()
    }

    // ── 카테고리 ──

    @Test
    @Order(1)
    @DisplayName("카테고리 생성 (ADMIN) → 201")
    fun createCategory() {
        val result = mockMvc.perform(
            post("/api/categories")
                .header("Authorization", "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"TestCat_$suffix"}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.name").value("TestCat_$suffix"))
            .andReturn()

        val json = objectMapper.readTree(result.response.contentAsByteArray)
        testCategoryId = json["id"].asInt()
    }

    @Test
    @Order(2)
    @DisplayName("카테고리 생성 (USER) → 403 권한 없음")
    fun createCategoryForbiddenForUser() {
        mockMvc.perform(
            post("/api/categories")
                .header("Authorization", "Bearer $userToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"ShouldFail"}""")
        ).andExpect(status().isForbidden)
    }

    @Test
    @Order(3)
    @DisplayName("카테고리 목록 조회 (공개) → 200")
    fun getCategories() {
        mockMvc.perform(get("/api/categories"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isArray)
    }

    @Test
    @Order(4)
    @DisplayName("카테고리 단건 조회 (공개) → 200")
    fun getCategoryById() {
        mockMvc.perform(get("/api/categories/$testCategoryId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(testCategoryId))
    }

    @Test
    @Order(5)
    @DisplayName("카테고리 요약 (공개) → 200")
    fun getCategorySummary() {
        mockMvc.perform(get("/api/categories/summary"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isArray)
    }

    // ── 상품 ──

    @Test
    @Order(10)
    @DisplayName("상품 생성 (ADMIN) → 201")
    fun createProduct() {
        val result = mockMvc.perform(
            post("/api/products")
                .header("Authorization", "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"TestProd_$suffix","description":"테스트 상품","price":9900.0,"stock":50,"categoryId":$testCategoryId}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.name").value("TestProd_$suffix"))
            .andExpect(jsonPath("$.price").value(9900.0))
            .andExpect(jsonPath("$.stock").value(50))
            .andExpect(jsonPath("$.category.id").value(testCategoryId))
            .andReturn()

        val json = objectMapper.readTree(result.response.contentAsByteArray)
        testProductId = json["id"].asInt()
    }

    @Test
    @Order(11)
    @DisplayName("상품 생성 (USER) → 403 권한 없음")
    fun createProductForbiddenForUser() {
        mockMvc.perform(
            post("/api/products")
                .header("Authorization", "Bearer $userToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"ShouldFail","price":100.0}""")
        ).andExpect(status().isForbidden)
    }

    @Test
    @Order(12)
    @DisplayName("상품 목록 조회 (공개) → 200 + 페이징")
    fun getProducts() {
        mockMvc.perform(get("/api/products"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content").isArray)
            .andExpect(jsonPath("$.totalElements").isNumber)
    }

    @Test
    @Order(13)
    @DisplayName("상품 단건 조회 (공개) → 200")
    fun getProductById() {
        mockMvc.perform(get("/api/products/$testProductId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(testProductId))
            .andExpect(jsonPath("$.name").value("TestProd_$suffix"))
    }

    @Test
    @Order(14)
    @DisplayName("상품 검색 (공개) → 200")
    fun searchProducts() {
        mockMvc.perform(get("/api/products/search").param("keyword", "TestProd_$suffix"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content[0].name").value("TestProd_$suffix"))
    }

    @Test
    @Order(15)
    @DisplayName("가격 범위 조회 (공개) → 200")
    fun getProductsByPriceRange() {
        mockMvc.perform(
            get("/api/products/price-range")
                .param("min", "9000")
                .param("max", "10000")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content").isArray)
    }

    @Test
    @Order(16)
    @DisplayName("상품 수정 (ADMIN) → 200")
    fun updateProduct() {
        mockMvc.perform(
            patch("/api/products/$testProductId")
                .header("Authorization", "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"price":12000.0,"description":"수정된 설명"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.price").value(12000.0))
            .andExpect(jsonPath("$.description").value("수정된 설명"))
    }

    @Test
    @Order(17)
    @DisplayName("상품 재입고 (ADMIN) → 200")
    fun restockProduct() {
        mockMvc.perform(
            patch("/api/products/$testProductId/restock")
                .header("Authorization", "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"quantity":20}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.stock").value(70))  // 50 + 20
    }

    @Test
    @Order(18)
    @DisplayName("재고 부족 상품 조회 (공개) → 200")
    fun getLowStockProducts() {
        mockMvc.perform(
            get("/api/products/low-stock").param("threshold", "5")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content").isArray)
    }

    // ── 삭제 (마지막에) ──

    @Test
    @Order(90)
    @DisplayName("상품 삭제 (ADMIN) → 204")
    fun deleteProduct() {
        mockMvc.perform(
            delete("/api/products/$testProductId")
                .header("Authorization", "Bearer $adminToken")
        ).andExpect(status().isNoContent)
    }

    @Test
    @Order(91)
    @DisplayName("카테고리 삭제 (ADMIN) → 204")
    fun deleteCategory() {
        mockMvc.perform(
            delete("/api/categories/$testCategoryId")
                .header("Authorization", "Bearer $adminToken")
        ).andExpect(status().isNoContent)
    }
}
