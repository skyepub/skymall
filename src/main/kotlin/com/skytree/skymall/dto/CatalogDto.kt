package com.skytree.skymall.dto

import com.skytree.skymall.entity.Category
import com.skytree.skymall.entity.Product
import java.time.LocalDateTime

// ── Request ──

data class CreateCategoryRequest(
    val name: String
)

data class CreateProductRequest(
    val name: String,
    val description: String? = null,
    val price: Double,
    val stock: Int = 0,
    val categoryId: Int? = null
)

data class UpdateProductRequest(
    val name: String? = null,
    val description: String? = null,
    val price: Double? = null,
    val stock: Int? = null,
    val categoryId: Int? = null
)

data class RestockRequest(
    val quantity: Int
)

// ── Response ──

data class CategoryResponse(
    val id: Int,
    val name: String
) {
    companion object {
        fun from(category: Category) = CategoryResponse(
            id = category.id,
            name = category.name
        )
    }
}

data class ProductResponse(
    val id: Int,
    val name: String,
    val description: String?,
    val price: Double,
    val stock: Int,
    val category: CategoryResponse?,
    val createdAt: LocalDateTime
) {
    companion object {
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

data class CategorySummaryResponse(
    val category: CategoryResponse,
    val productCount: Long,
    val avgPrice: Double?
)
