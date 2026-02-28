package com.skytree.skymall.repository

import com.skytree.skymall.entity.OrderItem
import org.springframework.data.jpa.repository.JpaRepository

/**
 * ================================================================
 * 주문항목 리포지토리 (OrderItemRepository)
 * ================================================================
 *
 * 주문항목은 대부분 SalesOrder를 통해 cascade로 저장/삭제되므로
 * 이 리포지토리를 직접 사용하는 경우는 드물다.
 *
 * SalesOrder 저장 시: cascade = CascadeType.ALL → OrderItem 자동 저장
 * SalesOrder 삭제 시: orphanRemoval = true → OrderItem 자동 삭제
 *
 * JpaRepository<OrderItem, Long>
 *   - OrderItem: 엔티티 클래스
 *   - Long: 기본키(order_item_id) 타입 (대량 데이터 대비 BIGINT)
 */
interface OrderItemRepository : JpaRepository<OrderItem, Long> {

    /**
     * 특정 주문의 항목 목록 조회
     * SQL: SELECT * FROM order_items WHERE order_id = ?
     *
     * 보통은 SalesOrder.items (OneToMany 관계)를 통해 접근하지만,
     * 별도로 OrderItem만 필요한 경우에 사용한다.
     */
    fun findByOrderId(orderId: Int): List<OrderItem>
}
