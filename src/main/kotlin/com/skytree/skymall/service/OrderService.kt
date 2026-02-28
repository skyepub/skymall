package com.skytree.skymall.service

// ── import 설명 ──
// dto 패키지의 모든 DTO 클래스를 가져온다.
// 이 서비스에서 사용하는 DTO:
//   CreateOrderRequest, OrderItemRequest, OrderResponse, OrderSummaryResponse, SalesReportResponse
import com.skytree.skymall.dto.*
// JPA 엔티티: 데이터베이스 테이블과 1:1 매핑
import com.skytree.skymall.entity.OrderItem    // order_items 테이블
import com.skytree.skymall.entity.SalesOrder   // sales_orders 테이블
// 커스텀 예외 클래스
import com.skytree.skymall.exception.BusinessException          // -> 400 Bad Request
import com.skytree.skymall.exception.EntityNotFoundException    // -> 404 Not Found
import com.skytree.skymall.exception.InsufficientStockException // -> 422 Unprocessable Entity
// Repository: DB 접근 계층
import com.skytree.skymall.repository.OrderItemRepository
import com.skytree.skymall.repository.SalesOrderRepository
// Spring Data 페이징
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
// Spring 어노테이션
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
// Java 날짜/시간 API
import java.time.LocalDateTime

/**
 * ================================================================
 * 주문 서비스 (OrderService) — 가장 복잡한 서비스
 * ================================================================
 *
 * 주문(SalesOrder)과 주문항목(OrderItem)의 비즈니스 로직을 담당한다.
 * 이 프로젝트에서 가장 많은 의존성을 가지며, 가장 복잡한 트랜잭션을 처리한다.
 *
 * ── 의존성 구조 (4개) ──
 *
 *   OrderService
 *     +-- SalesOrderRepository  : 주문 CRUD + 집계 쿼리 (sales_orders 테이블)
 *     +-- OrderItemRepository   : 주문항목 (주로 cascade로 자동 처리)
 *     +-- UserService           : 사용자 검증 (Service -> Service 위임)
 *     +-- CatalogService        : 상품 검증 + 재고 차감 (Service -> Service 위임)
 *
 * ── Service -> Service 위임 패턴 ──
 *
 * OrderService가 UserRepository와 ProductRepository를 직접 사용하지 않고,
 * UserService와 CatalogService를 통해 간접적으로 접근한다.
 *
 * 이 패턴의 장점:
 * 1. DRY (Don't Repeat Yourself):
 *    "사용자 존재 검증" 로직이 UserService.findUserOrThrow()에 한 곳만 있다.
 *    OrderService, AuthService 등 여러 곳에서 같은 검증을 재사용한다.
 *
 * 2. Single Responsibility (단일 책임):
 *    OrderService는 "주문" 관련 로직만 담당한다.
 *    "사용자" 관련 검증은 UserService에, "상품" 관련 검증은 CatalogService에 위임한다.
 *
 * 3. 일관성:
 *    사용자 검증 로직이 변경되면 (예: 탈퇴 상태 체크 추가)
 *    UserService.findUserOrThrow()만 수정하면 모든 곳에 반영된다.
 *
 * ── @Transactional 전파 (Propagation) — 매우 중요! ──
 *
 * Spring의 @Transactional은 기본 전파(Propagation) 모드가 REQUIRED이다.
 * REQUIRED: "이미 트랜잭션이 있으면 참여하고, 없으면 새로 시작한다"
 *
 * 이것이 의미하는 바:
 *
 *   @Transactional              ← OrderService.createOrder() - 트랜잭션 시작
 *   fun createOrder() {
 *     userService.findUserOrThrow()      ← 같은 트랜잭션에 참여 (새 트랜잭션 X)
 *     catalogService.findProductOrThrow() ← 같은 트랜잭션에 참여
 *     product.stock -= quantity           ← 같은 트랜잭션 내 변경
 *     orderRepo.save(order)               ← 같은 트랜잭션 내 저장
 *   }                            ← 트랜잭션 커밋 시점
 *
 * 만약 어디서든 RuntimeException이 발생하면:
 * → 전체 트랜잭션이 롤백된다 (재고 차감도 원복!)
 * → 예: 재고 차감 후 orderRepo.save()에서 에러 → 재고 차감도 롤백
 *
 * 이것이 트랜잭션의 "원자성(Atomicity)"이다:
 * "모두 성공하거나, 모두 실패하거나" — 중간 상태는 없다.
 *
 * ── @Service + @Transactional(readOnly = true) ──
 *
 * 클래스 레벨 설정: 모든 메서드의 기본값은 읽기 전용 트랜잭션.
 * 쓰기가 필요한 메서드에만 @Transactional을 개별 선언하여 오버라이드.
 */
@Service
@Transactional(readOnly = true)
class OrderService(
    private val orderRepo: SalesOrderRepository,  // 주문 테이블 접근
    private val orderItemRepo: OrderItemRepository, // 주문항목 테이블 접근 (주로 cascade 사용)
    private val userService: UserService,          // 사용자 검증 위임
    private val catalogService: CatalogService     // 상품 검증 + 재고 관리 위임
) {

    // ════════════════════════════════════════════════════════════════
    //  주문 생성 — 이 프로젝트에서 가장 복잡한 비즈니스 로직
    // ════════════════════════════════════════════════════════════════

    /**
     * 주문 생성 — 하나의 트랜잭션에서 원자적으로 처리
     *
     * 이 메서드는 여러 테이블에 걸친 복잡한 작업을 하나의 트랜잭션으로 묶는다.
     * Service 계층이 존재하는 가장 핵심적인 이유를 보여주는 메서드이다.
     *
     * ── 실행 흐름 (하나의 트랜잭션 안에서) ──
     *
     * ┌──────────────────────────────────────────────────────┐
     * │  @Transactional (하나의 데이터베이스 트랜잭션)          │
     * │                                                      │
     * │  1. 빈 주문 검증 — 주문 항목이 0개면 거부              │
     * │  2. 사용자 검증 — UserService에 위임                   │
     * │     a. 사용자 존재 확인 (없으면 404)                   │
     * │     b. 활성화 상태 확인 (비활성이면 400)               │
     * │  3. 상품별 처리 (반복)                                │
     * │     a. 상품 검증 — CatalogService에 위임 (없으면 404)  │
     * │     b. 재고 확인 (부족하면 422)                        │
     * │     c. 재고 차감 (product.stock -= quantity)           │
     * │     d. 소계 계산 + OrderItem 엔티티 생성               │
     * │  4. SalesOrder 엔티티 생성 + 양방향 관계 설정           │
     * │  5. DB 저장 (cascade로 OrderItem도 함께)               │
     * │                                                      │
     * │  -> 성공: 전체 커밋 (주문 + 항목 + 재고 변경)          │
     * │  -> 실패: 전체 롤백 (재고 차감도 원복!)                │
     * └──────────────────────────────────────────────────────┘
     *
     * ── 재고 차감과 트랜잭션 롤백 ──
     *
     * 재고 차감(product.stock -= quantity)은 dirty checking으로 처리된다.
     * 트랜잭션이 롤백되면 dirty checking도 무효화되므로,
     * 재고 차감이 DB에 반영되지 않는다 (원복).
     *
     * 예시 시나리오:
     * 1. 상품A 재고 10개 → 5개 차감 → 메모리상 5개
     * 2. 상품B 재고 부족 → InsufficientStockException 발생
     * 3. 트랜잭션 롤백 → 상품A의 재고 차감도 취소 → DB에는 여전히 10개
     *
     * @param req CreateOrderRequest (userId, items: List<OrderItemRequest>)
     * @return OrderResponse DTO (생성된 주문 정보)
     * @throws BusinessException 빈 주문, 비활성 사용자 (400)
     * @throws EntityNotFoundException 사용자 또는 상품이 없을 때 (404)
     * @throws InsufficientStockException 재고 부족 (422)
     */
    @Transactional
    fun createOrder(req: CreateOrderRequest): OrderResponse {

        // ── 1단계: 빈 주문 검증 ──
        //
        // req.items: List<OrderItemRequest>
        //   클라이언트가 보낸 주문 항목 리스트.
        //   각 항목에는 productId(상품 ID)와 quantity(수량)가 포함되어 있다.
        //
        // isEmpty(): 리스트가 비어있으면 true
        //
        // 주문 항목이 없는 주문은 비즈니스적으로 무의미하므로 거부한다.
        if (req.items.isEmpty()) {
            throw BusinessException("주문 항목이 비어있습니다")
        }

        // ── 2단계: 사용자 검증 — UserService에 위임 ──
        //
        // userService.findUserOrThrow(req.userId):
        //   UserService의 internal 메서드를 호출한다.
        //   → SELECT * FROM users WHERE id = ?
        //   없으면 EntityNotFoundException (404)
        //
        //   이 호출은 OrderService의 @Transactional 안에서 발생하므로,
        //   UserService의 메서드도 같은 트랜잭션에 참여한다 (전파: REQUIRED).
        val user = userService.findUserOrThrow(req.userId)

        // 계정 활성화 상태 확인
        // User.isEnabled = false이면 주문을 생성할 수 없다.
        // 관리자가 계정을 비활성화한 경우 (부정 사용 등)
        if (!user.isEnabled) {
            throw BusinessException("비활성화된 사용자는 주문할 수 없습니다")
        }

        // ── 3단계: 상품별 처리 (재고 확인 + 차감 + 금액 계산) ──

        // totalAmount: 전체 주문 금액을 누적할 변수
        // var: 값이 변경될 수 있는 변수 (val은 불변)
        var totalAmount = 0.0

        // orderItems: 생성할 OrderItem 엔티티를 담을 가변 리스트
        // mutableListOf<OrderItem>(): 빈 MutableList 생성
        // MutableList vs List:
        //   List: 읽기 전용 (요소 추가/삭제 불가)
        //   MutableList: 읽기+쓰기 (요소 추가/삭제 가능)
        val orderItems = mutableListOf<OrderItem>()

        // ── 각 주문 항목에 대해 반복 ──
        //
        // for (itemReq in req.items):
        //   Kotlin의 for-in 루프. Java의 for-each와 동일.
        //   itemReq: OrderItemRequest (productId: Int, quantity: Int)
        for (itemReq in req.items) {

            // ── 3a: 상품 검증 — CatalogService에 위임 ──
            //
            // catalogService.findProductOrThrow(itemReq.productId):
            //   CatalogService의 internal 메서드를 호출한다.
            //   → SELECT * FROM products WHERE id = ?
            //   없으면 EntityNotFoundException (404)
            //
            //   반환된 Product 엔티티는 JPA의 managed 상태이다.
            //   이 엔티티의 필드를 변경하면 dirty checking으로 자동 UPDATE된다.
            val product = catalogService.findProductOrThrow(itemReq.productId)

            // ── 3b: 재고 확인 ──
            //
            // product.stock: 현재 DB에 저장된 재고 수량
            // itemReq.quantity: 고객이 주문한 수량
            //
            // 재고가 부족하면 InsufficientStockException (422 Unprocessable Entity)
            //
            // InsufficientStockException은 커스텀 예외로,
            // 생성자에 상품명, 요청 수량, 현재 재고를 전달하여
            // 구체적인 에러 메시지를 생성한다.
            // 예: "상품 '맥북 프로'의 재고가 부족합니다. 요청: 5, 현재 재고: 3"
            if (product.stock < itemReq.quantity) {
                throw InsufficientStockException(
                    product.name,        // 상품명 (에러 메시지용)
                    itemReq.quantity,    // 요청 수량
                    product.stock        // 현재 재고
                )
            }

            // ── 3c: 재고 차감 ──
            //
            // product.stock -= itemReq.quantity:
            //   Kotlin의 복합 대입 연산자.
            //   product.stock = product.stock - itemReq.quantity 와 동일.
            //
            //   예: 재고 10개 - 주문 3개 = 7개
            //
            //   이 변경은 아직 DB에 반영되지 않는다!
            //   트랜잭션 커밋 시 dirty checking으로 자동 UPDATE:
            //   → UPDATE products SET stock = 7 WHERE id = ?
            //
            //   만약 이후에 예외가 발생하면 트랜잭션 전체가 롤백되므로,
            //   이 재고 차감도 DB에 반영되지 않는다 (안전!).
            product.stock -= itemReq.quantity

            // ── 3d: 소계(subtotal) 계산 ──
            //
            // subtotal = 단가 x 수량
            // 예: 10000원 x 3개 = 30000원
            val subtotal = product.price * itemReq.quantity

            // 전체 주문 금액에 소계를 누적
            // += 연산자: totalAmount = totalAmount + subtotal
            totalAmount += subtotal

            // ── 3e: OrderItem 엔티티 생성 ──
            //
            // OrderItem: 주문 항목 (어떤 상품을 몇 개, 얼마에 주문했는지)
            //
            // pricePerItem = product.price:
            //   주문 시점의 가격을 "스냅샷"으로 저장한다!
            //   이것이 매우 중요한 비즈니스 패턴이다.
            //
            //   왜 상품의 현재 가격을 저장하나?
            //   - 나중에 상품 가격이 변해도, 이 주문의 결제 금액은 변하지 않아야 한다
            //   - 예: 10000원에 구매 → 나중에 상품이 15000원으로 인상
            //         → 주문 이력에는 10000원으로 남아야 한다
            //   - 이 패턴이 없으면 과거 주문의 금액이 현재 상품 가격에 따라 변동된다
            //
            // order 필드는 아직 설정하지 않는다:
            //   SalesOrder가 아직 생성되지 않았기 때문이다.
            //   아래 4단계에서 양방향 관계를 설정할 때 order를 지정한다.
            orderItems.add(
                OrderItem(
                    product = product,                // @ManyToOne 관계: 주문한 상품
                    quantity = itemReq.quantity,       // 주문 수량
                    pricePerItem = product.price       // 주문 시점 단가 (가격 스냅샷)
                )
            )
        }

        // ── 4단계: SalesOrder 엔티티 생성 + 양방향 관계 설정 ──
        //
        // SalesOrder: 주문 (누가, 언제, 총 얼마의 주문을 했는지)
        //
        // user = user: @ManyToOne 관계 — 이 주문을 한 사용자
        //   → sales_orders 테이블의 user_id 컬럼에 user.id가 저장된다
        //
        // totalAmount = totalAmount: 위에서 계산한 전체 주문 금액
        val order = SalesOrder(
            user = user,                  // 주문자
            totalAmount = totalAmount     // 전체 금액
        )

        // ── 양방향 관계 설정 ──
        //
        // JPA의 양방향 관계에서는 "관계의 주인(owning side)"과
        // "역방향(inverse side)" 양쪽 모두 설정해야 한다.
        //
        // 관계의 주인: OrderItem.order (외래 키를 가진 쪽)
        //   → order_items 테이블의 order_id 컬럼
        //
        // 역방향: SalesOrder.items (mappedBy = "order")
        //   → DB 컬럼 없음, 순수 객체 참조
        //
        // 왜 양쪽 다 설정해야 하나?
        // - item.order = order: JPA가 외래 키(order_id)를 설정하려면 필수
        //   (이 쪽만 설정해도 DB에는 정상 저장된다)
        // - order.items.add(item): 메모리 상의 일관성을 위해 필요
        //   (설정하지 않으면 order.items가 빈 리스트로 남아
        //    이후 코드에서 주문 항목을 조회할 수 없다)
        //
        // forEach: Kotlin의 반복 함수. for-in 루프와 동일.
        orderItems.forEach { item ->
            item.order = order           // 자식 -> 부모: 외래 키 설정 (관계의 주인)
            order.items.add(item)        // 부모 -> 자식: 메모리 일관성 (역방향)
        }

        // ── 5단계: DB 저장 ──
        //
        // orderRepo.save(order):
        //   EntityManager.persist(order) 호출.
        //   → INSERT INTO sales_orders (user_id, total_amount, order_date) VALUES (?, ?, ?)
        //
        //   cascade = CascadeType.ALL 설정 때문에:
        //   order를 persist하면, order.items에 포함된 모든 OrderItem도 자동으로 persist된다.
        //   → INSERT INTO order_items (order_id, product_id, quantity, price_per_item) VALUES (?, ?, ?, ?)
        //   → INSERT INTO order_items (order_id, product_id, quantity, price_per_item) VALUES (?, ?, ?, ?)
        //   → ... (항목 수만큼 반복)
        //
        //   즉, orderRepo.save() 한 번 호출로 주문 + 모든 주문 항목이 함께 저장된다!
        //   이것이 cascade의 힘이다.
        //
        // saved: DB가 생성한 ID가 설정된 엔티티가 반환된다.
        val saved = orderRepo.save(order)

        // Entity -> DTO 변환 후 반환
        // OrderResponse.from(saved): SalesOrder + OrderItems를 JSON 응답 형태로 변환
        return OrderResponse.from(saved)
    }

    // ════════════════════════════════════════════════════════════════
    //  주문 조회 (Read)
    // ════════════════════════════════════════════════════════════════

    /**
     * 주문 단건 조회
     *
     * findOrderOrThrow(id):
     *   이 클래스의 private 헬퍼 메서드.
     *   → SELECT * FROM sales_orders WHERE id = ?
     *   없으면 EntityNotFoundException (404)
     *
     * OrderResponse.from(order):
     *   SalesOrder 엔티티를 OrderResponse DTO로 변환한다.
     *   주문 항목(OrderItems)도 함께 변환된다.
     *
     * @param id 조회할 주문의 PK
     * @return OrderResponse DTO (주문 정보 + 주문 항목 리스트)
     * @throws EntityNotFoundException 해당 주문이 없을 때 (404)
     */
    fun getOrder(id: Int): OrderResponse {
        val order = findOrderOrThrow(id)   // 주문 조회 (없으면 404)
        return OrderResponse.from(order)   // DTO 변환
    }

    /**
     * 전체 주문 목록 조회 (관리자용, 페이징)
     *
     * orderRepo.findAll(pageable):
     *   JpaRepository의 기본 페이징 메서드.
     *   → SELECT * FROM sales_orders ORDER BY ... LIMIT ? OFFSET ?
     *   → SELECT COUNT(*) FROM sales_orders
     *
     * @param pageable 페이징 정보 (page, size, sort)
     * @return Page<OrderResponse> — 페이징된 주문 목록
     */
    fun getAllOrders(pageable: Pageable): Page<OrderResponse> =
        orderRepo.findAll(pageable)                    // Page<SalesOrder>
            .map { OrderResponse.from(it) }            // Page<OrderResponse>

    /**
     * 사용자별 주문 조회 (최신순, 페이징)
     *
     * ── userService.findUserOrThrow(userId) ──
     *
     * 먼저 사용자가 존재하는지 확인한다.
     * 존재하지 않는 사용자의 주문을 조회하면 빈 결과가 반환되는데,
     * "사용자가 없어서 빈 결과"인지 "주문이 없어서 빈 결과"인지
     * 클라이언트가 구분할 수 없으므로, 사용자가 없으면 404를 반환한다.
     *
     * ── orderRepo.findByUserIdOrderByOrderDateDesc(userId, pageable) ──
     *
     * Spring Data JPA의 파생 쿼리.
     * 메서드명을 파싱하여 생성되는 SQL:
     *   SELECT * FROM sales_orders
     *   WHERE user_id = ?
     *   ORDER BY order_date DESC  (최신순)
     *   LIMIT ? OFFSET ?
     *
     * 메서드명 분석:
     * - findBy: SELECT 쿼리
     * - UserId: WHERE user_id = ?
     * - OrderBy: ORDER BY
     * - OrderDate: order_date 컬럼
     * - Desc: 내림차순 (최신 -> 과거)
     *
     * @param userId 조회할 사용자의 PK
     * @param pageable 페이징 정보
     * @return Page<OrderResponse> — 해당 사용자의 주문 목록 (최신순)
     * @throws EntityNotFoundException 해당 사용자가 없을 때 (404)
     */
    fun getOrdersByUser(userId: Int, pageable: Pageable): Page<OrderResponse> {
        userService.findUserOrThrow(userId)            // 사용자 존재 확인 (없으면 404)
        return orderRepo.findByUserIdOrderByOrderDateDesc(userId, pageable)
            .map { OrderResponse.from(it) }            // DTO 변환
    }

    /**
     * 기간별 주문 조회 (페이징)
     *
     * ── orderRepo.findByOrderDateBetween(from, to, pageable) ──
     *
     * 파생 쿼리. "Between" 키워드:
     * → SELECT * FROM sales_orders WHERE order_date BETWEEN ? AND ?
     *
     * BETWEEN은 양 끝 값을 포함한다.
     * 예: from=2026-01-01, to=2026-12-31
     *   → 2026년 전체 주문을 조회
     *
     * @param from 시작 일시 (포함)
     * @param to 종료 일시 (포함)
     * @param pageable 페이징 정보
     * @return Page<OrderResponse> — 기간 내 주문 목록
     */
    fun getOrdersByDateRange(
        from: LocalDateTime,
        to: LocalDateTime,
        pageable: Pageable
    ): Page<OrderResponse> =
        orderRepo.findByOrderDateBetween(from, to, pageable)  // BETWEEN 쿼리
            .map { OrderResponse.from(it) }                    // DTO 변환

    /**
     * 특정 상품을 포함한 주문 조회 (페이징)
     *
     * ── catalogService.findProductOrThrow(productId) ──
     *
     * 상품 존재 확인. 같은 이유로 사용자 조회와 동일:
     * 상품이 없는데 빈 결과를 반환하면 클라이언트가 혼란스러워할 수 있다.
     *
     * ── orderRepo.findByProductId(productId, pageable) ──
     *
     * SalesOrderRepository에 정의된 @Query JPQL 메서드.
     * OrderItem을 통해 특정 상품을 포함한 주문을 찾는 JOIN 쿼리:
     *
     * JPQL:
     *   SELECT DISTINCT o FROM SalesOrder o
     *   JOIN o.items oi
     *   WHERE oi.product.id = :productId
     *
     * 실제 SQL:
     *   SELECT DISTINCT so.* FROM sales_orders so
     *   JOIN order_items oi ON so.id = oi.order_id
     *   WHERE oi.product_id = ?
     *
     * DISTINCT: 같은 상품이 여러 항목으로 있어도 주문은 한 번만 반환
     *
     * @param productId 조회할 상품의 PK
     * @param pageable 페이징 정보
     * @return Page<OrderResponse> — 해당 상품을 포함한 주문 목록
     * @throws EntityNotFoundException 상품이 없을 때 (404)
     */
    fun getOrdersByProduct(productId: Int, pageable: Pageable): Page<OrderResponse> {
        catalogService.findProductOrThrow(productId)    // 상품 존재 확인 (없으면 404)
        return orderRepo.findByProductId(productId, pageable) // JPQL JOIN 쿼리
            .map { OrderResponse.from(it) }              // DTO 변환
    }

    /**
     * 고액 주문 조회 (최소 금액 이상, 페이징)
     *
     * ── orderRepo.findByTotalAmountGreaterThanEqual(minAmount, pageable) ──
     *
     * 파생 쿼리. "GreaterThanEqual" 키워드:
     * → SELECT * FROM sales_orders WHERE total_amount >= ?
     *
     * 사용 예:
     * GET /api/orders/high-value?minAmount=100000
     * → 총액 10만원 이상인 주문 조회
     *
     * @param minAmount 최소 주문 금액 (이 값 이상)
     * @param pageable 페이징 정보
     * @return Page<OrderResponse> — 고액 주문 목록
     */
    fun getHighValueOrders(minAmount: Double, pageable: Pageable): Page<OrderResponse> =
        orderRepo.findByTotalAmountGreaterThanEqual(minAmount, pageable) // >= minAmount
            .map { OrderResponse.from(it) }

    /**
     * 대량 주문 조회 (주문 항목이 N개 이상, 페이징)
     *
     * ── orderRepo.findByMinItemCount(minItems, pageable) ──
     *
     * SalesOrderRepository에 정의된 @Query JPQL 메서드.
     * HAVING 절로 그룹별 조건을 적용한다:
     *
     * JPQL:
     *   SELECT o FROM SalesOrder o
     *   WHERE SIZE(o.items) >= :minItems
     *
     * SIZE(): JPA 함수 — 컬렉션의 크기를 반환.
     * o.items: SalesOrder의 @OneToMany 관계 (주문 항목 리스트)
     *
     * 실제 SQL은 서브쿼리로 변환될 수 있다:
     *   SELECT * FROM sales_orders
     *   WHERE (SELECT COUNT(*) FROM order_items WHERE order_id = sales_orders.id) >= ?
     *
     * @param minItems 최소 주문 항목 수
     * @param pageable 페이징 정보
     * @return Page<OrderResponse> — 대량 주문 목록
     */
    fun getLargeOrders(minItems: Int, pageable: Pageable): Page<OrderResponse> =
        orderRepo.findByMinItemCount(minItems, pageable)    // SIZE() >= minItems
            .map { OrderResponse.from(it) }

    // ════════════════════════════════════════════════════════════════
    //  주문 취소 — 재고 복원 포함
    // ════════════════════════════════════════════════════════════════

    /**
     * 주문 취소 — 재고 복원 + 주문 삭제
     *
     * 주문 생성의 역순으로 처리한다:
     *   생성: 재고 차감 -> 주문 저장
     *   취소: 재고 복원 -> 주문 삭제
     *
     * ── 실행 흐름 ──
     *
     * ┌──────────────────────────────────────────────────────┐
     * │  @Transactional (하나의 트랜잭션)                      │
     * │                                                      │
     * │  1. 주문 조회 (없으면 404)                             │
     * │  2. 각 주문 항목의 상품 재고 복원                       │
     * │     → product.stock += item.quantity                 │
     * │     → dirty checking으로 자동 UPDATE                  │
     * │  3. 주문 삭제 (cascade ALL로 OrderItem도 함께 삭제)     │
     * │     → DELETE FROM order_items WHERE order_id = ?      │
     * │     → DELETE FROM sales_orders WHERE id = ?           │
     * │                                                      │
     * │  -> 성공: 전체 커밋 (재고 복원 + 주문/항목 삭제)         │
     * │  -> 실패: 전체 롤백                                    │
     * └──────────────────────────────────────────────────────┘
     *
     * ── cascade와 orphanRemoval ──
     *
     * SalesOrder의 @OneToMany 설정:
     *   cascade = [CascadeType.ALL], orphanRemoval = true
     *
     * cascade.ALL: 부모(SalesOrder)에 대한 모든 작업이 자식(OrderItem)에 전파
     * - persist -> persist: 주문 저장 시 항목도 저장
     * - remove -> remove: 주문 삭제 시 항목도 삭제
     *
     * orphanRemoval = true:
     * - 부모와의 관계가 끊어진 자식은 자동 삭제
     * - 예: order.items.remove(item) -> item이 DB에서도 삭제됨
     *
     * @param id 취소할 주문의 PK
     * @throws EntityNotFoundException 주문이 없을 때 (404)
     * @throws BusinessException 주문 항목에 상품 정보가 없을 때 (400)
     */
    @Transactional
    fun cancelOrder(id: Int) {

        // 1. 주문 조회 (없으면 404)
        val order = findOrderOrThrow(id)

        // 2. 재고 복원 — 각 주문 항목의 상품 재고를 되돌린다
        //
        // order.items: List<OrderItem> — 이 주문에 포함된 모든 항목
        //
        // @OneToMany(fetch = LAZY)이지만, 같은 트랜잭션 내에서 접근하므로
        // JPA가 자동으로 추가 SELECT를 실행하여 로드한다 (Lazy Loading).
        // → SELECT * FROM order_items WHERE order_id = ?
        for (item in order.items) {

            // item.product: OrderItem의 @ManyToOne 관계로 연결된 Product 엔티티
            // FetchType.LAZY이므로 접근 시 추가 SELECT 실행
            //
            // ?: throw BusinessException(...):
            //   product가 null인 경우 (데이터 무결성 문제)에 대한 방어 코드.
            //   정상적인 상황에서는 null이 되지 않지만, 안전을 위해 체크한다.
            val product = item.product
                ?: throw BusinessException("주문 아이템에 상품 정보가 없습니다")

            // product.stock += item.quantity:
            //   재고를 주문했던 수량만큼 되돌린다.
            //   예: 원래 재고 10개, 주문으로 3개 차감 → 현재 7개 + 3개 = 10개 복원
            //
            //   dirty checking으로 트랜잭션 커밋 시 자동 UPDATE:
            //   → UPDATE products SET stock = 10 WHERE id = ?
            product.stock += item.quantity
        }

        // 3. 주문 삭제
        //
        // orderRepo.delete(order):
        //   EntityManager.remove(order) 호출.
        //
        //   cascade = CascadeType.ALL 때문에 순서대로 실행:
        //   ① DELETE FROM order_items WHERE order_id = ?  (자식 먼저 삭제)
        //   ② DELETE FROM sales_orders WHERE id = ?        (부모 삭제)
        //
        //   만약 cascade 설정이 없다면?
        //   외래 키 제약(FK constraint)으로 인해 부모를 먼저 삭제할 수 없다.
        //   자식(order_items)을 먼저 수동으로 삭제한 후 부모를 삭제해야 한다.
        //   cascade가 이 작업을 자동화해준다.
        orderRepo.delete(order)
    }

    // ════════════════════════════════════════════════════════════════
    //  매출 통계 — 집계 쿼리 조합
    // ════════════════════════════════════════════════════════════════

    /**
     * 사용자별 주문 요약 (주문 건수, 총액, 평균)
     *
     * 3개의 집계 쿼리를 조합하여 하나의 요약 응답을 만든다.
     * 이것이 Service 계층의 "복수 쿼리 조합" 역할이다.
     *
     * ── 실행되는 SQL (총 4개) ──
     *
     * 1. SELECT * FROM users WHERE id = ?                              (사용자 존재 확인)
     * 2. SELECT COUNT(*) FROM sales_orders WHERE user_id = ?           (주문 건수)
     * 3. SELECT SUM(total_amount) FROM sales_orders WHERE user_id = ?  (총 주문액)
     * 4. SELECT AVG(total_amount) FROM sales_orders WHERE user_id = ?  (평균 주문액)
     *
     * ── ?: 0.0 (Elvis 연산자) ──
     *
     * SUM()과 AVG() SQL 함수는 대상 행이 없으면(주문이 0건이면) NULL을 반환한다.
     * Spring Data JPA는 이를 Kotlin의 null로 변환한다.
     * ?: 0.0으로 null을 0.0으로 대체한다.
     *
     * 예:
     * - 주문 5건, 총액 500000 → { totalOrders: 5, totalRevenue: 500000.0, avgOrderAmount: 100000.0 }
     * - 주문 0건           → { totalOrders: 0, totalRevenue: 0.0, avgOrderAmount: 0.0 }
     *
     * @param userId 요약을 조회할 사용자의 PK
     * @return OrderSummaryResponse (totalOrders, totalRevenue, avgOrderAmount)
     * @throws EntityNotFoundException 사용자가 없을 때 (404)
     */
    fun getUserOrderSummary(userId: Int): OrderSummaryResponse {

        // 사용자 존재 확인 (없으면 404)
        userService.findUserOrThrow(userId)

        // 3개의 집계 쿼리 결과를 하나의 DTO로 조합
        return OrderSummaryResponse(
            totalOrders = orderRepo.countByUserId(userId),                     // COUNT(*)
            totalRevenue = orderRepo.sumTotalAmountByUserId(userId) ?: 0.0,    // SUM() or 0.0
            avgOrderAmount = orderRepo.avgTotalAmountByUserId(userId) ?: 0.0   // AVG() or 0.0
        )
    }

    /**
     * 기간별 매출 리포트 (관리자용)
     *
     * 여러 Repository 메서드를 조합하여 종합 리포트를 생성한다.
     *
     * ── 실행되는 SQL ──
     *
     * 1. SELECT COUNT(*) FROM sales_orders WHERE order_date BETWEEN ? AND ?
     *    → 기간 내 주문 건수
     *
     * 2. SELECT SUM(total_amount) FROM sales_orders WHERE order_date BETWEEN ? AND ?
     *    → 기간 내 총 매출
     *
     * 3. SELECT * FROM sales_orders ORDER BY total_amount DESC
     *    → 금액 상위 주문 (전체 조회 후 take(5)로 제한)
     *
     * ── .take(5) ──
     *
     * Kotlin의 Collection 확장 함수: 리스트의 첫 5개 요소만 반환한다.
     * DB에서 전체를 가져온 후 메모리에서 잘라내는 방식이다.
     *
     * 성능 개선 팁:
     * 실제 프로덕션에서는 SQL에 LIMIT 5를 추가하여 DB에서 5개만 가져오는 것이 좋다.
     * 예: @Query("SELECT o FROM SalesOrder o ORDER BY o.totalAmount DESC LIMIT 5")
     * 또는 Pageable(0, 5)을 사용.
     *
     * @param from 시작 일시 (포함)
     * @param to 종료 일시 (포함)
     * @return SalesReportResponse (기간, 주문건수, 총매출, 상위5건)
     */
    fun getSalesReport(from: LocalDateTime, to: LocalDateTime): SalesReportResponse {

        // 기간 내 주문 건수
        // → SELECT COUNT(*) FROM sales_orders WHERE order_date BETWEEN ? AND ?
        val orderCount = orderRepo.countByOrderDateBetween(from, to)

        // 기간 내 총 매출 (null이면 0.0)
        // → SELECT SUM(total_amount) FROM sales_orders WHERE order_date BETWEEN ? AND ?
        val totalRevenue = orderRepo.sumTotalAmountBetween(from, to) ?: 0.0

        // 금액 상위 주문 5건
        // → SELECT * FROM sales_orders ORDER BY total_amount DESC
        //
        // .take(5): 상위 5건만 추출
        // .map { OrderResponse.from(it) }: Entity -> DTO 변환
        val topOrders = orderRepo.findTopByTotalAmount()
            .take(5)                                          // 상위 5건만
            .map { OrderResponse.from(it) }                   // DTO 변환

        // 모든 데이터를 SalesReportResponse DTO로 조합하여 반환
        return SalesReportResponse(
            from = from,                     // 리포트 시작 일시
            to = to,                         // 리포트 종료 일시
            orderCount = orderCount,         // 기간 내 주문 건수
            totalRevenue = totalRevenue,     // 기간 내 총 매출
            topOrders = topOrders            // 금액 상위 5건
        )
    }

    // ════════════════════════════════════════════════════════════════
    //  내부 헬퍼 메서드
    // ════════════════════════════════════════════════════════════════

    /**
     * 주문 엔티티 조회 — 없으면 404 예외
     *
     * private: OrderService 내부에서만 사용.
     * 다른 서비스에서 주문을 직접 조회할 필요가 없으므로 private으로 충분하다.
     *
     * ── orderRepo.findById(id) ──
     *
     * JpaRepository의 기본 메서드.
     * → SELECT * FROM sales_orders WHERE id = ?
     * 반환: Optional<SalesOrder>
     *
     * ── .orElseThrow { EntityNotFoundException("주문", id) } ──
     *
     * Optional에 값이 없으면(주문이 없으면) 예외를 던진다.
     * GlobalExceptionHandler에서 404 Not Found로 변환.
     *
     * ── Java Optional vs Kotlin nullable ──
     *
     * Spring Data JPA의 findById()는 Java의 Optional<T>를 반환한다.
     * Kotlin에서는 보통 nullable(T?)을 사용하지만,
     * JPA 인터페이스가 Java로 정의되어 있으므로 Optional을 그대로 사용한다.
     *
     * orElseThrow 패턴은 Spring/JPA 커뮤니티에서 가장 관용적인 방법이다.
     *
     * @param id 조회할 주문의 PK
     * @return SalesOrder 엔티티 (JPA managed 상태)
     * @throws EntityNotFoundException 해당 주문이 없을 때 (404)
     */
    private fun findOrderOrThrow(id: Int): SalesOrder =
        orderRepo.findById(id)                                  // Optional<SalesOrder>
            .orElseThrow { EntityNotFoundException("주문", id) } // 없으면 404
}
