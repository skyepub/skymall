package com.skytree.skymall.repository

import com.skytree.skymall.entity.SalesOrder
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

/**
 * ================================================================
 * 주문 리포지토리 (SalesOrderRepository)
 * ================================================================
 *
 * 가장 복잡한 리포지토리 — 파생 쿼리, JPQL 조인, 집계 함수를 모두 활용.
 *
 * ── 파생 쿼리 명명 규칙 복합 예제 ──
 *
 * findByUserIdOrderByOrderDateDesc:
 *   findBy     → WHERE
 *   UserId     → user_id = ?
 *   OrderBy    → ORDER BY
 *   OrderDate  → order_date
 *   Desc       → DESC (내림차순)
 *
 * findByUserIdAndOrderDateBetween:
 *   UserId     → user_id = ?
 *   And        → AND
 *   OrderDate  → order_date
 *   Between    → BETWEEN ? AND ?
 *
 * ── JPQL 조인 쿼리 ──
 *
 * 주문항목(OrderItem)을 통해 상품이나 카테고리 조건으로 주문을 검색한다.
 * 예: "이 상품이 포함된 주문 목록"
 *
 *   SalesOrder → JOIN → OrderItem → 조건 (product.id = ?)
 *
 * ── 집계 쿼리 ──
 *
 * SUM, COUNT, AVG 등 집계 함수로 매출 통계를 생성한다.
 * 이 쿼리들은 OrderService에서 조합되어 매출 리포트를 만든다.
 */
interface SalesOrderRepository : JpaRepository<SalesOrder, Int> {

    // ════════════════════════════════════════
    //  파생 쿼리 (Derived Query)
    // ════════════════════════════════════════

    /**
     * 사용자별 주문 목록
     * SQL: SELECT * FROM sales_orders WHERE user_id = ?
     */
    fun findByUserId(userId: Int): List<SalesOrder>

    /**
     * 사용자별 주문을 최신순으로 정렬
     * SQL: SELECT * FROM sales_orders WHERE user_id = ? ORDER BY order_date DESC
     *
     * 메서드 이름이 길지만, 한눈에 "무엇을 하는지" 알 수 있다.
     */
    fun findByUserIdOrderByOrderDateDesc(userId: Int): List<SalesOrder>
    fun findByUserIdOrderByOrderDateDesc(userId: Int, pageable: Pageable): Page<SalesOrder>

    /**
     * 기간별 주문 조회
     * SQL: SELECT * FROM sales_orders WHERE order_date BETWEEN ? AND ?
     * 예: findByOrderDateBetween(시작일, 종료일)
     */
    fun findByOrderDateBetween(from: LocalDateTime, to: LocalDateTime): List<SalesOrder>
    fun findByOrderDateBetween(from: LocalDateTime, to: LocalDateTime, pageable: Pageable): Page<SalesOrder>

    /**
     * 특정 금액 이상 주문 (고액 주문)
     * SQL: SELECT * FROM sales_orders WHERE total_amount >= ?
     */
    fun findByTotalAmountGreaterThanEqual(amount: Double): List<SalesOrder>
    fun findByTotalAmountGreaterThanEqual(amount: Double, pageable: Pageable): Page<SalesOrder>

    /**
     * 금액 범위 주문
     * SQL: SELECT * FROM sales_orders WHERE total_amount BETWEEN ? AND ?
     */
    fun findByTotalAmountBetween(min: Double, max: Double): List<SalesOrder>

    /**
     * 사용자의 기간별 주문 (복합 조건: AND)
     * SQL: SELECT * FROM sales_orders WHERE user_id = ? AND order_date BETWEEN ? AND ?
     */
    fun findByUserIdAndOrderDateBetween(userId: Int, from: LocalDateTime, to: LocalDateTime): List<SalesOrder>

    /**
     * 사용자별 주문 건수
     * SQL: SELECT COUNT(*) FROM sales_orders WHERE user_id = ?
     */
    fun countByUserId(userId: Int): Long

    // ════════════════════════════════════════
    //  JPQL 쿼리 — 조인 & 집계
    // ════════════════════════════════════════

    /**
     * 특정 상품을 포함한 주문 목록 (조인 쿼리)
     *
     * "JOIN o.items i": SalesOrder의 items 컬렉션을 통해 OrderItem과 조인
     * DISTINCT: 한 주문에 같은 상품이 여러 개여도 주문은 한 번만 반환
     *
     * SQL 변환:
     *   SELECT DISTINCT so.*
     *   FROM sales_orders so
     *   JOIN order_items oi ON so.order_id = oi.order_id
     *   WHERE oi.product_id = ?
     */
    @Query("""
        SELECT DISTINCT o FROM SalesOrder o
        JOIN o.items i
        WHERE i.product.id = :productId
    """)
    fun findByProductId(@Param("productId") productId: Int): List<SalesOrder>

    /**
     * 특정 상품을 포함한 주문 목록 (페이징 버전)
     * DISTINCT + 페이징에서는 countQuery를 별도 지정해야 정확한 전체 건수를 얻을 수 있다.
     */
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

    /**
     * 특정 카테고리 상품을 포함한 주문 (다단계 조인)
     *
     * o → items → product → category 로 3단계 조인
     * JPQL에서는 "i.product.category.id"처럼 점(.)으로 탐색할 수 있다.
     */
    @Query("""
        SELECT DISTINCT o FROM SalesOrder o
        JOIN o.items i
        WHERE i.product.category.id = :categoryId
    """)
    fun findByCategoryId(@Param("categoryId") categoryId: Int): List<SalesOrder>

    // ════════════════════════════════════════
    //  집계 쿼리 — 매출 통계용
    // ════════════════════════════════════════

    /**
     * 사용자별 총 주문 금액 (SUM)
     * SQL: SELECT SUM(total_amount) FROM sales_orders WHERE user_id = ?
     * 반환: Double? (주문이 없으면 null)
     */
    @Query("""
        SELECT SUM(o.totalAmount) FROM SalesOrder o
        WHERE o.user.id = :userId
    """)
    fun sumTotalAmountByUserId(@Param("userId") userId: Int): Double?

    /**
     * 기간별 총 매출 (SUM)
     * SQL: SELECT SUM(total_amount) FROM sales_orders WHERE order_date BETWEEN ? AND ?
     */
    @Query("""
        SELECT SUM(o.totalAmount) FROM SalesOrder o
        WHERE o.orderDate BETWEEN :from AND :to
    """)
    fun sumTotalAmountBetween(@Param("from") from: LocalDateTime, @Param("to") to: LocalDateTime): Double?

    /**
     * 기간별 주문 건수 (COUNT)
     * SQL: SELECT COUNT(*) FROM sales_orders WHERE order_date BETWEEN ? AND ?
     */
    @Query("""
        SELECT COUNT(o) FROM SalesOrder o
        WHERE o.orderDate BETWEEN :from AND :to
    """)
    fun countByOrderDateBetween(@Param("from") from: LocalDateTime, @Param("to") to: LocalDateTime): Long

    /**
     * 주문 금액 상위 N건 (정렬)
     * SQL: SELECT * FROM sales_orders ORDER BY total_amount DESC
     * Service에서 .take(5)로 상위 5건만 추출한다.
     */
    @Query("""
        SELECT o FROM SalesOrder o
        ORDER BY o.totalAmount DESC
    """)
    fun findTopByTotalAmount(): List<SalesOrder>

    /**
     * 사용자별 평균 주문 금액 (AVG)
     * SQL: SELECT AVG(total_amount) FROM sales_orders WHERE user_id = ?
     */
    @Query("""
        SELECT AVG(o.totalAmount) FROM SalesOrder o
        WHERE o.user.id = :userId
    """)
    fun avgTotalAmountByUserId(@Param("userId") userId: Int): Double?

    /**
     * 아이템 N개 이상 포함된 주문 (SIZE 함수)
     *
     * SIZE(o.items): 컬렉션의 크기를 반환하는 JPQL 함수
     * SQL에서는 서브쿼리로 변환된다:
     *   WHERE (SELECT COUNT(*) FROM order_items WHERE order_id = so.order_id) >= ?
     */
    @Query("""
        SELECT o FROM SalesOrder o
        WHERE SIZE(o.items) >= :minItems
    """)
    fun findByMinItemCount(@Param("minItems") minItems: Int): List<SalesOrder>

    /** 아이템 N개 이상 포함된 주문 (페이징 버전) */
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
