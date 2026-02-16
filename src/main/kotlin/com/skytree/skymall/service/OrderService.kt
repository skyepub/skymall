package com.skytree.skymall.service

import com.skytree.skymall.dto.*
import com.skytree.skymall.entity.OrderItem
import com.skytree.skymall.entity.SalesOrder
import com.skytree.skymall.exception.BusinessException
import com.skytree.skymall.exception.EntityNotFoundException
import com.skytree.skymall.exception.InsufficientStockException
import com.skytree.skymall.repository.OrderItemRepository
import com.skytree.skymall.repository.SalesOrderRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * 주문 서비스 — 가장 복잡한 서비스
 *
 * 4개의 Repository를 복합 사용:
 * - SalesOrderRepository: 주문 CRUD + 집계
 * - OrderItemRepository: 주문 아이템
 * - UserService.findUserOrThrow(): 사용자 검증 (Service → Service 위임)
 * - CatalogService.findProductOrThrow(): 상품 검증 + 재고 차감
 *
 * 하나의 주문 생성 메서드에서 사용자 검증 → 상품 검증 → 재고 차감 → 주문 생성이
 * 하나의 트랜잭션 안에서 처리된다.
 */
@Service
@Transactional(readOnly = true)
class OrderService(
    private val orderRepo: SalesOrderRepository,
    private val orderItemRepo: OrderItemRepository,
    private val userService: UserService,
    private val catalogService: CatalogService
) {
    // ══════════════════════════════════════
    //  주문 생성 — 핵심 비즈니스 로직
    // ══════════════════════════════════════

    /**
     * 주문 생성
     *
     * 하나의 트랜잭션에서:
     * 1. UserRepo → 사용자 존재 검증
     * 2. ProductRepo → 각 상품 존재 검증 + 재고 확인
     * 3. 재고 차감 (dirty checking)
     * 4. SalesOrder + OrderItem 저장 (cascade)
     *
     * 중간에 실패하면 전체 롤백된다.
     */
    @Transactional
    fun createOrder(req: CreateOrderRequest): OrderResponse {
        if (req.items.isEmpty()) {
            throw BusinessException("주문 항목이 비어있습니다")
        }

        // 1. 사용자 검증
        val user = userService.findUserOrThrow(req.userId)
        if (!user.isEnabled) {
            throw BusinessException("비활성화된 사용자는 주문할 수 없습니다")
        }

        // 2. 상품 검증 + 재고 확인 + 금액 계산
        var totalAmount = 0.0
        val orderItems = mutableListOf<OrderItem>()

        for (itemReq in req.items) {
            val product = catalogService.findProductOrThrow(itemReq.productId)

            // 재고 검증
            if (product.stock < itemReq.quantity) {
                throw InsufficientStockException(
                    product.name, itemReq.quantity, product.stock
                )
            }

            // 3. 재고 차감
            product.stock -= itemReq.quantity

            val subtotal = product.price * itemReq.quantity
            totalAmount += subtotal

            orderItems.add(
                OrderItem(
                    product = product,
                    quantity = itemReq.quantity,
                    pricePerItem = product.price  // 주문 시점 가격 스냅샷
                )
            )
        }

        // 4. 주문 저장 (cascade로 OrderItem도 함께 저장)
        val order = SalesOrder(
            user = user,
            totalAmount = totalAmount
        )
        orderItems.forEach { item ->
            item.order = order
            order.items.add(item)
        }

        val saved = orderRepo.save(order)
        return OrderResponse.from(saved)
    }

    // ══════════════════════════════════════
    //  주문 조회
    // ══════════════════════════════════════

    fun getOrder(id: Int): OrderResponse {
        val order = findOrderOrThrow(id)
        return OrderResponse.from(order)
    }

    fun getAllOrders(pageable: Pageable): Page<OrderResponse> =
        orderRepo.findAll(pageable).map { OrderResponse.from(it) }

    fun getOrdersByUser(userId: Int, pageable: Pageable): Page<OrderResponse> {
        userService.findUserOrThrow(userId)  // 사용자 존재 검증
        return orderRepo.findByUserIdOrderByOrderDateDesc(userId, pageable)
            .map { OrderResponse.from(it) }
    }

    fun getOrdersByDateRange(from: LocalDateTime, to: LocalDateTime, pageable: Pageable): Page<OrderResponse> =
        orderRepo.findByOrderDateBetween(from, to, pageable).map { OrderResponse.from(it) }

    fun getOrdersByProduct(productId: Int, pageable: Pageable): Page<OrderResponse> {
        catalogService.findProductOrThrow(productId)  // 상품 존재 검증
        return orderRepo.findByProductId(productId, pageable).map { OrderResponse.from(it) }
    }

    fun getHighValueOrders(minAmount: Double, pageable: Pageable): Page<OrderResponse> =
        orderRepo.findByTotalAmountGreaterThanEqual(minAmount, pageable)
            .map { OrderResponse.from(it) }

    fun getLargeOrders(minItems: Int, pageable: Pageable): Page<OrderResponse> =
        orderRepo.findByMinItemCount(minItems, pageable).map { OrderResponse.from(it) }

    // ══════════════════════════════════════
    //  주문 취소
    // ══════════════════════════════════════

    /**
     * 주문 취소 — 재고 복원 포함
     *
     * 1. 주문 조회
     * 2. 각 아이템의 상품 재고 복원
     * 3. 주문 삭제 (cascade로 OrderItem도 삭제)
     */
    @Transactional
    fun cancelOrder(id: Int) {
        val order = findOrderOrThrow(id)

        // 재고 복원
        for (item in order.items) {
            val product = item.product
                ?: throw BusinessException("주문 아이템에 상품 정보가 없습니다")
            product.stock += item.quantity
        }

        orderRepo.delete(order)
    }

    // ══════════════════════════════════════
    //  매출 통계 — 여러 집계 쿼리 복합
    // ══════════════════════════════════════

    /**
     * 사용자별 주문 요약
     * OrderRepo의 count + sum + avg를 결합
     */
    fun getUserOrderSummary(userId: Int): OrderSummaryResponse {
        userService.findUserOrThrow(userId)
        return OrderSummaryResponse(
            totalOrders = orderRepo.countByUserId(userId),
            totalRevenue = orderRepo.sumTotalAmountByUserId(userId) ?: 0.0,
            avgOrderAmount = orderRepo.avgTotalAmountByUserId(userId) ?: 0.0
        )
    }

    /**
     * 기간별 매출 리포트
     * OrderRepo의 count + sum + topN 복합
     */
    fun getSalesReport(from: LocalDateTime, to: LocalDateTime): SalesReportResponse {
        val orderCount = orderRepo.countByOrderDateBetween(from, to)
        val totalRevenue = orderRepo.sumTotalAmountBetween(from, to) ?: 0.0
        val topOrders = orderRepo.findTopByTotalAmount()
            .take(5)
            .map { OrderResponse.from(it) }

        return SalesReportResponse(
            from = from,
            to = to,
            orderCount = orderCount,
            totalRevenue = totalRevenue,
            topOrders = topOrders
        )
    }

    // ══════════════════════════════════════
    //  내부 헬퍼
    // ══════════════════════════════════════

    private fun findOrderOrThrow(id: Int): SalesOrder =
        orderRepo.findById(id)
            .orElseThrow { EntityNotFoundException("주문", id) }
}
