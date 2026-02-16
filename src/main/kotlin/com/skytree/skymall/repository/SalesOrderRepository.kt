package com.skytree.skymall.repository

import com.skytree.skymall.entity.SalesOrder
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

interface SalesOrderRepository : JpaRepository<SalesOrder, Int> {

    // === 파생 쿼리 ===

    // 특정 사용자의 주문 목록
    fun findByUserId(userId: Int): List<SalesOrder>

    // 특정 사용자의 주문을 최신순으로
    fun findByUserIdOrderByOrderDateDesc(userId: Int): List<SalesOrder>
    fun findByUserIdOrderByOrderDateDesc(userId: Int, pageable: Pageable): Page<SalesOrder>

    // 기간별 주문 조회
    fun findByOrderDateBetween(from: LocalDateTime, to: LocalDateTime): List<SalesOrder>
    fun findByOrderDateBetween(from: LocalDateTime, to: LocalDateTime, pageable: Pageable): Page<SalesOrder>

    // 금액 이상 주문
    fun findByTotalAmountGreaterThanEqual(amount: Double): List<SalesOrder>
    fun findByTotalAmountGreaterThanEqual(amount: Double, pageable: Pageable): Page<SalesOrder>

    // 금액 범위 주문
    fun findByTotalAmountBetween(min: Double, max: Double): List<SalesOrder>

    // 특정 사용자의 기간별 주문
    fun findByUserIdAndOrderDateBetween(userId: Int, from: LocalDateTime, to: LocalDateTime): List<SalesOrder>

    // 주문 수 카운트 (사용자별)
    fun countByUserId(userId: Int): Long

    // === JPQL 쿼리 ===

    // 특정 상품을 포함한 주문 목록
    @Query("""
        SELECT DISTINCT o FROM SalesOrder o
        JOIN o.items i
        WHERE i.product.id = :productId
    """)
    fun findByProductId(@Param("productId") productId: Int): List<SalesOrder>

    @Query(
        value = """
            SELECT DISTINCT o FROM SalesOrder o
            JOIN o.items i
            WHERE i.product.id = :productId
        """,
        countQuery = """
            SELECT COUNT(DISTINCT o) FROM SalesOrder o
            JOIN o.items i
            WHERE i.product.id = :productId
        """
    )
    fun findByProductId(@Param("productId") productId: Int, pageable: Pageable): Page<SalesOrder>

    // 특정 카테고리 상품을 포함한 주문
    @Query("""
        SELECT DISTINCT o FROM SalesOrder o
        JOIN o.items i
        WHERE i.product.category.id = :categoryId
    """)
    fun findByCategoryId(@Param("categoryId") categoryId: Int): List<SalesOrder>

    // 사용자별 총 주문 금액
    @Query("""
        SELECT SUM(o.totalAmount) FROM SalesOrder o
        WHERE o.user.id = :userId
    """)
    fun sumTotalAmountByUserId(@Param("userId") userId: Int): Double?

    // 기간별 총 매출
    @Query("""
        SELECT SUM(o.totalAmount) FROM SalesOrder o
        WHERE o.orderDate BETWEEN :from AND :to
    """)
    fun sumTotalAmountBetween(@Param("from") from: LocalDateTime, @Param("to") to: LocalDateTime): Double?

    // 기간별 주문 건수
    @Query("""
        SELECT COUNT(o) FROM SalesOrder o
        WHERE o.orderDate BETWEEN :from AND :to
    """)
    fun countByOrderDateBetween(@Param("from") from: LocalDateTime, @Param("to") to: LocalDateTime): Long

    // 주문 금액 상위 N건
    @Query("""
        SELECT o FROM SalesOrder o
        ORDER BY o.totalAmount DESC
    """)
    fun findTopByTotalAmount(): List<SalesOrder>

    // 특정 사용자의 평균 주문 금액
    @Query("""
        SELECT AVG(o.totalAmount) FROM SalesOrder o
        WHERE o.user.id = :userId
    """)
    fun avgTotalAmountByUserId(@Param("userId") userId: Int): Double?

    // 아이템 N개 이상 포함된 주문
    @Query("""
        SELECT o FROM SalesOrder o
        WHERE SIZE(o.items) >= :minItems
    """)
    fun findByMinItemCount(@Param("minItems") minItems: Int): List<SalesOrder>

    @Query(
        value = """
            SELECT o FROM SalesOrder o
            WHERE SIZE(o.items) >= :minItems
        """,
        countQuery = """
            SELECT COUNT(o) FROM SalesOrder o
            WHERE SIZE(o.items) >= :minItems
        """
    )
    fun findByMinItemCount(@Param("minItems") minItems: Int, pageable: Pageable): Page<SalesOrder>
}
