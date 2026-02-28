package com.skytree.skymall.exception

/**
 * ================================================================
 * 도메인 예외 계층 (Custom Exception Hierarchy)
 * ================================================================
 *
 * 스프링에서 예외를 처리하는 패턴:
 *
 *   Service 계층 → 비즈니스 규칙 위반 시 예외를 던진다 (throw)
 *   Controller 계층 → @RestControllerAdvice가 예외를 잡아서 HTTP 응답으로 변환
 *
 * ── 예외 계층 구조 ──
 *
 *   RuntimeException (Java 표준)
 *     └─ BusinessException              ← 400 Bad Request
 *          ├─ EntityNotFoundException    ← 404 Not Found
 *          ├─ DuplicateException         ← 409 Conflict
 *          └─ InsufficientStockException ← 422 Unprocessable Entity
 *
 * ── 왜 RuntimeException인가? ──
 *
 * Java에는 2가지 예외가 있다:
 * - Checked Exception: 반드시 try-catch로 처리해야 함 (컴파일 에러)
 * - Unchecked Exception (RuntimeException): 처리 강제 안 함
 *
 * Spring에서는 Unchecked Exception을 권장한다:
 * 1. 코드가 깔끔해진다 (모든 곳에 try-catch 안 써도 됨)
 * 2. @Transactional은 RuntimeException에서만 자동 롤백한다
 * 3. @RestControllerAdvice에서 한 곳에서 처리
 *
 * ── open class ──
 * Kotlin에서 클래스는 기본적으로 final(상속 불가)이다.
 * 하위 예외 클래스가 상속할 수 있도록 open 키워드를 붙인다.
 */

/**
 * 비즈니스 예외 (기본 예외)
 * HTTP 400 Bad Request로 매핑된다.
 *
 * 사용 예:
 *   throw BusinessException("주문 항목이 비어있습니다")
 *   throw BusinessException("비활성화된 계정입니다")
 */
open class BusinessException(message: String) : RuntimeException(message)

/**
 * 엔티티 조회 실패 예외
 * HTTP 404 Not Found로 매핑된다.
 *
 * entity: 엔티티 종류 ("상품", "사용자", "주문")
 * id: 조회한 식별자
 *
 * 사용 예:
 *   throw EntityNotFoundException("상품", 42)
 *   → "상품을(를) 찾을 수 없습니다: 42"
 */
class EntityNotFoundException(entity: String, id: Any) :
    BusinessException("${entity}을(를) 찾을 수 없습니다: $id")

/**
 * 중복 데이터 예외
 * HTTP 409 Conflict로 매핑된다.
 *
 * field: 중복된 필드명 ("사용자명", "이메일", "카테고리명")
 * value: 중복된 값
 *
 * 사용 예:
 *   throw DuplicateException("이메일", "john@test.com")
 *   → "이미 존재하는 이메일입니다: john@test.com"
 */
class DuplicateException(field: String, value: Any) :
    BusinessException("이미 존재하는 ${field}입니다: $value")

/**
 * 재고 부족 예외
 * HTTP 422 Unprocessable Entity로 매핑된다.
 *
 * 주문 시 요청 수량이 현재 재고보다 많을 때 발생한다.
 *
 * 사용 예:
 *   throw InsufficientStockException("맥북", 10, 3)
 *   → "재고 부족 — 맥북: 요청 10개, 재고 3개"
 */
class InsufficientStockException(productName: String, requested: Int, available: Int) :
    BusinessException("재고 부족 — $productName: 요청 ${requested}개, 재고 ${available}개")
