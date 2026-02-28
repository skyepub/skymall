package com.skytree.skymall.entity

import jakarta.persistence.*

/**
 * ================================================================
 * 카테고리 엔티티 (Category Entity)
 * ================================================================
 *
 * 상품을 분류하는 카테고리 테이블.
 * 가장 단순한 형태의 JPA 엔티티 예제이다.
 *
 * ┌──────────────────────────────────────────┐
 * │  categories 테이블                         │
 * ├────────────────┬────────────┬─────────────┤
 * │ category_id(PK)│ INT        │ 자동 증가    │
 * │ name           │ VARCHAR(50)│ NOT NULL, UNIQUE │
 * └────────────────┴────────────┴─────────────┘
 *
 * ── 관계 ──
 * Category ←──── Product (1:N)
 * 하나의 카테고리에 여러 상품이 속할 수 있다.
 * 관계의 주인(외래키)은 Product 쪽에 있다. (Product.category)
 *
 * ── unique = true ──
 * 동일한 이름의 카테고리를 중복 생성할 수 없다.
 * DB 레벨에서 UNIQUE 제약조건이 걸린다.
 */
@Entity
@Table(name = "categories")
class Category(
    /** 기본키 — AUTO_INCREMENT */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "category_id")
    val id: Int = 0,

    /** 카테고리 이름 — 중복 불가, 최대 50자 */
    @Column(nullable = false, unique = true, length = 50)
    var name: String
)
