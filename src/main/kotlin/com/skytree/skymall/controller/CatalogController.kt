package com.skytree.skymall.controller

import com.skytree.skymall.dto.*
import com.skytree.skymall.service.CatalogService
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

/**
 * ================================================================
 * 카탈로그 컨트롤러 (CatalogController)
 * ================================================================
 *
 * 상품과 카테고리의 REST API 엔드포인트를 정의한다.
 *
 * ── 엔드포인트 요약 ──
 *
 * [카테고리]
 * GET    /api/categories          — 전체 카테고리 목록 (공개)
 * GET    /api/categories/{id}     — 카테고리 단건 조회 (공개)
 * GET    /api/categories/{id}/products — 카테고리별 상품 (공개)
 * GET    /api/categories/summary  — 카테고리 통계 (공개)
 * POST   /api/categories          — 카테고리 생성 (ADMIN, MANAGER)
 * DELETE /api/categories/{id}     — 카테고리 삭제 (ADMIN, MANAGER)
 *
 * [상품]
 * GET    /api/products            — 상품 목록 (공개, 페이징)
 * GET    /api/products/{id}       — 상품 상세 (공개)
 * GET    /api/products/search     — 키워드 검색 (공개)
 * GET    /api/products/price-range — 가격 범위 조회 (공개)
 * GET    /api/products/low-stock  — 재고 부족 상품 (공개)
 * GET    /api/products/unsold     — 미판매 상품 (공개)
 * POST   /api/products            — 상품 등록 (ADMIN, MANAGER)
 * PATCH  /api/products/{id}       — 상품 수정 (ADMIN, MANAGER)
 * PATCH  /api/products/{id}/restock — 재입고 (ADMIN, MANAGER)
 * DELETE /api/products/{id}       — 상품 삭제 (ADMIN, MANAGER)
 *
 * ── @RequestMapping("/api") ──
 * 상품과 카테고리가 /api/products, /api/categories로 나뉘지만
 * 같은 CatalogService를 사용하므로 하나의 컨트롤러에서 처리한다.
 *
 * ── 권한 규칙 (SecurityConfig에서 설정) ──
 * GET 요청: 공개 (permitAll) — 비회원도 상품을 볼 수 있어야 한다
 * POST/PATCH/DELETE: ADMIN, MANAGER만 — 상품 관리 권한
 */
@RestController
@RequestMapping("/api")
class CatalogController(
    private val catalogService: CatalogService
) {
    // ════════════════════════════════════════
    //  카테고리 (Categories)
    // ════════════════════════════════════════

    /** 전체 카테고리 목록 조회 — 공개 */
    @GetMapping("/categories")
    fun getAllCategories(): List<CategoryResponse> =
        catalogService.getAllCategories()

    /**
     * 카테고리 단건 조회 — 공개
     *
     * @PathVariable: URL 경로의 {id} 부분을 파라미터로 바인딩
     * 예: GET /api/categories/3 → id = 3
     */
    @GetMapping("/categories/{id}")
    fun getCategory(@PathVariable id: Int): CategoryResponse =
        catalogService.getCategory(id)

    /**
     * 카테고리별 상품 목록 — 공개, 페이징
     *
     * @PageableDefault(size = 20):
     *   페이징 기본값 설정 — 클라이언트가 page, size를 지정하지 않으면 사용
     *   예: GET /api/categories/1/products → page=0, size=20 (기본)
     *   예: GET /api/categories/1/products?page=2&size=10 → 3번째 페이지, 10개씩
     *
     * Pageable 객체에 포함되는 정보:
     *   - page:  페이지 번호 (0부터 시작)
     *   - size:  페이지당 항목 수
     *   - sort:  정렬 조건 (예: sort=price,desc)
     */
    @GetMapping("/categories/{id}/products")
    fun getProductsByCategory(
        @PathVariable id: Int,
        @PageableDefault(size = 20) pageable: Pageable
    ): Page<ProductResponse> = catalogService.getProductsByCategory(id, pageable)

    /** 카테고리별 통계 (상품 수, 평균 가격) — 공개 */
    @GetMapping("/categories/summary")
    fun getCategorySummary(): List<CategorySummaryResponse> =
        catalogService.getCategorySummary()

    /**
     * 카테고리 생성 — ADMIN, MANAGER만
     *
     * @ResponseStatus(HttpStatus.CREATED): 201 Created
     * @RequestBody: JSON 본문 → CreateCategoryRequest 객체 변환
     */
    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    fun createCategory(@RequestBody req: CreateCategoryRequest): CategoryResponse =
        catalogService.createCategory(req)

    /**
     * 카테고리 삭제 — ADMIN, MANAGER만
     *
     * @ResponseStatus(HttpStatus.NO_CONTENT): 204 No Content (응답 본문 없음)
     * 상품이 있는 카테고리는 삭제 불가 (Service에서 BusinessException)
     */
    @DeleteMapping("/categories/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteCategory(@PathVariable id: Int) =
        catalogService.deleteCategory(id)

    // ════════════════════════════════════════
    //  상품 (Products)
    // ════════════════════════════════════════

    /**
     * 전체 상품 목록 — 공개, 페이징
     *
     * Page<ProductResponse> 응답 JSON 구조:
     * {
     *   "content": [ ... ],        ← 현재 페이지의 데이터
     *   "totalElements": 100,      ← 전체 데이터 수
     *   "totalPages": 5,           ← 전체 페이지 수
     *   "number": 0,               ← 현재 페이지 번호 (0-based)
     *   "size": 20,                ← 페이지 크기
     *   "first": true,             ← 첫 페이지 여부
     *   "last": false              ← 마지막 페이지 여부
     * }
     */
    @GetMapping("/products")
    fun getAllProducts(
        @PageableDefault(size = 20) pageable: Pageable
    ): Page<ProductResponse> = catalogService.getAllProducts(pageable)

    /** 상품 상세 조회 — 공개 */
    @GetMapping("/products/{id}")
    fun getProduct(@PathVariable id: Int): ProductResponse =
        catalogService.getProduct(id)

    /**
     * 상품 키워드 검색 — 공개
     *
     * @RequestParam: URL 쿼리 파라미터를 바인딩
     * 예: GET /api/products/search?keyword=맥북 → keyword = "맥북"
     *
     * 이름과 설명에서 키워드를 검색한다 (JPQL OR + LIKE 쿼리)
     */
    @GetMapping("/products/search")
    fun searchProducts(
        @RequestParam keyword: String,
        @PageableDefault(size = 20) pageable: Pageable
    ): Page<ProductResponse> = catalogService.searchProducts(keyword, pageable)

    /**
     * 가격 범위 조회 — 공개
     * 예: GET /api/products/price-range?min=1000&max=50000
     */
    @GetMapping("/products/price-range")
    fun getProductsByPriceRange(
        @RequestParam min: Double,
        @RequestParam max: Double,
        @PageableDefault(size = 20) pageable: Pageable
    ): Page<ProductResponse> = catalogService.getProductsByPriceRange(min, max, pageable)

    /**
     * 재고 부족 상품 조회 — 공개
     *
     * @RequestParam(defaultValue = "5"):
     *   파라미터를 생략하면 기본값 5를 사용한다.
     *   예: GET /api/products/low-stock → threshold = 5
     *   예: GET /api/products/low-stock?threshold=10 → threshold = 10
     */
    @GetMapping("/products/low-stock")
    fun getLowStockProducts(
        @RequestParam(defaultValue = "5") threshold: Int,
        @PageableDefault(size = 20) pageable: Pageable
    ): Page<ProductResponse> = catalogService.getLowStockProducts(threshold, pageable)

    /** 미판매 상품 조회 (한 번도 주문에 포함되지 않은 상품) — 공개 */
    @GetMapping("/products/unsold")
    fun getUnsoldProducts(
        @PageableDefault(size = 20) pageable: Pageable
    ): Page<ProductResponse> = catalogService.getUnsoldProducts(pageable)

    /**
     * 상품 등록 — ADMIN, MANAGER만
     * 201 Created 응답
     */
    @PostMapping("/products")
    @ResponseStatus(HttpStatus.CREATED)
    fun createProduct(@RequestBody req: CreateProductRequest): ProductResponse =
        catalogService.createProduct(req)

    /**
     * 상품 수정 — ADMIN, MANAGER만 (Partial Update)
     *
     * PATCH vs PUT:
     * - PUT:   전체 교체 — 모든 필드를 보내야 함
     * - PATCH: 부분 수정 — 변경할 필드만 보내면 됨 (REST 권장)
     *
     * 예: PATCH /api/products/1
     *     Body: {"price": 15000}  ← 가격만 변경, 나머지는 유지
     */
    @PatchMapping("/products/{id}")
    fun updateProduct(
        @PathVariable id: Int,
        @RequestBody req: UpdateProductRequest
    ): ProductResponse = catalogService.updateProduct(id, req)

    /**
     * 재입고 — ADMIN, MANAGER만
     * 기존 재고에 수량을 추가한다.
     */
    @PatchMapping("/products/{id}/restock")
    fun restockProduct(
        @PathVariable id: Int,
        @RequestBody req: RestockRequest
    ): ProductResponse = catalogService.restockProduct(id, req.quantity)

    /** 상품 삭제 — ADMIN, MANAGER만, 204 No Content */
    @DeleteMapping("/products/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteProduct(@PathVariable id: Int) =
        catalogService.deleteProduct(id)
}
