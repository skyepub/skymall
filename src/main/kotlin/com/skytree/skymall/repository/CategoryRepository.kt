package com.skytree.skymall.repository

import com.skytree.skymall.entity.Category
import org.springframework.data.jpa.repository.JpaRepository

/**
 * ================================================================
 * 카테고리 리포지토리 (CategoryRepository)
 * ================================================================
 *
 * 가장 단순한 형태의 JPA Repository 예제.
 * JpaRepository가 기본 제공하는 CRUD + 파생 쿼리 1개.
 *
 * JpaRepository<Category, Int>
 *   - Category: 엔티티 클래스
 *   - Int: 기본키(category_id) 타입
 *
 * 기본 제공 메서드만으로도 대부분의 CRUD가 가능하다:
 *   save(), findById(), findAll(), deleteById(), count() 등
 */
interface CategoryRepository : JpaRepository<Category, Int> {

    /**
     * 카테고리명으로 조회
     * 생성되는 SQL: SELECT * FROM categories WHERE name = ?
     * 반환: 해당 카테고리 또는 null (unique이므로 최대 1개)
     * 용도: 카테고리 생성 시 중복 이름 체크
     */
    fun findByName(name: String): Category?
}
