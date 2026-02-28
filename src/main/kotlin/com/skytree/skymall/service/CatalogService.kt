package com.skytree.skymall.service

// ── import 설명 ──
// dto 패키지의 모든 DTO 클래스를 가져온다 (star import).
// 이 서비스에서 사용하는 DTO:
//   CategoryResponse, CreateCategoryRequest, CategorySummaryResponse,
//   ProductResponse, CreateProductRequest, UpdateProductRequest, RestockRequest
import com.skytree.skymall.dto.*
// JPA 엔티티: 데이터베이스 테이블과 1:1 매핑되는 클래스
import com.skytree.skymall.entity.Category   // categories 테이블
import com.skytree.skymall.entity.Product    // products 테이블
// 커스텀 예외 클래스: GlobalExceptionHandler에서 HTTP 상태 코드로 변환
import com.skytree.skymall.exception.BusinessException       // -> 400 Bad Request
import com.skytree.skymall.exception.DuplicateException      // -> 409 Conflict
import com.skytree.skymall.exception.EntityNotFoundException // -> 404 Not Found
// Spring Data JPA Repository: DB 접근 인터페이스 (구현체는 Spring이 자동 생성)
import com.skytree.skymall.repository.CategoryRepository
import com.skytree.skymall.repository.ProductRepository
// Spring Data의 페이징 관련 클래스
import org.springframework.data.domain.Page     // 페이징된 결과 컨테이너
import org.springframework.data.domain.Pageable // 페이징 요청 정보
// Spring 어노테이션
import org.springframework.stereotype.Service              // 서비스 빈 등록
import org.springframework.transaction.annotation.Transactional // 트랜잭션 관리

/**
 * ================================================================
 * 카탈로그 서비스 (CatalogService)
 * ================================================================
 *
 * 상품(Product)과 카테고리(Category)의 비즈니스 로직을 담당하는 서비스 계층.
 *
 * ── 왜 상품과 카테고리를 하나의 서비스에서 처리하나? ──
 *
 * 상품과 카테고리는 밀접하게 관련된 도메인이다:
 * - 상품 등록 시 카테고리를 지정한다 (Product -> Category)
 * - 카테고리 삭제 시 해당 카테고리의 상품 수를 확인한다
 * - 카테고리별 상품 조회, 카테고리별 통계 등 조합 쿼리가 많다
 *
 * 따라서 두 Repository를 하나의 서비스에서 관리하는 것이 자연스럽다.
 * 만약 각각 별도 서비스로 분리하면, 상호 참조가 발생하여 순환 의존성 문제가 생길 수 있다.
 *
 * ── 의존성 구조 ──
 *
 *   CatalogService
 *     +-- ProductRepository    : 상품 CRUD + 검색 쿼리 (products 테이블)
 *     +-- CategoryRepository   : 카테고리 CRUD (categories 테이블)
 *
 * ── 서비스 계층의 4가지 역할 (이 클래스에서 모두 볼 수 있다) ──
 *
 * 1. 비즈니스 검증:
 *    - 카테고리 이름 중복 체크 (createCategory)
 *    - 상품이 있는 카테고리 삭제 방지 (deleteCategory)
 *    - 재고 보충 수량 양수 검증 (restockProduct)
 *
 * 2. 트랜잭션 관리:
 *    - @Transactional로 DB 작업의 원자성 보장
 *    - 읽기 전용 vs 읽기+쓰기 트랜잭션 분리
 *
 * 3. Entity <-> DTO 변환:
 *    - DB의 Entity를 API 응답용 DTO로 변환
 *    - Entity의 민감한 정보(내부 ID 등)를 DTO에서 제어
 *
 * 4. 복수 Repository 조합:
 *    - 카테고리별 통계: CategoryRepo + ProductRepo 결합
 *    - 상품 등록: ProductRepo + CategoryRepo (카테고리 검증)
 *
 * ── @Transactional(readOnly = true) — 클래스 레벨 ──
 *
 * 모든 메서드의 기본 트랜잭션 설정.
 * 쓰기가 필요한 메서드에만 @Transactional을 개별 선언하여 오버라이드.
 *
 * readOnly = true의 효과:
 * - Hibernate: dirty checking 생략, 스냅샷 미생성 -> 메모리/CPU 절약
 * - JDBC: readOnly 힌트로 DB 최적화 (Replica 라우팅 등)
 * - flush: 자동 flush 비활성화로 불필요한 DB 쓰기 방지
 *
 * ── Dirty Checking (변경 감지) — 이 서비스의 핵심 개념 ──
 *
 * JPA가 managed 상태의 엔티티 변경을 자동으로 감지하여 UPDATE 쿼리를 실행한다.
 *
 * 동작 흐름:
 * 1. 트랜잭션 시작 시 엔티티를 DB에서 로드 → 원본 스냅샷 보관
 * 2. 코드에서 엔티티 필드 값 변경 (예: product.name = "새이름")
 * 3. 트랜잭션 커밋 시 현재 상태와 스냅샷 비교
 * 4. 변경된 필드만 UPDATE 쿼리에 포함하여 실행
 *
 * 따라서 save()를 다시 호출하지 않아도 변경이 DB에 반영된다!
 * 이 패턴은 updateProduct(), restockProduct()에서 사용된다.
 */
@Service
@Transactional(readOnly = true)
class CatalogService(
    private val productRepo: ProductRepository,     // products 테이블 접근
    private val categoryRepo: CategoryRepository    // categories 테이블 접근
) {

    // ════════════════════════════════════════════════════════════════
    //  카테고리 조회 (Category Read)
    // ════════════════════════════════════════════════════════════════

    /**
     * 모든 카테고리 목록 조회
     *
     * ── categoryRepo.findAll() ──
     *
     * JpaRepository가 제공하는 기본 메서드.
     * → SELECT * FROM categories
     * 반환: List<Category> (전체 카테고리 목록)
     *
     * 카테고리는 보통 수가 적으므로(수십 개 이내) 페이징 없이 전체를 반환한다.
     * 만약 카테고리가 수백 개 이상이라면 페이징을 적용하는 것이 좋다.
     *
     * ── .map { CategoryResponse.from(it) } ──
     *
     * Kotlin의 Collection.map() 함수: 각 요소를 변환하여 새 리스트를 만든다.
     * - it: 람다의 단일 파라미터 기본 이름 (여기서는 Category 엔티티)
     * - CategoryResponse.from(it): Entity -> DTO 변환 (companion object의 정적 메서드)
     *
     * 결과: List<CategoryResponse> (DTO 리스트)
     *
     * @return List<CategoryResponse> — 전체 카테고리 목록 (DTO)
     */
    fun getAllCategories(): List<CategoryResponse> =
        categoryRepo.findAll()                         // List<Category> 조회
            .map { CategoryResponse.from(it) }         // List<CategoryResponse>로 변환

    /**
     * 카테고리 단건 조회
     *
     * findCategoryOrThrow(id):
     *   이 클래스의 private 헬퍼 메서드.
     *   ID로 Category 엔티티를 조회하고, 없으면 404 예외를 던진다.
     *
     * @param id 조회할 카테고리의 PK (categories 테이블의 id 컬럼)
     * @return CategoryResponse DTO
     * @throws EntityNotFoundException 해당 ID의 카테고리가 없을 때 (404)
     */
    fun getCategory(id: Int): CategoryResponse =
        CategoryResponse.from(findCategoryOrThrow(id))

    // ════════════════════════════════════════════════════════════════
    //  카테고리 생성/삭제 (Category Write)
    // ════════════════════════════════════════════════════════════════

    /**
     * 카테고리 생성
     *
     * ── 비즈니스 검증: 이름 중복 체크 ──
     *
     * 같은 이름의 카테고리가 이미 존재하면 DuplicateException (409 Conflict).
     * DB의 UNIQUE 제약과 별개로 Service에서 먼저 검증하는 이유:
     * - 더 의미 있는 에러 메시지를 제공하기 위함
     * - DB 예외는 보통 500으로 변환되지만, 우리는 409를 반환하고 싶다
     *
     * ── categoryRepo.findByName(req.name) ──
     *
     * CategoryRepository의 파생 쿼리 메서드.
     * 메서드명을 파싱하여 자동 생성되는 SQL:
     * → SELECT * FROM categories WHERE name = ?
     * 반환: Category? (없으면 null)
     *
     * ── ?.let { throw ... } ──
     *
     * null이 아니면 (= 이미 존재하면) 예외를 던진다.
     * null이면 (= 존재하지 않으면) 아무 일도 일어나지 않고 다음 줄로 진행.
     *
     * ── categoryRepo.save(Category(name = req.name)) ──
     *
     * 새 엔티티이므로 INSERT 실행:
     * → INSERT INTO categories (name) VALUES (?)
     * DB가 auto_increment로 id를 생성하고, save()가 id가 설정된 엔티티를 반환.
     *
     * @param req CreateCategoryRequest (name: String)
     * @return CategoryResponse DTO (생성된 카테고리 정보)
     * @throws DuplicateException 같은 이름의 카테고리가 이미 있을 때 (409)
     */
    @Transactional
    fun createCategory(req: CreateCategoryRequest): CategoryResponse {
        // 이름 중복 검사: 이미 존재하면 409 Conflict
        categoryRepo.findByName(req.name)?.let {
            throw DuplicateException("카테고리명", req.name)
        }
        // 새 카테고리 엔티티 생성 + DB 저장
        val saved = categoryRepo.save(Category(name = req.name))
        // Entity -> DTO 변환 후 반환
        return CategoryResponse.from(saved)
    }

    /**
     * 카테고리 삭제
     *
     * ── 비즈니스 검증: 상품 포함 여부 ──
     *
     * 카테고리에 상품이 하나라도 있으면 삭제를 차단한다.
     * 이유:
     * - Product 엔티티가 Category를 @ManyToOne으로 참조하고 있다
     * - 카테고리를 삭제하면 상품들이 "카테고리 없음" 상태가 된다
     * - 이는 데이터 무결성(Referential Integrity) 위반이다
     *
     * 해결 방법:
     * 1. 카테고리의 상품을 먼저 다른 카테고리로 이동
     * 2. 카테고리의 상품을 먼저 삭제
     * 3. 그 후에 빈 카테고리를 삭제
     *
     * ── productRepo.countByCategoryId(id) ──
     *
     * ProductRepository의 파생 쿼리 메서드.
     * → SELECT COUNT(*) FROM products WHERE category_id = ?
     * 반환: Long (상품 개수)
     *
     * ── categoryRepo.deleteById(id) ──
     *
     * JpaRepository의 기본 메서드.
     * 내부적으로: SELECT -> remove -> DELETE FROM categories WHERE id = ?
     *
     * @param id 삭제할 카테고리의 PK
     * @throws BusinessException 카테고리에 상품이 있을 때 (400)
     */
    @Transactional
    fun deleteCategory(id: Int) {
        // 상품 개수 확인: 상품이 있으면 삭제 불가
        val count = productRepo.countByCategoryId(id)
        if (count > 0) {
            throw BusinessException("카테고리에 ${count}개의 상품이 있어 삭제할 수 없습니다")
        }
        // 상품이 없는 빈 카테고리만 삭제 가능
        categoryRepo.deleteById(id)
    }

    // ════════════════════════════════════════════════════════════════
    //  상품 조회 (Product Read)
    // ════════════════════════════════════════════════════════════════

    /**
     * 전체 상품 목록 조회 (페이징)
     *
     * ── productRepo.findAll(pageable) ──
     *
     * JpaRepository가 제공하는 기본 메서드에 Pageable을 전달하면 페이징 처리된다.
     *
     * 실행되는 SQL (2개):
     * 1. SELECT * FROM products ORDER BY ... LIMIT ? OFFSET ?
     *    (현재 페이지의 데이터 조회)
     * 2. SELECT COUNT(*) FROM products
     *    (전체 건수 조회 - 전체 페이지 수 계산용)
     *
     * 반환: Page<Product>
     * Page 객체가 포함하는 정보:
     * - content: List<Product> — 현재 페이지의 데이터
     * - totalElements: Long — 전체 데이터 수 (예: 150)
     * - totalPages: Int — 전체 페이지 수 (예: 150/20 = 8)
     * - number: Int — 현재 페이지 번호 (0-based. 예: 0 = 첫 페이지)
     * - size: Int — 페이지 크기 (예: 20)
     * - first: Boolean — 첫 페이지인지
     * - last: Boolean — 마지막 페이지인지
     *
     * ── .map { ProductResponse.from(it) } ──
     *
     * Page.map()은 Kotlin Collection의 map()과 다르다!
     * Page.map()은 데이터(content)만 변환하고, 페이지 메타 정보는 그대로 유지한다.
     *
     * 결과: Page<ProductResponse>
     *   content = List<ProductResponse> (DTO)
     *   totalElements, totalPages 등 = 원래 값 유지
     *
     * @param pageable 페이징 정보 (page, size, sort)
     * @return Page<ProductResponse> — 페이징된 상품 목록
     */
    fun getAllProducts(pageable: Pageable): Page<ProductResponse> =
        productRepo.findAll(pageable)                  // Page<Product> 조회
            .map { ProductResponse.from(it) }          // Page<ProductResponse>로 변환

    /**
     * 상품 단건 조회
     *
     * findProductOrThrow(id):
     *   이 클래스의 internal 헬퍼 메서드.
     *   OrderService에서도 재사용한다 (재고 차감 시 상품 검증).
     *
     * @param id 조회할 상품의 PK (products 테이블의 id 컬럼)
     * @return ProductResponse DTO
     * @throws EntityNotFoundException 해당 ID의 상품이 없을 때 (404)
     */
    fun getProduct(id: Int): ProductResponse =
        ProductResponse.from(findProductOrThrow(id))

    // ════════════════════════════════════════════════════════════════
    //  상품 생성 (Product Create)
    // ════════════════════════════════════════════════════════════════

    /**
     * 상품 등록
     *
     * ── 카테고리 연결 (선택적) ──
     *
     * 상품은 카테고리에 속할 수도 있고, 속하지 않을 수도 있다.
     * (Product.category는 nullable: Category?)
     *
     * req.categoryId?.let { findCategoryOrThrow(it) }:
     *   categoryId가 null이 아니면:
     *     → findCategoryOrThrow(categoryId) 호출
     *     → Category 엔티티 반환 (없으면 404)
     *   categoryId가 null이면:
     *     → let 블록이 실행되지 않고 null 반환
     *     → Product.category = null (카테고리 없는 상품)
     *
     * 이것이 Kotlin의 ?.let 패턴의 강력함이다.
     * Java에서는 if-else 분기가 필요하지만, Kotlin에서는 한 줄로 해결된다.
     *
     * ── productRepo.save(Product(...)) ──
     *
     * 새 엔티티이므로 INSERT 실행:
     * → INSERT INTO products (name, description, price, stock, category_id)
     *   VALUES (?, ?, ?, ?, ?)
     *
     * category가 null이면 category_id도 NULL로 저장된다.
     *
     * @param req CreateProductRequest (name, description, price, stock, categoryId?)
     * @return ProductResponse DTO (생성된 상품 정보)
     * @throws EntityNotFoundException categoryId가 지정되었으나 해당 카테고리가 없을 때 (404)
     */
    @Transactional
    fun createProduct(req: CreateProductRequest): ProductResponse {

        // 카테고리 조회 (categoryId가 있을 때만)
        // val category: Category? — null일 수 있다
        val category = req.categoryId?.let { findCategoryOrThrow(it) }

        // 상품 엔티티 생성 + DB 저장
        val product = productRepo.save(
            Product(
                name = req.name,               // 상품명
                description = req.description, // 설명
                price = req.price,             // 가격
                stock = req.stock,             // 재고 수량
                category = category            // 카테고리 (null 가능)
            )
        )

        // Entity -> DTO 변환 후 반환
        return ProductResponse.from(product)
    }

    // ════════════════════════════════════════════════════════════════
    //  상품 수정 (Product Update) — Partial Update + Dirty Checking
    // ════════════════════════════════════════════════════════════════

    /**
     * 상품 수정 — Partial Update (부분 업데이트)
     *
     * ── Partial Update 패턴 ──
     *
     * 클라이언트가 변경하고 싶은 필드만 JSON에 포함하면 된다.
     * 포함되지 않은 필드(= JSON에서 생략 = null)는 기존 값을 유지한다.
     *
     * 요청 예시:
     *   PATCH /api/products/1
     *   Body: {"price": 15000}
     *   → 가격만 변경, 이름/설명/재고/카테고리는 기존 값 유지
     *
     *   PATCH /api/products/1
     *   Body: {"name": "새이름", "stock": 100}
     *   → 이름과 재고만 변경
     *
     * ── Dirty Checking으로 save() 불필요 ──
     *
     * 이 메서드에서 productRepo.save(product)를 호출하지 않아도
     * 변경 사항이 트랜잭션 커밋 시 자동으로 DB에 반영된다.
     *
     * 실행되는 SQL:
     *   UPDATE products SET price = 15000 WHERE id = 1
     *   (변경된 필드만 SET 절에 포함)
     *
     * ── null이 아닌 필드만 반영하는 패턴 ──
     *
     * req.name?.let { product.name = it }
     *
     * 이것은 다음과 같다:
     *   if (req.name != null) {
     *       product.name = req.name
     *   }
     *
     * 왜 이 패턴을 사용하나?
     * - JSON에서 필드를 생략하면 Jackson이 null로 역직렬화한다
     * - null = "변경 의도 없음"으로 해석한다
     * - not null = "이 값으로 변경"으로 해석한다
     *
     * @param id 수정할 상품의 PK
     * @param req UpdateProductRequest (변경할 필드만 not null)
     * @return ProductResponse DTO (수정 후 상품 정보)
     * @throws EntityNotFoundException 상품 또는 카테고리가 없을 때 (404)
     */
    @Transactional
    fun updateProduct(id: Int, req: UpdateProductRequest): ProductResponse {

        // 수정 대상 상품 조회 (없으면 404)
        // 이 시점부터 product는 JPA의 "managed" 상태.
        // managed 상태 엔티티의 필드 변경은 dirty checking 대상이 된다.
        val product = findProductOrThrow(id)

        // 각 필드별 null 체크 후 변경 (Partial Update 패턴)
        req.name?.let { product.name = it }                // 상품명 변경
        req.description?.let { product.description = it }  // 설명 변경
        req.price?.let { product.price = it }              // 가격 변경
        req.stock?.let { product.stock = it }              // 재고 변경

        // 카테고리 변경: categoryId가 있으면 해당 카테고리로 변경
        // findCategoryOrThrow(it): 카테고리가 없으면 404 예외
        req.categoryId?.let { product.category = findCategoryOrThrow(it) }

        // DTO 변환 후 반환
        // save()를 호출하지 않아도 dirty checking으로 자동 UPDATE!
        return ProductResponse.from(product)
    }

    // ════════════════════════════════════════════════════════════════
    //  상품 삭제 (Product Delete)
    // ════════════════════════════════════════════════════════════════

    /**
     * 상품 삭제
     *
     * findProductOrThrow(id): 상품이 없으면 404 예외.
     * 존재하지 않는 상품을 삭제하려는 시도를 의미 있는 에러로 처리.
     *
     * productRepo.deleteById(id):
     *   → DELETE FROM products WHERE id = ?
     *
     * 주의: 이 상품을 참조하는 주문(OrderItem)이 있으면
     * DB의 외래 키 제약(FK constraint)으로 인해 삭제가 실패한다.
     * 이 경우 DataIntegrityViolationException이 발생하며,
     * 프로덕션에서는 이를 적절히 처리해야 한다.
     *
     * @param id 삭제할 상품의 PK
     * @throws EntityNotFoundException 해당 상품이 없을 때 (404)
     */
    @Transactional
    fun deleteProduct(id: Int) {
        findProductOrThrow(id)         // 존재 확인 (없으면 404)
        productRepo.deleteById(id)     // DELETE 실행
    }

    // ════════════════════════════════════════════════════════════════
    //  재고 관리 (Stock Management)
    // ════════════════════════════════════════════════════════════════

    /**
     * 재고 보충 (Restock)
     *
     * 기존 재고에 수량을 추가한다. 교체가 아닌 덧셈이다.
     * 예: 현재 재고 10개 + 보충 50개 = 재고 60개
     *
     * ── 비즈니스 검증: 양수 체크 ──
     *
     * 0 이하의 수량으로 보충하려는 시도를 차단한다.
     * 음수를 허용하면 재고 차감에 악용될 수 있다.
     *
     * ── product.stock += quantity ──
     *
     * Kotlin의 복합 대입 연산자: product.stock = product.stock + quantity
     * managed 상태의 엔티티이므로 dirty checking으로 자동 UPDATE:
     * → UPDATE products SET stock = (기존값 + quantity) WHERE id = ?
     *
     * 주의: 동시성 이슈
     * 여러 요청이 동시에 restock을 호출하면 "lost update" 문제가 발생할 수 있다.
     * 프로덕션에서는 @Lock(LockModeType.PESSIMISTIC_WRITE)이나
     * @Version(Optimistic Locking)을 사용해야 한다.
     * 이 예제에서는 단순화를 위해 다루지 않는다.
     *
     * @param id 재입고할 상품의 PK
     * @param quantity 보충 수량 (1 이상이어야 함)
     * @return ProductResponse DTO (재입고 후 상품 정보)
     * @throws BusinessException 수량이 0 이하일 때 (400)
     * @throws EntityNotFoundException 상품이 없을 때 (404)
     */
    @Transactional
    fun restockProduct(id: Int, quantity: Int): ProductResponse {
        // 수량 검증: 0 이하이면 비즈니스 규칙 위반
        if (quantity <= 0) throw BusinessException("보충 수량은 1 이상이어야 합니다")

        // 상품 조회 (없으면 404)
        val product = findProductOrThrow(id)

        // 재고 추가 — dirty checking으로 자동 UPDATE
        product.stock += quantity

        // DTO 변환 후 반환 (save() 불필요)
        return ProductResponse.from(product)
    }

    // ════════════════════════════════════════════════════════════════
    //  복합 조회 — ProductRepo + CategoryRepo 조합
    // ════════════════════════════════════════════════════════════════

    /**
     * 카테고리별 상품 목록 조회 (페이징)
     *
     * 2단계로 처리:
     * 1. findCategoryOrThrow(categoryId): 카테고리 존재 검증 (없으면 404)
     * 2. productRepo.findByCategoryId(): 해당 카테고리의 상품 목록 조회
     *
     * 왜 카테고리 존재 여부를 먼저 확인하나?
     * - 존재하지 않는 카테고리를 조회하면 빈 Page가 반환된다
     * - 하지만 빈 결과가 "카테고리가 없어서"인지 "상품이 없어서"인지 구분할 수 없다
     * - 카테고리가 없으면 404, 상품이 없으면 빈 Page를 반환하는 것이 정확한 API 설계
     *
     * ── productRepo.findByCategoryId(categoryId, pageable) ──
     *
     * ProductRepository의 파생 쿼리 메서드.
     * → SELECT * FROM products WHERE category_id = ? ORDER BY ... LIMIT ? OFFSET ?
     * → SELECT COUNT(*) FROM products WHERE category_id = ?
     *
     * @param categoryId 카테고리 PK
     * @param pageable 페이징 정보
     * @return Page<ProductResponse> — 해당 카테고리의 상품 목록
     * @throws EntityNotFoundException 카테고리가 없을 때 (404)
     */
    fun getProductsByCategory(categoryId: Int, pageable: Pageable): Page<ProductResponse> {
        findCategoryOrThrow(categoryId)    // 카테고리 존재 검증 (없으면 404)
        return productRepo.findByCategoryId(categoryId, pageable)  // 상품 목록 조회
            .map { ProductResponse.from(it) }                       // DTO 변환
    }

    /**
     * 카테고리명으로 상품 조회
     *
     * ── productRepo.findByCategoryName(name) ──
     *
     * ProductRepository에 정의된 @Query JPQL 메서드.
     * JPQL (Java Persistence Query Language)로 JOIN 쿼리를 작성했다:
     *   SELECT p FROM Product p WHERE p.category.name = :name
     *
     * 실제 생성되는 SQL:
     *   SELECT p.* FROM products p
     *   JOIN categories c ON p.category_id = c.id
     *   WHERE c.name = ?
     *
     * JPQL에서 p.category.name으로 작성하면
     * JPA가 자동으로 JOIN을 생성한다 (묵시적 조인).
     *
     * @param name 카테고리명
     * @return List<ProductResponse> — 해당 카테고리의 상품 목록
     */
    fun getProductsByCategoryName(name: String): List<ProductResponse> =
        productRepo.findByCategoryName(name)           // JPQL JOIN 쿼리
            .map { ProductResponse.from(it) }          // DTO 변환

    /**
     * 키워드 검색 (이름 + 설명)
     *
     * ── productRepo.searchByKeyword(keyword, pageable) ──
     *
     * ProductRepository에 정의된 @Query JPQL 메서드.
     * 이름과 설명 모두에서 키워드를 검색한다:
     *
     * JPQL:
     *   SELECT p FROM Product p
     *   WHERE LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
     *      OR LOWER(p.description) LIKE LOWER(CONCAT('%', :keyword, '%'))
     *
     * 실제 SQL:
     *   SELECT * FROM products
     *   WHERE LOWER(name) LIKE LOWER('%keyword%')
     *      OR LOWER(description) LIKE LOWER('%keyword%')
     *
     * LOWER(): 대소문자 구분 없는 검색을 위해 양쪽을 소문자로 변환
     * CONCAT('%', keyword, '%'): LIKE 패턴 생성 (앞뒤 와일드카드)
     *
     * @param keyword 검색할 키워드
     * @param pageable 페이징 정보
     * @return Page<ProductResponse> — 키워드와 일치하는 상품 목록
     */
    fun searchProducts(keyword: String, pageable: Pageable): Page<ProductResponse> =
        productRepo.searchByKeyword(keyword, pageable)     // JPQL 키워드 검색
            .map { ProductResponse.from(it) }              // DTO 변환

    /**
     * 가격 범위 조회
     *
     * ── productRepo.findByPriceBetween(min, max, pageable) ──
     *
     * Spring Data JPA의 파생 쿼리.
     * 메서드명의 "Between" 키워드를 파싱하여 SQL을 생성:
     * → SELECT * FROM products WHERE price BETWEEN ? AND ?
     *
     * Between은 양 끝 값을 포함한다 (min <= price <= max).
     *
     * @param min 최소 가격
     * @param max 최대 가격
     * @param pageable 페이징 정보
     * @return Page<ProductResponse> — 가격 범위에 해당하는 상품 목록
     */
    fun getProductsByPriceRange(min: Double, max: Double, pageable: Pageable): Page<ProductResponse> =
        productRepo.findByPriceBetween(min, max, pageable)   // BETWEEN 쿼리
            .map { ProductResponse.from(it) }                // DTO 변환

    /**
     * 재고 부족 상품 조회
     *
     * ── productRepo.findByStockLessThan(threshold, pageable) ──
     *
     * 파생 쿼리. "LessThan" 키워드:
     * → SELECT * FROM products WHERE stock < ?
     *
     * threshold: 재고 임계값 (기본값: 5)
     * 예: threshold=5 → 재고가 5개 미만인 상품을 조회
     *
     * 이 메서드는 재고 관리 대시보드에서 "곧 품절될 상품"을 파악하는 데 사용된다.
     *
     * @param threshold 재고 임계값 (이 값 미만인 상품 조회)
     * @param pageable 페이징 정보
     * @return Page<ProductResponse> — 재고 부족 상품 목록
     */
    fun getLowStockProducts(threshold: Int = 5, pageable: Pageable): Page<ProductResponse> =
        productRepo.findByStockLessThan(threshold, pageable)   // 재고 < threshold
            .map { ProductResponse.from(it) }                  // DTO 변환

    /**
     * 미판매 상품 조회 (한 번도 주문에 포함되지 않은 상품)
     *
     * ── productRepo.findUnsoldProducts(pageable) ──
     *
     * ProductRepository에 정의된 @Query JPQL 메서드.
     * NOT IN 서브쿼리로 미판매 상품을 찾는다:
     *
     * JPQL:
     *   SELECT p FROM Product p
     *   WHERE p.id NOT IN (SELECT DISTINCT oi.product.id FROM OrderItem oi)
     *
     * 실제 SQL:
     *   SELECT * FROM products
     *   WHERE id NOT IN (
     *     SELECT DISTINCT product_id FROM order_items
     *   )
     *
     * 서브쿼리: order_items에서 한 번이라도 주문된 상품 ID를 조회
     * NOT IN: 그 목록에 없는 상품 = 한 번도 주문되지 않은 상품
     *
     * @param pageable 페이징 정보
     * @return Page<ProductResponse> — 미판매 상품 목록
     */
    fun getUnsoldProducts(pageable: Pageable): Page<ProductResponse> =
        productRepo.findUnsoldProducts(pageable)              // NOT IN 서브쿼리
            .map { ProductResponse.from(it) }                 // DTO 변환

    // ════════════════════════════════════════════════════════════════
    //  카테고리별 통계 — 여러 Repository 조합
    // ════════════════════════════════════════════════════════════════

    /**
     * 카테고리별 통계 (카테고리별 상품 수, 평균 가격)
     *
     * 이 메서드는 Service 계층의 "복수 Repository 조합" 역할을 가장 잘 보여준다.
     * CategoryRepo와 ProductRepo의 결과를 결합하여 하나의 복합 응답을 만든다.
     *
     * ── 실행 흐름 ──
     *
     * 1. categoryRepo.findAll()                    → 모든 카테고리 조회
     * 2. 각 카테고리에 대해:
     *    a. productRepo.countByCategoryId(id)       → 상품 수 조회
     *    b. productRepo.avgPriceByCategoryId(id)    → 평균 가격 조회
     * 3. 결과를 CategorySummaryResponse DTO로 조합
     *
     * 실행되는 SQL:
     *   SELECT * FROM categories                                          (1번)
     *   SELECT COUNT(*) FROM products WHERE category_id = 1               (2a - 카테고리1)
     *   SELECT AVG(price) FROM products WHERE category_id = 1             (2b - 카테고리1)
     *   SELECT COUNT(*) FROM products WHERE category_id = 2               (2a - 카테고리2)
     *   SELECT AVG(price) FROM products WHERE category_id = 2             (2b - 카테고리2)
     *   ... (카테고리 수만큼 반복)
     *
     * ── N+1 쿼리 문제 ──
     *
     * 카테고리가 N개이면 SQL이 1 + 2*N개 실행된다.
     * 카테고리가 적으면(수십 개) 문제없지만,
     * 수천 개라면 성능 문제가 발생할 수 있다.
     * 해결 방법: JPQL에서 GROUP BY + 집계 함수로 한 번에 조회
     *
     * 이 예제에서는 학습 목적으로 간단한 패턴을 사용했다.
     *
     * ── .map { category -> ... } ──
     *
     * Kotlin의 map 함수: 각 요소를 변환하여 새 리스트를 만든다.
     * category: 현재 순회 중인 Category 엔티티 (it 대신 명시적 이름 사용)
     *
     * @return List<CategorySummaryResponse> — 각 카테고리의 통계 목록
     */
    fun getCategorySummary(): List<CategorySummaryResponse> =
        categoryRepo.findAll()                                  // 1. 모든 카테고리 조회
            .map { category ->                                  // 2. 각 카테고리마다
                CategorySummaryResponse(
                    category = CategoryResponse.from(category), // 카테고리 기본 정보
                    productCount = productRepo.countByCategoryId(category.id), // 상품 수
                    avgPrice = productRepo.avgPriceByCategoryId(category.id)   // 평균 가격
                )
            }

    // ════════════════════════════════════════════════════════════════
    //  내부 헬퍼 메서드
    // ════════════════════════════════════════════════════════════════

    /**
     * 상품 엔티티 조회 — 없으면 404 예외
     *
     * ── internal 접근 제한자 ──
     *
     * 이 메서드는 같은 모듈 내에서만 접근 가능하다.
     *
     * 사용 위치:
     * 1. CatalogService 내부 — getProduct(), updateProduct(), deleteProduct(), restockProduct()
     * 2. OrderService — createOrder()에서 상품 검증 + 재고 차감 시 상품 엔티티 필요
     *
     * private이 아닌 internal인 이유:
     * OrderService에서 "상품이 존재하는지 확인 + 상품 엔티티를 가져오기"를
     * 이 메서드 하나로 처리할 수 있다. DRY(Don't Repeat Yourself) 원칙.
     *
     * ── productRepo.findById(id) ──
     *
     * JpaRepository의 기본 메서드.
     * → SELECT * FROM products WHERE id = ?
     * 반환: Optional<Product>
     *
     * ── .orElseThrow { EntityNotFoundException("상품", id) } ──
     *
     * Optional이 비어있으면 (상품이 없으면) 예외를 던진다.
     * EntityNotFoundException은 GlobalExceptionHandler에서 404로 변환.
     *
     * @param id 조회할 상품의 PK
     * @return Product 엔티티 (JPA managed 상태)
     * @throws EntityNotFoundException 해당 상품이 없을 때 (404)
     */
    internal fun findProductOrThrow(id: Int): Product =
        productRepo.findById(id)                                   // Optional<Product>
            .orElseThrow { EntityNotFoundException("상품", id) }    // 없으면 404

    /**
     * 카테고리 엔티티 조회 — 없으면 404 예외
     *
     * private: CatalogService 내부에서만 사용.
     * 다른 서비스에서 카테고리를 조회할 일이 없으므로 private으로 충분하다.
     *
     * @param id 조회할 카테고리의 PK
     * @return Category 엔티티 (JPA managed 상태)
     * @throws EntityNotFoundException 해당 카테고리가 없을 때 (404)
     */
    private fun findCategoryOrThrow(id: Int): Category =
        categoryRepo.findById(id)                                    // Optional<Category>
            .orElseThrow { EntityNotFoundException("카테고리", id) }  // 없으면 404
}
