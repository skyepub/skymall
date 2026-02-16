package com.skytree.skymall.service

import com.skytree.skymall.dto.*
import com.skytree.skymall.entity.Category
import com.skytree.skymall.entity.Product
import com.skytree.skymall.exception.BusinessException
import com.skytree.skymall.exception.DuplicateException
import com.skytree.skymall.exception.EntityNotFoundException
import com.skytree.skymall.repository.CategoryRepository
import com.skytree.skymall.repository.ProductRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 상품 카탈로그 서비스
 *
 * ProductRepository + CategoryRepository를 복합 사용.
 * 조회는 readOnly, 변경은 쓰기 트랜잭션.
 */
@Service
@Transactional(readOnly = true)
class CatalogService(
    private val productRepo: ProductRepository,
    private val categoryRepo: CategoryRepository
) {
    // ══════════════════════════════════════
    //  Category
    // ══════════════════════════════════════

    fun getAllCategories(): List<CategoryResponse> =
        categoryRepo.findAll().map { CategoryResponse.from(it) }

    fun getCategory(id: Int): CategoryResponse =
        CategoryResponse.from(findCategoryOrThrow(id))

    @Transactional
    fun createCategory(req: CreateCategoryRequest): CategoryResponse {
        // 비즈니스 검증: 중복 이름
        categoryRepo.findByName(req.name)?.let {
            throw DuplicateException("카테고리명", req.name)
        }
        val saved = categoryRepo.save(Category(name = req.name))
        return CategoryResponse.from(saved)
    }

    @Transactional
    fun deleteCategory(id: Int) {
        val count = productRepo.countByCategoryId(id)
        if (count > 0) {
            throw BusinessException("카테고리에 ${count}개의 상품이 있어 삭제할 수 없습니다")
        }
        categoryRepo.deleteById(id)
    }

    // ══════════════════════════════════════
    //  Product CRUD
    // ══════════════════════════════════════

    fun getAllProducts(pageable: Pageable): Page<ProductResponse> =
        productRepo.findAll(pageable).map { ProductResponse.from(it) }

    fun getProduct(id: Int): ProductResponse =
        ProductResponse.from(findProductOrThrow(id))

    /**
     * 상품 등록 — CategoryRepo에서 카테고리를 조회하여 Product에 연결
     */
    @Transactional
    fun createProduct(req: CreateProductRequest): ProductResponse {
        val category = req.categoryId?.let { findCategoryOrThrow(it) }
        val product = productRepo.save(
            Product(
                name = req.name,
                description = req.description,
                price = req.price,
                stock = req.stock,
                category = category
            )
        )
        return ProductResponse.from(product)
    }

    /**
     * 상품 수정 — null이 아닌 필드만 변경 (Partial Update)
     * dirty checking으로 자동 저장된다.
     */
    @Transactional
    fun updateProduct(id: Int, req: UpdateProductRequest): ProductResponse {
        val product = findProductOrThrow(id)
        req.name?.let { product.name = it }
        req.description?.let { product.description = it }
        req.price?.let { product.price = it }
        req.stock?.let { product.stock = it }
        req.categoryId?.let { product.category = findCategoryOrThrow(it) }
        return ProductResponse.from(product)
    }

    @Transactional
    fun deleteProduct(id: Int) {
        findProductOrThrow(id)
        productRepo.deleteById(id)
    }

    /**
     * 재고 보충 — 기존 재고에 수량을 더한다
     */
    @Transactional
    fun restockProduct(id: Int, quantity: Int): ProductResponse {
        if (quantity <= 0) throw BusinessException("보충 수량은 1 이상이어야 합니다")
        val product = findProductOrThrow(id)
        product.stock += quantity
        return ProductResponse.from(product)
    }

    // ══════════════════════════════════════
    //  복합 조회 — 두 Repo를 함께 사용
    // ══════════════════════════════════════

    fun getProductsByCategory(categoryId: Int, pageable: Pageable): Page<ProductResponse> {
        findCategoryOrThrow(categoryId)  // 존재 여부 검증
        return productRepo.findByCategoryId(categoryId, pageable).map { ProductResponse.from(it) }
    }

    fun getProductsByCategoryName(name: String): List<ProductResponse> =
        productRepo.findByCategoryName(name).map { ProductResponse.from(it) }

    fun searchProducts(keyword: String, pageable: Pageable): Page<ProductResponse> =
        productRepo.searchByKeyword(keyword, pageable).map { ProductResponse.from(it) }

    fun getProductsByPriceRange(min: Double, max: Double, pageable: Pageable): Page<ProductResponse> =
        productRepo.findByPriceBetween(min, max, pageable).map { ProductResponse.from(it) }

    fun getLowStockProducts(threshold: Int = 5, pageable: Pageable): Page<ProductResponse> =
        productRepo.findByStockLessThan(threshold, pageable).map { ProductResponse.from(it) }

    fun getUnsoldProducts(pageable: Pageable): Page<ProductResponse> =
        productRepo.findUnsoldProducts(pageable).map { ProductResponse.from(it) }

    /**
     * 카테고리별 통계 — CategoryRepo.findAll() + ProductRepo 집계 함수 복합
     */
    fun getCategorySummary(): List<CategorySummaryResponse> =
        categoryRepo.findAll().map { category ->
            CategorySummaryResponse(
                category = CategoryResponse.from(category),
                productCount = productRepo.countByCategoryId(category.id),
                avgPrice = productRepo.avgPriceByCategoryId(category.id)
            )
        }

    // ══════════════════════════════════════
    //  내부 헬퍼 — Entity를 반환 (Service 내부에서만 사용)
    // ══════════════════════════════════════

    internal fun findProductOrThrow(id: Int): Product =
        productRepo.findById(id)
            .orElseThrow { EntityNotFoundException("상품", id) }

    private fun findCategoryOrThrow(id: Int): Category =
        categoryRepo.findById(id)
            .orElseThrow { EntityNotFoundException("카테고리", id) }
}
