package com.skytree.skymall.dto

import com.skytree.skymall.entity.OrderItem
import com.skytree.skymall.entity.SalesOrder
import java.time.LocalDateTime

/**
 * ================================================================
 * 주문 관련 DTO
 * ================================================================
 *
 * 주문 시스템의 데이터 흐름:
 *
 * [클라이언트 요청]
 *   CreateOrderRequest
 *     └─ items: List<OrderItemRequest>  ← 상품ID + 수량
 *
 * [서버 처리]
 *   1. User 검증
 *   2. Product별 재고 확인 + 차감
 *   3. SalesOrder + OrderItem 엔티티 생성
 *   4. DB 저장
 *
 * [클라이언트 응답]
 *   OrderResponse
 *     └─ items: List<OrderItemResponse>  ← 상품명 + 단가 + 소계
 *
 * ── 가격 스냅샷 ──
 * 주문 시점의 가격(pricePerItem)을 별도로 저장한다.
 * 응답의 subtotal = quantity × pricePerItem (주문 시점 가격 기준)
 */

// ════════════════════════════════════════
//  Request DTO
// ════════════════════════════════════════

/**
 * 주문 생성 요청
 *
 * JSON 예시:
 * {
 *   "userId": 1,
 *   "items": [
 *     {"productId": 10, "quantity": 2},
 *     {"productId": 20, "quantity": 1}
 *   ]
 * }
 */
data class CreateOrderRequest(
    val userId: Int,                       // 주문자 ID
    val items: List<OrderItemRequest>      // 주문할 상품 목록
)

/**
 * 주문 항목 요청 (개별 상품 + 수량)
 */
data class OrderItemRequest(
    val productId: Int,   // 상품 ID
    val quantity: Int      // 주문 수량
)

// ════════════════════════════════════════
//  Response DTO
// ════════════════════════════════════════

/**
 * 주문 응답 DTO
 *
 * JSON 예시:
 * {
 *   "id": 1,
 *   "userId": 1,
 *   "username": "john",
 *   "orderDate": "2026-02-28T14:30:00",
 *   "totalAmount": 150000,
 *   "items": [
 *     {
 *       "id": 1,
 *       "productId": 10,
 *       "productName": "맥북 프로",
 *       "quantity": 2,
 *       "pricePerItem": 50000,
 *       "subtotal": 100000
 *     }
 *   ]
 * }
 */
data class OrderResponse(
    val id: Int,
    val userId: Int,
    val username: String?,
    val orderDate: LocalDateTime,
    val totalAmount: Double,
    val items: List<OrderItemResponse>     // 주문항목 목록 (중첩 DTO)
) {
    companion object {
        /**
         * SalesOrder Entity → OrderResponse DTO 변환
         *
         * order.user?.id ?: 0:
         *   사용자가 null이면 0을 기본값으로 사용 (Elvis 연산자)
         *
         * order.items.map { OrderItemResponse.from(it) }:
         *   각 OrderItem 엔티티를 OrderItemResponse DTO로 변환
         */
        fun from(order: SalesOrder) = OrderResponse(
            id = order.id,
            userId = order.user?.id ?: 0,
            username = order.user?.username,
            orderDate = order.orderDate,
            totalAmount = order.totalAmount,
            items = order.items.map { OrderItemResponse.from(it) }
        )
    }
}

/**
 * 주문항목 응답 DTO
 *
 * subtotal: 항목별 소계 = 수량 × 단가
 * 이 값은 DB에 저장하지 않고 응답 시 계산한다 (파생 필드).
 */
data class OrderItemResponse(
    val id: Long,
    val productId: Int,
    val productName: String?,
    val quantity: Int,
    val pricePerItem: Double,    // 주문 시점 단가 (스냅샷)
    val subtotal: Double         // 소계 = quantity × pricePerItem (계산값)
) {
    companion object {
        fun from(item: OrderItem) = OrderItemResponse(
            id = item.id,
            productId = item.product?.id ?: 0,
            productName = item.product?.name,
            quantity = item.quantity,
            pricePerItem = item.pricePerItem,
            subtotal = item.quantity * item.pricePerItem  // 소계 계산
        )
    }
}

/**
 * 사용자 주문 요약 응답
 * 특정 사용자의 주문 통계를 한눈에 보여준다.
 *
 * JSON 예시:
 * {
 *   "totalOrders": 5,
 *   "totalRevenue": 750000,
 *   "avgOrderAmount": 150000
 * }
 */
data class OrderSummaryResponse(
    val totalOrders: Long,         // 총 주문 건수
    val totalRevenue: Double,      // 총 주문 금액
    val avgOrderAmount: Double     // 평균 주문 금액
)

/**
 * 매출 리포트 응답
 * 기간별 매출 현황 + 상위 주문 목록
 *
 * JSON 예시:
 * {
 *   "from": "2026-01-01T00:00:00",
 *   "to": "2026-12-31T23:59:59",
 *   "orderCount": 1000,
 *   "totalRevenue": 50000000,
 *   "topOrders": [ ... ]         ← 금액 상위 5건
 * }
 */
data class SalesReportResponse(
    val from: LocalDateTime,               // 조회 시작일
    val to: LocalDateTime,                 // 조회 종료일
    val orderCount: Long,                  // 기간 내 주문 건수
    val totalRevenue: Double,              // 기간 내 총 매출
    val topOrders: List<OrderResponse>     // 금액 상위 주문 5건
)
