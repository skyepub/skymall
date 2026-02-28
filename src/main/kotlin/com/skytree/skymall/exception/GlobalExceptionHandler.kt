package com.skytree.skymall.exception

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/**
 * 에러 응답 DTO
 *
 * 모든 에러 응답을 통일된 형식으로 반환한다.
 * JSON 예시: {"error": "NOT_FOUND", "message": "상품을(를) 찾을 수 없습니다: 42"}
 */
data class ErrorResponse(
    val error: String,     // 에러 코드 (NOT_FOUND, DUPLICATE 등)
    val message: String    // 사람이 읽을 수 있는 에러 메시지
)

/**
 * ================================================================
 * 전역 예외 핸들러 (Global Exception Handler)
 * ================================================================
 *
 * @RestControllerAdvice:
 *   모든 @RestController에서 발생하는 예외를 이 클래스에서 처리한다.
 *   Controller마다 try-catch를 쓰지 않아도 예외가 자동으로 여기로 전달된다.
 *
 * ── 동작 흐름 ──
 *
 *   Controller → Service에서 예외 발생!
 *     → Spring이 예외 타입에 맞는 @ExceptionHandler를 찾음
 *     → 해당 메서드가 실행되어 HTTP 응답 생성
 *     → 클라이언트에게 JSON 에러 응답 반환
 *
 * ── 예외 매핑 규칙 ──
 *
 *   EntityNotFoundException     → 404 Not Found
 *   DuplicateException          → 409 Conflict
 *   InsufficientStockException  → 422 Unprocessable Entity
 *   BusinessException           → 400 Bad Request (위에 해당 안 되는 나머지)
 *
 * ── 매칭 순서가 중요! ──
 *
 * BusinessException이 상위 클래스이므로, 하위 클래스 핸들러를 먼저 선언해야 한다.
 * Spring은 가장 구체적인(하위) 예외 타입의 핸들러를 우선 매칭한다.
 *
 *   EntityNotFoundException은 BusinessException의 하위 클래스이지만,
 *   handleNotFound()가 handleBusiness()보다 더 구체적이므로 먼저 매칭된다.
 */
@RestControllerAdvice
class GlobalExceptionHandler {

    /** 404 Not Found — 엔티티 조회 실패 */
    @ExceptionHandler(EntityNotFoundException::class)
    fun handleNotFound(e: EntityNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse("NOT_FOUND", e.message ?: ""))

    /** 409 Conflict — 중복 데이터 */
    @ExceptionHandler(DuplicateException::class)
    fun handleDuplicate(e: DuplicateException) =
        ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ErrorResponse("DUPLICATE", e.message ?: ""))

    /** 422 Unprocessable Entity — 재고 부족 */
    @ExceptionHandler(InsufficientStockException::class)
    fun handleInsufficientStock(e: InsufficientStockException) =
        ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
            .body(ErrorResponse("INSUFFICIENT_STOCK", e.message ?: ""))

    /** 400 Bad Request — 기타 비즈니스 규칙 위반 (위에 해당하지 않는 모든 BusinessException) */
    @ExceptionHandler(BusinessException::class)
    fun handleBusiness(e: BusinessException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse("BAD_REQUEST", e.message ?: ""))
}
