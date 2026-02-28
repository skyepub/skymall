package com.skytree.skymall.controller

import com.skytree.skymall.dto.*
import com.skytree.skymall.service.OrderService
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.time.LocalDateTime

/**
 * ================================================================
 * 주문 컨트롤러 (OrderController)
 * ================================================================
 *
 * 주문 관련 REST API 엔드포인트.
 * 모든 엔드포인트는 인증이 필요하다 (SecurityConfig: anyRequest().authenticated())
 *
 * ── 엔드포인트 요약 ──
 *
 * POST   /api/orders              — 주문 생성 (인증 필요)
 * GET    /api/orders              — 전체 주문 목록 (인증 필요)
 * GET    /api/orders/{id}         — 주문 상세 (인증 필요)
 * GET    /api/orders/my           — 내 주문 목록 (인증 필요, JWT에서 userId 추출)
 * GET    /api/orders/user/{userId} — 사용자별 주문 (인증 필요)
 * GET    /api/orders/product/{id} — 상품별 주문 (인증 필요)
 * GET    /api/orders/high-value   — 고액 주문 (인증 필요)
 * GET    /api/orders/large        — 대량 주문 (인증 필요)
 * GET    /api/orders/date-range   — 기간별 주문 (인증 필요)
 * GET    /api/orders/user/{id}/summary — 주문 요약 (인증 필요)
 * GET    /api/orders/report       — 매출 리포트 (ADMIN, MANAGER)
 * DELETE /api/orders/{id}         — 주문 취소 (인증 필요)
 */
@RestController
@RequestMapping("/api/orders")
class OrderController(
    private val orderService: OrderService
) {
    /**
     * 주문 생성 — 201 Created
     *
     * 하나의 트랜잭션에서 처리:
     * 사용자 검증 → 상품 검증 → 재고 확인 → 재고 차감 → 주문 저장
     * 실패 시 전체 롤백 (재고 차감 포함)
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createOrder(@RequestBody req: CreateOrderRequest): OrderResponse =
        orderService.createOrder(req)

    /**
     * 전체 주문 목록 — 페이징
     * 관리자가 모든 주문을 조회할 때 사용
     */
    @GetMapping
    fun getAllOrders(
        @PageableDefault(size = 20) pageable: Pageable
    ): Page<OrderResponse> = orderService.getAllOrders(pageable)

    /** 주문 상세 조회 */
    @GetMapping("/{id}")
    fun getOrder(@PathVariable id: Int): OrderResponse =
        orderService.getOrder(id)

    /**
     * 내 주문 목록 — JWT에서 userId 추출
     *
     * Authentication 파라미터:
     *   Spring Security가 SecurityContext에서 자동으로 주입한다.
     *   JwtAuthFilter에서 설정한 인증 객체가 여기에 들어온다.
     *
     * auth.principal as Int:
     *   JwtAuthFilter에서 principal = userId (Int)로 설정했으므로
     *   캐스팅하여 사용한다.
     *
     * 이 패턴의 장점:
     * - URL에 userId를 노출하지 않는다 (/api/orders/my vs /api/orders/user/1)
     * - 다른 사용자의 주문을 볼 수 없다 (자기 자신만)
     */
    @GetMapping("/my")
    fun getMyOrders(
        auth: Authentication,
        @PageableDefault(size = 20) pageable: Pageable
    ): Page<OrderResponse> = orderService.getOrdersByUser(auth.principal as Int, pageable)

    /** 사용자별 주문 조회 (관리자용) */
    @GetMapping("/user/{userId}")
    fun getOrdersByUser(
        @PathVariable userId: Int,
        @PageableDefault(size = 20) pageable: Pageable
    ): Page<OrderResponse> = orderService.getOrdersByUser(userId, pageable)

    /** 특정 상품을 포함한 주문 조회 */
    @GetMapping("/product/{productId}")
    fun getOrdersByProduct(
        @PathVariable productId: Int,
        @PageableDefault(size = 20) pageable: Pageable
    ): Page<OrderResponse> = orderService.getOrdersByProduct(productId, pageable)

    /**
     * 고액 주문 조회
     * 예: GET /api/orders/high-value?minAmount=100000
     */
    @GetMapping("/high-value")
    fun getHighValueOrders(
        @RequestParam minAmount: Double,
        @PageableDefault(size = 20) pageable: Pageable
    ): Page<OrderResponse> = orderService.getHighValueOrders(minAmount, pageable)

    /**
     * 대량 주문 조회 (항목 N개 이상)
     * 예: GET /api/orders/large?minItems=5
     */
    @GetMapping("/large")
    fun getLargeOrders(
        @RequestParam(defaultValue = "3") minItems: Int,
        @PageableDefault(size = 20) pageable: Pageable
    ): Page<OrderResponse> = orderService.getLargeOrders(minItems, pageable)

    /**
     * 기간별 주문 조회
     *
     * @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME):
     *   ISO 8601 형식의 날짜 문자열을 LocalDateTime으로 변환한다.
     *   예: "2026-01-01T00:00:00" → LocalDateTime.of(2026, 1, 1, 0, 0, 0)
     *
     * 호출 예:
     * GET /api/orders/date-range?from=2026-01-01T00:00:00&to=2026-12-31T23:59:59
     */
    @GetMapping("/date-range")
    fun getOrdersByDateRange(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) from: LocalDateTime,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) to: LocalDateTime,
        @PageableDefault(size = 20) pageable: Pageable
    ): Page<OrderResponse> = orderService.getOrdersByDateRange(from, to, pageable)

    /** 사용자 주문 요약 (주문 건수, 총액, 평균) */
    @GetMapping("/user/{userId}/summary")
    fun getUserOrderSummary(@PathVariable userId: Int): OrderSummaryResponse =
        orderService.getUserOrderSummary(userId)

    /**
     * 매출 리포트 — ADMIN, MANAGER만 (SecurityConfig에서 설정)
     * 기간 내 주문 건수, 총 매출, 상위 주문 5건을 반환한다.
     */
    @GetMapping("/report")
    fun getSalesReport(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) from: LocalDateTime,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) to: LocalDateTime
    ): SalesReportResponse = orderService.getSalesReport(from, to)

    /**
     * 주문 취소 — 204 No Content
     * 주문을 삭제하고, 각 상품의 재고를 복원한다.
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun cancelOrder(@PathVariable id: Int) =
        orderService.cancelOrder(id)
}
