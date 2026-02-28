package com.skytree.skymall

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * ================================================================
 * SkyMall 애플리케이션 진입점
 * ================================================================
 *
 * @SpringBootApplication은 다음 3개 어노테이션을 합친 것이다:
 *
 * 1. @Configuration
 *    - 이 클래스 자체가 스프링 설정 파일 역할
 *    - @Bean 메서드를 정의할 수 있다
 *
 * 2. @EnableAutoConfiguration
 *    - 클래스패스에 있는 라이브러리를 감지하여 자동 설정
 *    - 예: spring-boot-starter-data-jpa가 있으면 → JPA 자동 설정
 *    - 예: spring-boot-starter-security가 있으면 → Security 자동 설정
 *
 * 3. @ComponentScan
 *    - 이 클래스가 속한 패키지(com.skytree.skymall) 하위를 스캔
 *    - @Component, @Service, @Repository, @Controller 등을 찾아 빈으로 등록
 *
 * ================================================================
 * 스프링 부트 애플리케이션의 전체 동작 흐름:
 *
 *   main() 실행
 *     → runApplication<SkymallApplication>()
 *       → 내장 톰캣 서버 시작 (port: 9090)
 *       → 컴포넌트 스캔 → 빈 등록 (Service, Repository, Controller 등)
 *       → DataSource 자동 생성 (application.yaml의 DB 설정)
 *       → JPA EntityManagerFactory 생성
 *       → Hibernate DDL 실행 (ddl-auto: update)
 *       → HTTP 요청 수신 대기
 *
 * ================================================================
 */
@SpringBootApplication
class SkymallApplication

/**
 * 애플리케이션의 main 함수.
 *
 * args: 커맨드라인 인자 (예: --server.port=8080으로 포트 변경 가능)
 * *args: 배열을 가변인자(vararg)로 펼치는 Kotlin 스프레드 연산자
 */
fun main(args: Array<String>) {
	runApplication<SkymallApplication>(*args)
}
