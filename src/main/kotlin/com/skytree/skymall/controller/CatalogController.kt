package com.skytree.skymall.controller

import com.skytree.skymall.dto.*
import com.skytree.skymall.service.CatalogService
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api")
class CatalogController(
    private val catalogService: CatalogService
) {
    // ── Categories ──

    @GetMapping("/categories")
    fun getAllCategories(): List<CategoryResponse> =
        catalogService.getAllCategories()

    @GetMapping("/categories/{id}")
    fun getCategory(@PathVariable id: Int): CategoryResponse =
        catalogService.getCategory(id)

    @GetMapping("/categories/{id}/products")
    fun getProductsByCategory(
        @PathVariable id: Int,
        @PageableDefault(size = 20) pageable: Pageable
    ): Page<ProductResponse> = catalogService.getProductsByCategory(id, pageable)

    @GetMapping("/categories/summary")
    fun getCategorySummary(): List<CategorySummaryResponse> =
        catalogService.getCategorySummary()

    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    fun createCategory(@RequestBody req: CreateCategoryRequest): CategoryResponse =
        catalogService.createCategory(req)

    @DeleteMapping("/categories/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteCategory(@PathVariable id: Int) =
        catalogService.deleteCategory(id)

    // ── Products ──

    @GetMapping("/products")
    fun getAllProducts(
        @PageableDefault(size = 20) pageable: Pageable
    ): Page<ProductResponse> = catalogService.getAllProducts(pageable)

    @GetMapping("/products/{id}")
    fun getProduct(@PathVariable id: Int): ProductResponse =
        catalogService.getProduct(id)

    @GetMapping("/products/search")
    fun searchProducts(
        @RequestParam keyword: String,
        @PageableDefault(size = 20) pageable: Pageable
    ): Page<ProductResponse> = catalogService.searchProducts(keyword, pageable)

    @GetMapping("/products/price-range")
    fun getProductsByPriceRange(
        @RequestParam min: Double,
        @RequestParam max: Double,
        @PageableDefault(size = 20) pageable: Pageable
    ): Page<ProductResponse> = catalogService.getProductsByPriceRange(min, max, pageable)

    @GetMapping("/products/low-stock")
    fun getLowStockProducts(
        @RequestParam(defaultValue = "5") threshold: Int,
        @PageableDefault(size = 20) pageable: Pageable
    ): Page<ProductResponse> = catalogService.getLowStockProducts(threshold, pageable)

    @GetMapping("/products/unsold")
    fun getUnsoldProducts(
        @PageableDefault(size = 20) pageable: Pageable
    ): Page<ProductResponse> = catalogService.getUnsoldProducts(pageable)

    @PostMapping("/products")
    @ResponseStatus(HttpStatus.CREATED)
    fun createProduct(@RequestBody req: CreateProductRequest): ProductResponse =
        catalogService.createProduct(req)

    @PatchMapping("/products/{id}")
    fun updateProduct(
        @PathVariable id: Int,
        @RequestBody req: UpdateProductRequest
    ): ProductResponse = catalogService.updateProduct(id, req)

    @PatchMapping("/products/{id}/restock")
    fun restockProduct(
        @PathVariable id: Int,
        @RequestBody req: RestockRequest
    ): ProductResponse = catalogService.restockProduct(id, req.quantity)

    @DeleteMapping("/products/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteProduct(@PathVariable id: Int) =
        catalogService.deleteProduct(id)
}
