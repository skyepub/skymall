package com.skytree.skymall.repository

import com.skytree.skymall.entity.Product
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

/**
 * ================================================================
 * 상품 리포지토리 (ProductRepository)
 * ================================================================
 *
 * Spring Data JPA의 다양한 쿼리 기법을 보여주는 종합 예제.
 *
 * 이 리포지토리에서 사용하는 3가지 쿼리 방식:
 *
 * ── 1. 파생 쿼리 (Derived Query) ──
 * 메서드 이름에서 SQL이 자동 생성된다.
 * 장점: 코드가 간결하고, 쿼리 오류를 컴파일 시점에 발견할 수 있다.
 * 단점: 조건이 복잡해지면 메서드 이름이 너무 길어진다.
 *
 * 주요 키워드:
 *   findBy           → WHERE
 *   Containing       → LIKE '%...%'
 *   IgnoreCase       → LOWER(필드)로 비교
 *   LessThan         → < 비교
 *   LessThanEqual    → <= 비교
 *   GreaterThan      → > 비교
 *   Between          → BETWEEN A AND B
 *   OrderByXxxDesc   → ORDER BY xxx DESC
 *
 * ── 2. JPQL (Java Persistence Query Language) ──
 * @Query 어노테이션으로 직접 쿼리를 작성한다.
 * SQL과 비슷하지만, 테이블이 아닌 '엔티티 클래스'를 대상으로 한다.
 * 예: "SELECT p FROM Product p" (products 테이블이 아닌 Product 엔티티)
 *
 * ── 3. 페이징 (Pageable) ──
 * 동일한 쿼리의 List 버전과 Page 버전을 모두 제공한다.
 * Pageable 파라미터를 추가하면 자동으로 LIMIT/OFFSET이 적용된다.
 * Page<T>는 데이터 + 전체 건수 + 페이지 정보를 포함한다.
 *
 * Page 객체 주요 속성:
 *   content:        현재 페이지의 데이터 리스트
 *   totalElements:  전체 데이터 수 (전체 페이지의 합)
 *   totalPages:     전체 페이지 수
 *   number:         현재 페이지 번호 (0부터 시작)
 *   size:           페이지당 데이터 수
 */
interface ProductRepository : JpaRepository<Product, Int> {

    // ════════════════════════════════════════
    //  파생 쿼리 (Derived Query)
    // ════════════════════════════════════════

    /**
     * 카테고리별 상품 조회
     * SQL: SELECT * FROM products WHERE category_id = ?
     *
     * 같은 쿼리를 List 버전과 Page 버전 2가지로 제공한다:
     * - List 버전: 전체 데이터 반환 (소량일 때)
     * - Page 버전: 페이징 적용 (대량 데이터일 때)
     */
    fun findByCategoryId(categoryId: Int): List<Product>
    fun findByCategoryId(categoryId: Int, pageable: Pageable): Page<Product>

    /**
     * 상품명 부분 검색 (대소문자 무시)
     * SQL: SELECT * FROM products WHERE LOWER(name) LIKE LOWER('%keyword%')
     *
     * Containing = LIKE '%...%' (양쪽 와일드카드)
     * IgnoreCase = 대소문자 무시
     */
    fun findByNameContainingIgnoreCase(keyword: String): List<Product>

    /**
     * 재고 부족 상품 조회
     * SQL: SELECT * FROM products WHERE stock < ?
     * 예: findByStockLessThan(5) → 재고 5개 미만 상품
     */
    fun findByStockLessThan(threshold: Int): List<Product>
    fun findByStockLessThan(threshold: Int, pageable: Pageable): Page<Product>

    /**
     * 가격 범위 조회
     * SQL: SELECT * FROM products WHERE price BETWEEN ? AND ?
     * 예: findByPriceBetween(1000.0, 5000.0)
     */
    fun findByPriceBetween(min: Double, max: Double): List<Product>
    fun findByPriceBetween(min: Double, max: Double, pageable: Pageable): Page<Product>

    /**
     * 특정 가격 이하 상품
     * SQL: SELECT * FROM products WHERE price <= ?
     */
    fun findByPriceLessThanEqual(maxPrice: Double): List<Product>

    /**
     * 재고 있는 상품만 조회
     * SQL: SELECT * FROM products WHERE stock > ?
     * 예: findByStockGreaterThan(0) → 재고가 1개 이상인 상품
     */
    fun findByStockGreaterThan(minStock: Int): List<Product>

    /**
     * 카테고리별 상품 수
     * SQL: SELECT COUNT(*) FROM products WHERE category_id = ?
     * count 접두사: SELECT COUNT(*)를 생성
     */
    fun countByCategoryId(categoryId: Int): Long

    // ════════════════════════════════════════
    //  JPQL 커스텀 쿼리 (@Query)
    // ════════════════════════════════════════

    /**
     * 이름 또는 설명에서 키워드 검색 (OR 조건)
     *
     * 파생 쿼리로는 OR 조건이 길어지므로 JPQL을 사용한다.
     * 파생 쿼리 버전: findByNameContainingIgnoreCaseOrDescriptionContainingIgnoreCase(...)
     * → 너무 길다! JPQL이 훨씬 깔끔하다.
     *
     * JPQL 문법:
     * - "Product p": 엔티티 별칭
     * - LOWER(): 대소문자 무시
     * - CONCAT('%', :keyword, '%'): 와일드카드 결합
     * - :keyword: 메서드 파라미터 바인딩 (@Param)
     */
    @Query("""
        SELECT p FROM Product p
        WHERE LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
           OR LOWER(p.description) LIKE LOWER(CONCAT('%', :keyword, '%'))
    """)
    fun searchByKeyword(@Param("keyword") keyword: String): List<Product>

    /** 키워드 검색 (페이징 버전) */
    @Query("""
        SELECT p FROM Product p
        WHERE LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
           OR LOWER(p.description) LIKE LOWER(CONCAT('%', :keyword, '%'))
    """)
    fun searchByKeyword(@Param("keyword") keyword: String, pageable: Pageable): Page<Product>

    /**
     * 카테고리명으로 상품 조회 (JOIN)
     *
     * JPQL에서 JOIN은 엔티티의 관계 필드를 사용한다:
     * - "JOIN p.category c": Product.category 필드로 조인
     * - SQL의 "JOIN categories ON products.category_id = categories.category_id"와 동일
     *
     * 엔티티 관계를 통한 JOIN이므로 외래키를 직접 쓸 필요가 없다.
     */
    @Query("""
        SELECT p FROM Product p
        JOIN p.category c
        WHERE c.name = :categoryName
    """)
    fun findByCategoryName(@Param("categoryName") categoryName: String): List<Product>

    /**
     * 가격 높은 순 정렬
     * SQL: SELECT * FROM products ORDER BY price DESC
     */
    @Query("""
        SELECT p FROM Product p
        ORDER BY p.price DESC
    """)
    fun findAllOrderByPriceDesc(): List<Product>

    /**
     * 한 번이라도 판매된 상품 (주문에 포함된 상품)
     *
     * DISTINCT: 같은 상품이 여러 주문에 포함되어도 한 번만 반환
     * 크로스 엔티티 조인: "JOIN OrderItem oi ON oi.product = p"
     * → Product와 OrderItem 사이에 직접 @관계가 없어도 조인 가능
     */
    @Query("""
        SELECT DISTINCT p FROM Product p
        JOIN OrderItem oi ON oi.product = p
    """)
    fun findSoldProducts(): List<Product>

    /**
     * 한 번도 판매되지 않은 상품 (서브쿼리)
     *
     * NOT IN 서브쿼리:
     * "주문항목에 한 번이라도 등장한 상품 목록"에 포함되지 않는 상품
     *
     * 페이징 버전에서는 countQuery를 별도 지정해야 한다.
     * → Spring Data JPA가 전체 건수를 세는 COUNT 쿼리를 자동 생성하지 못하는 경우가 있기 때문
     */
    @Query("""
        SELECT p FROM Product p
        WHERE p NOT IN (
            SELECT DISTINCT oi.product FROM OrderItem oi
        )
    """)
    fun findUnsoldProducts(): List<Product>

    /**
     * 한 번도 판매되지 않은 상품 (페이징 버전)
     *
     * countQuery: Page 응답의 totalElements를 계산하기 위한 별도 쿼리.
     * 복잡한 JPQL에서는 Spring이 자동 생성하는 COUNT 쿼리가
     * 실패할 수 있어 명시적으로 지정한다.
     */
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

    /**
     * 카테고리별 평균 가격 (집계 함수)
     *
     * AVG(): SQL 집계 함수 — 평균값을 반환
     * 반환 타입 Double?: 해당 카테고리에 상품이 없으면 null
     */
    @Query("""
        SELECT AVG(p.price) FROM Product p
        WHERE p.category.id = :categoryId
    """)
    fun avgPriceByCategoryId(@Param("categoryId") categoryId: Int): Double?
}
