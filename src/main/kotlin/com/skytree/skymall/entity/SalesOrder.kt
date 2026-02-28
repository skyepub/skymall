package com.skytree.skymall.entity

import jakarta.persistence.*
import java.time.LocalDateTime

/**
 * ================================================================
 * 주문 엔티티 (SalesOrder Entity)
 * ================================================================
 *
 * 사용자의 주문 정보를 담는 엔티티.
 * "Order"는 SQL 예약어이므로 "SalesOrder"로 명명한다.
 *
 * ┌──────────────────────────────────────────────────┐
 * │  sales_orders 테이블                               │
 * ├──────────────┬────────────┬───────────────────────┤
 * │ order_id(PK) │ INT        │ 자동 증가              │
 * │ user_id(FK)  │ INT        │ 주문자 외래키           │
 * │ order_date   │ DATETIME   │ 주문 시각              │
 * │ total_amount │ DOUBLE     │ NOT NULL (총 금액)     │
 * └──────────────┴────────────┴───────────────────────┘
 *
 * ── 관계 구조 ──
 *
 *   User ──────< SalesOrder ──────< OrderItem >────── Product
 *   (1)          (N)                (N)                (1)
 *
 *   한 사용자가 여러 주문을 하고,
 *   한 주문에 여러 주문항목이 있고,
 *   각 항목은 하나의 상품을 참조한다.
 *
 * ── @OneToMany (일대다 관계) ──
 *
 * SalesOrder(1) ──< OrderItem(N)
 *
 * mappedBy = "order"
 *   관계의 주인(외래키)이 OrderItem.order 필드에 있다는 뜻이다.
 *   JPA에서 양방향 관계 시, 외래키를 가진 쪽이 "관계의 주인"이다.
 *   → OrderItem 테이블에 order_id 외래키가 존재한다.
 *
 * cascade = [CascadeType.ALL]
 *   부모(SalesOrder) 엔티티의 영속성 작업이 자식(OrderItem)에게 전파된다.
 *   - PERSIST: 주문 저장 시 → 주문항목도 함께 저장
 *   - MERGE:   주문 수정 시 → 주문항목도 함께 수정
 *   - REMOVE:  주문 삭제 시 → 주문항목도 함께 삭제
 *   - ALL = PERSIST + MERGE + REMOVE + REFRESH + DETACH
 *
 * orphanRemoval = true
 *   고아 객체 제거: order.items에서 항목을 제거하면
 *   해당 OrderItem이 DB에서도 자동 삭제된다.
 *   예: order.items.removeAt(0) → DELETE 쿼리 자동 실행
 *
 * ── MutableList ──
 * JPA 컬렉션은 변경 가능한 리스트(MutableList)를 사용해야 한다.
 * Hibernate가 내부적으로 PersistentBag으로 래핑하여 변경을 추적한다.
 */
@Entity
@Table(name = "sales_orders")
class SalesOrder(
    /** 기본키 — AUTO_INCREMENT */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_id")
    val id: Int = 0,

    /**
     * 주문자 (다대일 관계)
     * 여러 주문이 한 사용자에게 속한다.
     * LAZY: 주문 조회 시 사용자 정보는 필요할 때만 로딩
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    var user: User? = null,

    /** 주문 시각 — 객체 생성 시 자동 설정 */
    @Column(name = "order_date")
    val orderDate: LocalDateTime = LocalDateTime.now(),

    /** 주문 총 금액 — 모든 주문항목의 소계 합산 */
    @Column(name = "total_amount", nullable = false)
    var totalAmount: Double,

    /**
     * 주문 항목 목록 (일대다 관계)
     * cascade ALL: 주문 저장/삭제 시 항목도 함께 처리
     * orphanRemoval: 리스트에서 제거된 항목은 DB에서도 삭제
     */
    @OneToMany(mappedBy = "order", cascade = [CascadeType.ALL], orphanRemoval = true)
    val items: MutableList<OrderItem> = mutableListOf()
)
