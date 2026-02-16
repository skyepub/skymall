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

@RestController
@RequestMapping("/api/orders")
class OrderController(
    private val orderService: OrderService
) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createOrder(@RequestBody req: CreateOrderRequest): OrderResponse =
        orderService.createOrder(req)

    @GetMapping
    fun getAllOrders(
        @PageableDefault(size = 20) pageable: Pageable
    ): Page<OrderResponse> = orderService.getAllOrders(pageable)

    @GetMapping("/{id}")
    fun getOrder(@PathVariable id: Int): OrderResponse =
        orderService.getOrder(id)

    /**
     * 내 주문 목록 — JWT 토큰에서 userId를 추출
     */
    @GetMapping("/my")
    fun getMyOrders(
        auth: Authentication,
        @PageableDefault(size = 20) pageable: Pageable
    ): Page<OrderResponse> = orderService.getOrdersByUser(auth.principal as Int, pageable)

    @GetMapping("/user/{userId}")
    fun getOrdersByUser(
        @PathVariable userId: Int,
        @PageableDefault(size = 20) pageable: Pageable
    ): Page<OrderResponse> = orderService.getOrdersByUser(userId, pageable)

    @GetMapping("/product/{productId}")
    fun getOrdersByProduct(
        @PathVariable productId: Int,
        @PageableDefault(size = 20) pageable: Pageable
    ): Page<OrderResponse> = orderService.getOrdersByProduct(productId, pageable)

    @GetMapping("/high-value")
    fun getHighValueOrders(
        @RequestParam minAmount: Double,
        @PageableDefault(size = 20) pageable: Pageable
    ): Page<OrderResponse> = orderService.getHighValueOrders(minAmount, pageable)

    @GetMapping("/large")
    fun getLargeOrders(
        @RequestParam(defaultValue = "3") minItems: Int,
        @PageableDefault(size = 20) pageable: Pageable
    ): Page<OrderResponse> = orderService.getLargeOrders(minItems, pageable)

    @GetMapping("/date-range")
    fun getOrdersByDateRange(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) from: LocalDateTime,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) to: LocalDateTime,
        @PageableDefault(size = 20) pageable: Pageable
    ): Page<OrderResponse> = orderService.getOrdersByDateRange(from, to, pageable)

    @GetMapping("/user/{userId}/summary")
    fun getUserOrderSummary(@PathVariable userId: Int): OrderSummaryResponse =
        orderService.getUserOrderSummary(userId)

    @GetMapping("/report")
    fun getSalesReport(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) from: LocalDateTime,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) to: LocalDateTime
    ): SalesReportResponse = orderService.getSalesReport(from, to)

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun cancelOrder(@PathVariable id: Int) =
        orderService.cancelOrder(id)
}
