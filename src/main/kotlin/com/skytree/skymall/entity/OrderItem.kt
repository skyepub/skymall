package com.skytree.skymall.entity

import jakarta.persistence.*

/**
 * ================================================================
 * 주문항목 엔티티 (OrderItem Entity)
 * ================================================================
 *
 * 주문 1건에 포함된 개별 상품 항목.
 * 주문(SalesOrder)과 상품(Product) 사이의 "다대다 관계"를
 * "중간 테이블 엔티티"로 풀어낸 패턴이다.
 *
 * ┌──────────────────────────────────────────────────────┐
 * │  order_items 테이블                                    │
 * ├────────────────────┬────────────┬─────────────────────┤
 * │ order_item_id(PK)  │ BIGINT     │ 자동 증가            │
 * │ order_id(FK)       │ INT        │ 주문 외래키           │
 * │ product_id(FK)     │ INT        │ 상품 외래키           │
 * │ quantity           │ INT        │ NOT NULL (주문 수량)  │
 * │ price_per_item     │ DOUBLE     │ NOT NULL (주문 시점 단가) │
 * └────────────────────┴────────────┴─────────────────────┘
 *
 * ── 왜 다대다(ManyToMany)를 쓰지 않는가? ──
 *
 * 이론적으로 Order ↔ Product는 다대다(M:N) 관계이다:
 *   - 하나의 주문에 여러 상품이 포함됨
 *   - 하나의 상품이 여러 주문에 포함됨
 *
 * @ManyToMany를 사용하면 JPA가 자동으로 중간 테이블을 만들지만,
 * 추가 컬럼(quantity, pricePerItem)을 넣을 수 없다.
 *
 * 따라서 실무에서는 중간 테이블을 직접 엔티티로 만들어서
 * 두 개의 @ManyToOne 관계로 풀어낸다:
 *
 *   SalesOrder (1) ──< OrderItem (N) >── (1) Product
 *                         │
 *                    quantity, pricePerItem 등 추가 정보
 *
 * ── pricePerItem (주문 시점 가격 스냅샷) ──
 * 상품 가격은 수시로 변할 수 있다.
 * 주문 시점의 가격을 별도로 저장해야 나중에 "그때 얼마였는지" 알 수 있다.
 * product.price를 직접 참조하면 가격 변경 시 과거 주문 금액도 바뀌게 된다!
 *
 * ── Long vs Int ──
 * id 타입이 Long(BIGINT)인 이유: 주문항목은 주문 수 × 항목 수로
 * 데이터가 빠르게 증가하므로 INT 범위(약 21억)를 넘을 수 있다.
 */
@Entity
@Table(name = "order_items")
class OrderItem(
    /** 기본키 — BIGINT AUTO_INCREMENT (대량 데이터 대비) */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_item_id")
    val id: Long = 0,

    /**
     * 소속 주문 (다대일 관계)
     * 이 필드가 관계의 주인: order_items 테이블에 order_id FK가 존재한다.
     * SalesOrder.items의 mappedBy = "order"가 이 필드를 가리킨다.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    var order: SalesOrder? = null,

    /**
     * 주문한 상품 (다대일 관계)
     * LAZY: 주문항목 조회 시 상품 정보는 필요할 때만 로딩
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    var product: Product? = null,

    /** 주문 수량 */
    @Column(nullable = false)
    var quantity: Int,

    /**
     * 주문 시점의 상품 단가 (가격 스냅샷)
     * 상품 가격이 변경되어도 이미 완료된 주문의 금액은 보존된다.
     */
    @Column(name = "price_per_item", nullable = false)
    var pricePerItem: Double
)
