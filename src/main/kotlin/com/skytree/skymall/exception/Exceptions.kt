package com.skytree.skymall.exception

/**
 * 도메인 예외 계층
 *
 * - BusinessException: 비즈니스 규칙 위반 (400 계열)
 * - EntityNotFoundException: 엔티티 조회 실패 (404)
 * - DuplicateException: 중복 데이터 (409)
 * - InsufficientStockException: 재고 부족 (422)
 *
 * 모두 RuntimeException — 체크 예외는 사용하지 않는다.
 * Service에서 던지고, Controller(또는 ExceptionHandler)에서 처리한다.
 */
open class BusinessException(message: String) : RuntimeException(message)

class EntityNotFoundException(entity: String, id: Any) :
    BusinessException("${entity}을(를) 찾을 수 없습니다: $id")

class DuplicateException(field: String, value: Any) :
    BusinessException("이미 존재하는 ${field}입니다: $value")

class InsufficientStockException(productName: String, requested: Int, available: Int) :
    BusinessException("재고 부족 — $productName: 요청 ${requested}개, 재고 ${available}개")
