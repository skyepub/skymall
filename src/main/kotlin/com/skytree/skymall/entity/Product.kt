package com.skytree.skymall.entity

import jakarta.persistence.*
import java.time.LocalDateTime

/**
 * ================================================================
 * 상품 엔티티 (Product Entity)
 * ================================================================
 *
 * 쇼핑몰의 판매 상품 정보를 담는 엔티티.
 * Category와 ManyToOne(다대일) 관계를 가진다.
 *
 * ┌──────────────────────────────────────────────────┐
 * │  products 테이블                                   │
 * ├───────────────┬────────────┬──────────────────────┤
 * │ product_id(PK)│ INT        │ 자동 증가             │
 * │ name          │ VARCHAR(100)│ NOT NULL             │
 * │ description   │ TEXT        │ 상세 설명 (nullable) │
 * │ price         │ DOUBLE      │ NOT NULL             │
 * │ stock         │ INT         │ NOT NULL (기본: 0)    │
 * │ category_id(FK)│ INT        │ 카테고리 외래키 (nullable) │
 * │ created_at    │ DATETIME    │ 등록 시각             │
 * └───────────────┴────────────┴──────────────────────┘
 *
 * ── @ManyToOne 관계 (다대일) ──
 *
 * 여러 상품(Many)이 하나의 카테고리(One)에 속한다.
 *
 *   Category  ←─────  Product
 *   (1)                (N)
 *   "전자제품"   ←── "노트북", "마우스", "키보드"
 *
 * @ManyToOne(fetch = FetchType.LAZY)
 *   - LAZY (지연 로딩): Product를 조회할 때 Category를 즉시 로딩하지 않는다.
 *     실제로 product.category에 접근할 때 비로소 SELECT 쿼리가 실행된다.
 *     → 불필요한 JOIN을 방지하여 성능을 높인다.
 *   - EAGER (즉시 로딩): Product 조회 시 항상 Category도 함께 JOIN.
 *     편리하지만 N+1 문제를 유발할 수 있어 실무에서는 LAZY를 기본으로 사용한다.
 *
 * @JoinColumn(name = "category_id")
 *   외래키(FK) 컬럼 이름을 지정한다.
 *   이 컬럼이 categories 테이블의 category_id를 참조한다.
 *   생략하면 "category_category_id" 같은 이름이 자동 생성된다.
 *
 * ── columnDefinition = "TEXT" ──
 * MySQL의 TEXT 타입: 최대 65,535자 (VARCHAR(255)보다 큼)
 * 상품 설명처럼 긴 텍스트에 적합하다.
 */
@Entity
@Table(name = "products")
class Product(
    /** 기본키 — AUTO_INCREMENT */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "product_id")
    val id: Int = 0,

    /** 상품명 — NOT NULL, 최대 100자 */
    @Column(nullable = false, length = 100)
    var name: String,

    /** 상품 설명 — TEXT 타입, 선택 사항 */
    @Column(columnDefinition = "TEXT")
    var description: String? = null,

    /** 가격 — NOT NULL */
    @Column(nullable = false)
    var price: Double,

    /**
     * 재고 수량
     * 주문 시 차감되고, 주문 취소 시 복원된다.
     * JPA dirty checking으로 product.stock -= quantity 만 하면 자동 UPDATE 된다.
     */
    @Column(nullable = false)
    var stock: Int = 0,

    /**
     * 소속 카테고리 (다대일 관계)
     * nullable: 카테고리 없는 상품도 허용
     * LAZY 로딩: category에 접근할 때만 DB 조회
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    var category: Category? = null,

    /** 등록 시각 — 객체 생성 시 자동 설정 */
    @Column(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now()
)
