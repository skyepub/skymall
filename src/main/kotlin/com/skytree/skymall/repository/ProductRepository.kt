package com.skytree.skymall.repository

import com.skytree.skymall.entity.Product
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ProductRepository : JpaRepository<Product, Int> {

    // === 파생 쿼리 ===

    // 카테고리별 상품
    fun findByCategoryId(categoryId: Int): List<Product>
    fun findByCategoryId(categoryId: Int, pageable: Pageable): Page<Product>

    // 이름 키워드 검색
    fun findByNameContainingIgnoreCase(keyword: String): List<Product>

    // 재고 부족 상품
    fun findByStockLessThan(threshold: Int): List<Product>
    fun findByStockLessThan(threshold: Int, pageable: Pageable): Page<Product>

    // 가격 범위 조회
    fun findByPriceBetween(min: Double, max: Double): List<Product>
    fun findByPriceBetween(min: Double, max: Double, pageable: Pageable): Page<Product>

    // 특정 가격 이하 상품
    fun findByPriceLessThanEqual(maxPrice: Double): List<Product>

    // 재고 있는 상품만
    fun findByStockGreaterThan(minStock: Int): List<Product>

    // 카테고리별 상품 수
    fun countByCategoryId(categoryId: Int): Long

    // === JPQL 쿼리 ===

    // 이름 또는 설명에서 키워드 검색
    @Query("""
        SELECT p FROM Product p
        WHERE LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
           OR LOWER(p.description) LIKE LOWER(CONCAT('%', :keyword, '%'))
    """)
    fun searchByKeyword(@Param("keyword") keyword: String): List<Product>

    @Query("""
        SELECT p FROM Product p
        WHERE LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
           OR LOWER(p.description) LIKE LOWER(CONCAT('%', :keyword, '%'))
    """)
    fun searchByKeyword(@Param("keyword") keyword: String, pageable: Pageable): Page<Product>

    // 카테고리명으로 상품 조회
    @Query("""
        SELECT p FROM Product p
        JOIN p.category c
        WHERE c.name = :categoryName
    """)
    fun findByCategoryName(@Param("categoryName") categoryName: String): List<Product>

    // 가격 높은 순 상위 상품
    @Query("""
        SELECT p FROM Product p
        ORDER BY p.price DESC
    """)
    fun findAllOrderByPriceDesc(): List<Product>

    // 판매된 적 있는 상품 (주문에 포함된)
    @Query("""
        SELECT DISTINCT p FROM Product p
        JOIN OrderItem oi ON oi.product = p
    """)
    fun findSoldProducts(): List<Product>

    // 한 번도 판매되지 않은 상품
    @Query("""
        SELECT p FROM Product p
        WHERE p NOT IN (
            SELECT DISTINCT oi.product FROM OrderItem oi
        )
    """)
    fun findUnsoldProducts(): List<Product>

    @Query(
        value = """
            SELECT p FROM Product p
            WHERE p NOT IN (
                SELECT DISTINCT oi.product FROM OrderItem oi
            )
        """,
        countQuery = """
            SELECT COUNT(p) FROM Product p
            WHERE p NOT IN (
                SELECT DISTINCT oi.product FROM OrderItem oi
            )
        """
    )
    fun findUnsoldProducts(pageable: Pageable): Page<Product>

    // 카테고리별 평균 가격
    @Query("""
        SELECT AVG(p.price) FROM Product p
        WHERE p.category.id = :categoryId
    """)
    fun avgPriceByCategoryId(@Param("categoryId") categoryId: Int): Double?
}
