package com.skytree.skymall.dto

import com.skytree.skymall.entity.Category
import com.skytree.skymall.entity.Product
import java.time.LocalDateTime

/**
 * ================================================================
 * 카탈로그 관련 DTO (상품 + 카테고리)
 * ================================================================
 *
 * Request DTO: 클라이언트 → 서버 (생성/수정 요청)
 * Response DTO: 서버 → 클라이언트 (조회 결과)
 *
 * ── Partial Update 패턴 ──
 *
 * UpdateProductRequest의 모든 필드가 nullable(?)인 이유:
 * PATCH 요청에서 "변경하고 싶은 필드만" 보낼 수 있도록 하기 위함이다.
 *
 * 예: 가격만 변경 → {"price": 15000}
 *     → name, description, stock은 null → 기존값 유지
 *
 * Service에서는 req.name?.let { product.name = it } 패턴으로
 * null이 아닌 필드만 엔티티에 반영한다.
 *
 * ── companion object + from() 패턴 ──
 *
 * Entity를 DTO로 변환하는 팩토리 메서드를 companion object에 정의한다.
 * 이렇게 하면 변환 로직이 DTO 안에 캡슐화되어 유지보수가 쉽다.
 *
 * 사용: ProductResponse.from(product)
 * 대안: product.toResponse() 확장 함수도 가능하지만, DTO에 로직을 모으는 게 일반적이다.
 */

// ════════════════════════════════════════
//  Request DTO — 클라이언트 → 서버
// ════════════════════════════════════════

/**
 * 카테고리 생성 요청
 * JSON: {"name": "전자제품"}
 */
data class CreateCategoryRequest(
    val name: String   // 카테고리 이름 (중복 시 DuplicateException)
)

/**
 * 상품 생성 요청
 *
 * JSON 예시:
 * {
 *   "name": "맥북 프로 14",
 *   "description": "M3 Max 칩 탑재",
 *   "price": 3990000,
 *   "stock": 50,
 *   "categoryId": 1           ← 생략 가능 (카테고리 없는 상품)
 * }
 *
 * Kotlin 기본 인자: description, stock, categoryId는 생략 가능
 */
data class CreateProductRequest(
    val name: String,
    val description: String? = null,   // 상세 설명 (선택)
    val price: Double,                 // 가격 (필수)
    val stock: Int = 0,                // 초기 재고 (기본: 0)
    val categoryId: Int? = null        // 카테고리 ID (선택)
)

/**
 * 상품 수정 요청 (Partial Update)
 *
 * 모든 필드가 nullable — 변경할 필드만 전송하면 된다.
 *
 * JSON 예시 (가격만 변경):
 * {"price": 4290000}
 *
 * JSON 예시 (이름과 카테고리 변경):
 * {"name": "맥북 에어", "categoryId": 2}
 */
data class UpdateProductRequest(
    val name: String? = null,
    val description: String? = null,
    val price: Double? = null,
    val stock: Int? = null,
    val categoryId: Int? = null
)

/**
 * 재입고 요청
 * JSON: {"quantity": 100}
 * 기존 재고에 추가되는 수량 (0 이하면 BusinessException)
 */
data class RestockRequest(
    val quantity: Int  // 추가할 수량
)

// ════════════════════════════════════════
//  Response DTO — 서버 → 클라이언트
// ════════════════════════════════════════

/**
 * 카테고리 응답 DTO
 *
 * JSON 예시:
 * {"id": 1, "name": "전자제품"}
 */
data class CategoryResponse(
    val id: Int,
    val name: String
) {
    companion object {
        /**
         * Entity → DTO 변환 팩토리 메서드
         * 사용: CategoryResponse.from(category)
         */
        fun from(category: Category) = CategoryResponse(
            id = category.id,
            name = category.name
        )
    }
}

/**
 * 상품 응답 DTO
 *
 * JSON 예시:
 * {
 *   "id": 1,
 *   "name": "맥북 프로 14",
 *   "description": "M3 Max",
 *   "price": 3990000,
 *   "stock": 50,
 *   "category": {"id": 1, "name": "전자제품"},  ← 중첩 DTO
 *   "createdAt": "2026-02-28T10:30:00"
 * }
 *
 * category가 null이면 JSON에서도 null로 표시된다.
 */
data class ProductResponse(
    val id: Int,
    val name: String,
    val description: String?,
    val price: Double,
    val stock: Int,
    val category: CategoryResponse?,    // 중첩 DTO (카테고리 없으면 null)
    val createdAt: LocalDateTime
) {
    companion object {
        /**
         * Entity → DTO 변환
         *
         * product.category?.let { ... }:
         * 카테고리가 null이 아닐 때만 CategoryResponse로 변환
         * → Kotlin의 안전 호출 연산자(?.)와 let을 활용한 null 안전 변환
         */
        fun from(product: Product) = ProductResponse(
            id = product.id,
            name = product.name,
            description = product.description,
            price = product.price,
            stock = product.stock,
            category = product.category?.let { CategoryResponse.from(it) },
            createdAt = product.createdAt
        )
    }
}

/**
 * 카테고리 통계 응답 DTO
 *
 * JSON 예시:
 * {
 *   "category": {"id": 1, "name": "전자제품"},
 *   "productCount": 15,
 *   "avgPrice": 250000.0
 * }
 */
data class CategorySummaryResponse(
    val category: CategoryResponse,    // 카테고리 정보
    val productCount: Long,            // 소속 상품 수
    val avgPrice: Double?              // 평균 가격 (상품 없으면 null)
)
