package com.skytree.skymall.service

// ── import 설명 ──
// JwtProvider: JWT 토큰 생성/검증을 담당하는 커스텀 컴포넌트
import com.skytree.skymall.config.JwtProvider
// DTO (Data Transfer Object): Controller <-> Service 간 데이터 전달 객체
// Entity를 직접 노출하지 않고, 필요한 필드만 담은 DTO를 사용한다.
import com.skytree.skymall.dto.LoginRequest
import com.skytree.skymall.dto.RefreshRequest
import com.skytree.skymall.dto.RegisterRequest
import com.skytree.skymall.dto.TokenResponse
// RefreshToken 엔티티: refresh_tokens 테이블과 매핑되는 JPA 엔티티
import com.skytree.skymall.entity.RefreshToken
// User 엔티티: users 테이블과 매핑되는 JPA 엔티티
import com.skytree.skymall.entity.User
// 커스텀 예외 클래스들 (GlobalExceptionHandler에서 HTTP 상태 코드로 변환)
import com.skytree.skymall.exception.BusinessException
import com.skytree.skymall.exception.DuplicateException
// JPA Repository: 데이터베이스 접근 계층
import com.skytree.skymall.repository.RefreshTokenRepository
import com.skytree.skymall.repository.UserRepository
// Spring Security의 비밀번호 인코더 인터페이스
// 구현체는 SecurityConfig에서 BCryptPasswordEncoder로 빈 등록되어 있다.
import org.springframework.security.crypto.password.PasswordEncoder
// @Service: 이 클래스를 Spring 빈으로 등록하고, "서비스 계층"임을 명시한다.
import org.springframework.stereotype.Service
// @Transactional: 메서드 실행을 데이터베이스 트랜잭션으로 감싼다.
// 성공하면 커밋, RuntimeException 발생 시 롤백.
import org.springframework.transaction.annotation.Transactional
// Java 표준 암호화 API - SHA-256 해시 계산에 사용
import java.security.MessageDigest
// Java 8+ 날짜/시간 API - Refresh Token 만료일 계산에 사용
import java.time.LocalDateTime

/**
 * ================================================================
 * 인증 서비스 (AuthService)
 * ================================================================
 *
 * JWT 기반 인증의 핵심 비즈니스 로직을 담당한다.
 * Controller는 HTTP 요청/응답만 처리하고, 실제 로직은 모두 여기에 있다.
 *
 * ── 이 서비스가 사용하는 의존성 4개 ──
 *
 *   AuthService
 *     +-- UserRepository          : 사용자 조회/저장 (DB 접근)
 *     +-- RefreshTokenRepository  : 리프레시 토큰 저장/조회/폐기 (DB 접근)
 *     +-- JwtProvider             : JWT 토큰 생성/검증 (암호화 로직)
 *     +-- PasswordEncoder         : 비밀번호 BCrypt 해시/비교 (보안)
 *
 * ── 인증 흐름 전체 그림 ──
 *
 *   [회원가입/로그인]
 *     -> 비밀번호 BCrypt 검증
 *     -> Access Token (15분) + Refresh Token (7일) 발급
 *     -> Refresh Token의 SHA-256 해시를 DB에 저장
 *     -> 클라이언트에게 두 토큰 모두 반환
 *
 *   [API 호출]
 *     -> 클라이언트: Authorization: Bearer {accessToken} 헤더 전송
 *     -> JwtAuthFilter에서 Access Token 검증
 *     -> SecurityContext에 인증 정보 설정
 *     -> Controller 메서드 실행
 *
 *   [토큰 갱신 (Rotation)]
 *     -> 클라이언트: POST /api/auth/refresh {refreshToken}
 *     -> 1. JWT 서명/만료 검증 (위조/만료된 토큰 거부)
 *     -> 2. 토큰 타입 확인 (type == "refresh"인지)
 *     -> 3. SHA-256 해시 계산 -> DB에서 조회 (revoked=false인지)
 *     -> 4. 기존 토큰 revoked=true로 변경 (재사용 방지)
 *     -> 5. 새 토큰 쌍 발급 + 새 해시 DB 저장
 *
 *   [로그아웃]
 *     -> 클라이언트: POST /api/auth/logout (Access Token 필요)
 *     -> 해당 사용자의 모든 Refresh Token revoked=true
 *     -> Access Token은 자연 만료 (15분 대기)
 *     -> (즉시 무효화가 필요하면 Token Blacklist 패턴 사용 - 이 예제에서는 다루지 않음)
 *
 * ── @Service ──
 *
 * Spring의 컴포넌트 스캔이 이 어노테이션을 발견하면:
 * 1. 이 클래스의 인스턴스를 생성한다 (싱글톤)
 * 2. Spring IoC 컨테이너에 빈으로 등록한다
 * 3. 생성자에 선언된 의존성(UserRepository 등)을 자동 주입한다
 *
 * @Service는 @Component의 특수화 버전이다.
 * 기능적으로는 @Component와 동일하지만, "이 클래스는 비즈니스 로직 계층"이라는
 * 의미를 명시적으로 전달한다.
 *
 * ── @Transactional(readOnly = true) — 클래스 레벨 ──
 *
 * 클래스의 모든 public 메서드에 기본 트랜잭션 설정을 적용한다.
 *
 * readOnly = true의 효과:
 * 1. Hibernate가 dirty checking(변경 감지)을 생략한다 -> 성능 향상
 *    - 영속성 컨텍스트에 스냅샷을 저장하지 않아 메모리 절약
 * 2. JDBC 커넥션에 readOnly 힌트를 설정한다
 *    - MySQL의 경우 Replica(읽기 전용 서버)로 라우팅 가능
 * 3. flush 모드가 MANUAL로 설정되어 불필요한 DB 쓰기 방지
 *
 * 쓰기가 필요한 메서드에는 @Transactional (readOnly 없음)을 개별 선언하여
 * 클래스 레벨 설정을 오버라이드한다. 이것이 Spring의 "메서드 레벨 우선" 규칙이다.
 *
 * ── 생성자 주입 (Constructor Injection) ──
 *
 * Kotlin의 주 생성자(primary constructor)에 의존성을 선언하면
 * Spring이 자동으로 주입한다. @Autowired 어노테이션이 필요 없다.
 * (단일 생성자인 경우 Spring Boot가 자동으로 @Autowired를 적용)
 *
 * private val: 주입받은 의존성을 불변 프로퍼티로 저장한다.
 * - private: 외부에서 직접 접근 불가
 * - val: 한번 할당되면 변경 불가 (Java의 final과 유사)
 */
@Service
@Transactional(readOnly = true)
class AuthService(
    private val userRepo: UserRepository,              // 사용자 테이블 접근
    private val refreshTokenRepo: RefreshTokenRepository, // 리프레시 토큰 테이블 접근
    private val jwtProvider: JwtProvider,              // JWT 토큰 생성/검증
    private val passwordEncoder: PasswordEncoder       // BCrypt 비밀번호 해시/비교
) {

    // ════════════════════════════════════════════════════════════════
    //  회원가입 (Register)
    // ════════════════════════════════════════════════════════════════

    /**
     * 회원가입 처리
     *
     * 실행 흐름:
     * 1. 사용자명 중복 검사 -> 이미 있으면 409 Conflict
     * 2. 이메일 중복 검사 -> 이미 있으면 409 Conflict
     * 3. 비밀번호를 BCrypt로 해시하여 User 엔티티 생성
     * 4. DB에 저장 (INSERT INTO users ...)
     * 5. Access Token + Refresh Token 발급
     * 6. Refresh Token 해시를 DB에 저장
     * 7. 클라이언트에게 토큰 쌍 + 사용자 정보 반환
     *
     * ── @Transactional (readOnly 없음 = 읽기+쓰기) ──
     *
     * 이 메서드는 DB에 데이터를 쓰므로 readOnly = true이면 안 된다.
     * 메서드 레벨의 @Transactional이 클래스 레벨의 readOnly = true를 오버라이드한다.
     *
     * 이 하나의 트랜잭션 안에서 처리되는 DB 작업:
     * - SELECT users WHERE username = ? (중복 검사)
     * - SELECT users WHERE email = ? (중복 검사)
     * - INSERT INTO users (...) VALUES (...) (사용자 생성)
     * - INSERT INTO refresh_tokens (...) VALUES (...) (토큰 저장)
     *
     * 어디서든 예외가 발생하면 위의 모든 작업이 롤백된다.
     *
     * @param req 회원가입 요청 DTO (username, email, password, role)
     * @return TokenResponse (accessToken, refreshToken, userId, username, role)
     * @throws DuplicateException 사용자명 또는 이메일이 이미 존재할 때 (409)
     */
    @Transactional
    fun register(req: RegisterRequest): TokenResponse {

        // ── 1단계: 사용자명 중복 검사 ──
        //
        // userRepo.findByUsername(req.username):
        //   UserRepository의 파생 쿼리 메서드.
        //   Spring Data JPA가 메서드명을 파싱하여 자동으로 SQL을 생성한다:
        //   → SELECT * FROM users WHERE username = ?
        //   반환 타입: User? (Kotlin nullable)
        //   - 사용자가 있으면: User 객체
        //   - 사용자가 없으면: null
        //
        // ?.let { ... }:
        //   Kotlin의 스코프 함수. null이 아닌 경우에만 블록을 실행한다.
        //   여기서는 "이미 존재하는 경우"에만 예외를 던진다.
        //
        //   동등한 Java 코드:
        //   User existing = userRepo.findByUsername(req.username);
        //   if (existing != null) {
        //       throw new DuplicateException("사용자명", req.username);
        //   }
        //
        // DuplicateException:
        //   커스텀 예외. GlobalExceptionHandler에서 409 Conflict로 변환된다.
        //   클라이언트에게 "이미 존재하는 사용자명입니다" 메시지가 전달된다.
        userRepo.findByUsername(req.username)?.let {
            throw DuplicateException("사용자명", req.username)
        }

        // ── 2단계: 이메일 중복 검사 ──
        // 사용자명과 동일한 패턴으로 이메일 중복을 검사한다.
        // 이메일은 비밀번호 찾기, 알림 발송 등에 사용되므로 유일해야 한다.
        userRepo.findByEmail(req.email)?.let {
            throw DuplicateException("이메일", req.email)
        }

        // ── 3단계: 비밀번호 BCrypt 해시 + User 엔티티 생성 & 저장 ──
        //
        // passwordEncoder.encode(req.password):
        //   평문 비밀번호를 BCrypt 알고리즘으로 해시한다.
        //   예: "mypassword" -> "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy"
        //
        //   BCrypt의 특징:
        //   - 같은 비밀번호라도 매번 다른 해시가 생성된다 (salt가 포함되므로)
        //   - 해시에서 원본 비밀번호를 복원할 수 없다 (단방향 해시)
        //   - 비용 인자(cost factor)로 연산 속도를 조절할 수 있다
        //
        // !! (non-null assertion):
        //   passwordEncoder.encode()는 Java 메서드라 Kotlin에서 반환 타입이 String!
        //   (플랫폼 타입 = null 가능성 불확실)
        //   실제로 null을 반환하지 않으므로 !!로 단언한다.
        //
        // userRepo.save(user):
        //   JPA의 save() 메서드. 새 엔티티이므로 INSERT 쿼리를 실행한다.
        //   → INSERT INTO users (username, email, password, role, ...) VALUES (?, ?, ?, ?, ...)
        //   저장 후 DB가 생성한 id가 반환된 엔티티에 설정된다.
        //
        // User(...):
        //   Kotlin의 named argument 문법으로 생성자를 호출한다.
        //   Java의 new User(username, email, password, role)보다 가독성이 좋다.
        val user = userRepo.save(
            User(
                username = req.username,              // 사용자명 (로그인 ID)
                email = req.email,                    // 이메일 (유일 제약)
                password = passwordEncoder.encode(req.password)!!, // BCrypt 해시된 비밀번호
                role = req.role                       // 권한 (USER, MANAGER, ADMIN)
            )
        )

        // ── 4단계: 토큰 쌍 발급 + Refresh Token DB 저장 ──
        // issueTokens()는 이 클래스의 private 메서드.
        // Access Token + Refresh Token을 동시에 생성하고,
        // Refresh Token의 SHA-256 해시를 DB에 저장한 뒤,
        // TokenResponse DTO를 반환한다.
        return issueTokens(user)
    }

    // ════════════════════════════════════════════════════════════════
    //  로그인 (Login)
    // ════════════════════════════════════════════════════════════════

    /**
     * 로그인 처리
     *
     * 실행 흐름:
     * 1. 사용자명으로 User 조회 -> 없으면 400 Bad Request
     * 2. 계정 활성화 여부 확인 -> 비활성화 계정이면 400
     * 3. 비밀번호 BCrypt 비교 -> 불일치하면 400
     * 4. 마지막 로그인 시각 갱신 (dirty checking으로 자동 UPDATE)
     * 5. 토큰 쌍 발급
     *
     * ── 보안 주의사항 ──
     *
     * "사용자명이 틀렸습니다" vs "비밀번호가 틀렸습니다"를 구분하지 않고
     * "잘못된 사용자명 또는 비밀번호입니다"라는 동일한 메시지를 반환한다.
     * 이는 공격자가 유효한 사용자명을 탐색하는 것(User Enumeration)을 방지하기 위함이다.
     *
     * @param req 로그인 요청 DTO (username, password)
     * @return TokenResponse (accessToken, refreshToken, userId, username, role)
     * @throws BusinessException 잘못된 자격 증명 또는 비활성화 계정 (400)
     */
    @Transactional
    fun login(req: LoginRequest): TokenResponse {

        // ── 1단계: 사용자명으로 DB에서 User 조회 ──
        //
        // userRepo.findByUsername(req.username):
        //   → SELECT * FROM users WHERE username = ?
        //   반환: User? (없으면 null)
        //
        // ?: throw BusinessException(...):
        //   Kotlin의 Elvis 연산자.
        //   "왼쪽이 null이면 오른쪽을 실행한다"
        //   즉, 사용자가 없으면 예외를 던진다.
        //
        //   동등한 Java 코드:
        //   User user = userRepo.findByUsername(req.username);
        //   if (user == null) {
        //       throw new BusinessException("잘못된 사용자명 또는 비밀번호입니다");
        //   }
        val user = userRepo.findByUsername(req.username)
            ?: throw BusinessException("잘못된 사용자명 또는 비밀번호입니다")

        // ── 2단계: 계정 활성화 여부 확인 ──
        // User.isEnabled 필드: 관리자가 계정을 비활성화할 수 있다.
        // 비활성화된 계정은 로그인을 차단한다.
        if (!user.isEnabled) {
            throw BusinessException("비활성화된 계정입니다")
        }

        // ── 3단계: 비밀번호 검증 ──
        //
        // passwordEncoder.matches(rawPassword, encodedPassword):
        //   평문 비밀번호와 BCrypt 해시를 비교한다.
        //   내부적으로 rawPassword를 BCrypt로 해시한 후, encodedPassword와 비교한다.
        //   BCrypt에는 salt가 포함되어 있어, 같은 해시 파라미터로 재해시하여 비교한다.
        //
        //   예: matches("mypassword", "$2a$10$N9qo8uLO...") -> true
        //       matches("wrong",      "$2a$10$N9qo8uLO...") -> false
        //
        // 주의: == 연산자로 비교하면 안 된다! BCrypt는 같은 비밀번호라도
        // 매번 다른 해시를 생성하므로 문자열 비교로는 검증할 수 없다.
        if (!passwordEncoder.matches(req.password, user.password)) {
            throw BusinessException("잘못된 사용자명 또는 비밀번호입니다")
        }

        // ── 4단계: 마지막 로그인 시각 갱신 ──
        //
        // user.lastLoginAt = LocalDateTime.now():
        //   JPA 엔티티의 필드 값을 직접 변경한다.
        //
        // 이것이 JPA의 "Dirty Checking (변경 감지)" 기능이다:
        // 1. @Transactional 메서드 시작 시, JPA가 엔티티의 원본 상태를 스냅샷으로 보관
        // 2. 메서드 내에서 엔티티 필드 값 변경 (이 라인)
        // 3. 트랜잭션 커밋 시, JPA가 스냅샷과 현재 상태를 비교
        // 4. 변경된 필드가 있으면 자동으로 UPDATE 쿼리 실행:
        //    → UPDATE users SET last_login_at = ? WHERE id = ?
        //
        // 따라서 userRepo.save(user)를 다시 호출할 필요가 없다!
        // (JPA가 managed 상태의 엔티티 변경을 자동으로 감지한다)
        user.lastLoginAt = LocalDateTime.now()

        // ── 5단계: 토큰 쌍 발급 ──
        return issueTokens(user)
    }

    // ════════════════════════════════════════════════════════════════
    //  토큰 갱신 (Refresh Token Rotation)
    // ════════════════════════════════════════════════════════════════

    /**
     * Refresh Token으로 새 토큰 쌍 발급 (Rotation 패턴)
     *
     * ── Refresh Token Rotation이란? ──
     *
     * 갱신할 때마다 이전 Refresh Token을 폐기하고 새 Refresh Token을 발급하는 패턴.
     *
     * 왜 필요한가?
     * - 만약 공격자가 Refresh Token을 탈취하여 갱신하면,
     *   정상 사용자의 토큰은 이미 폐기(revoked)된 상태이므로 갱신에 실패한다.
     * - 이를 통해 토큰 탈취를 감지할 수 있다.
     * - 반면, Rotation 없이 같은 Refresh Token을 계속 사용하면,
     *   공격자와 정상 사용자가 동시에 같은 토큰으로 API를 호출할 수 있어 위험하다.
     *
     * ── 검증 단계 (4단계) ──
     *
     * 1. JWT 서명/만료 검증: 토큰이 위조되지 않았고 만료되지 않았는지
     * 2. 토큰 타입 확인: type 클레임이 "refresh"인지 (Access Token으로 갱신 방지)
     * 3. DB 해시 조회: SHA-256 해시가 DB에 존재하고 revoked=false인지
     * 4. Rotation: 기존 토큰 폐기 -> 새 토큰 쌍 발급
     *
     * @param req RefreshRequest (refreshToken 문자열)
     * @return TokenResponse (새 accessToken, 새 refreshToken, 사용자 정보)
     * @throws BusinessException 유효하지 않은 토큰, 잘못된 타입, 이미 사용된 토큰 (400)
     */
    @Transactional
    fun refresh(req: RefreshRequest): TokenResponse {

        // 요청에서 Refresh Token 원본 문자열을 꺼낸다.
        // 이 값은 클라이언트가 로그인/갱신 시 받았던 JWT 문자열이다.
        // 예: "eyJhbGciOiJIUzM4NCJ9.eyJ0eXBlIjoicmVmcmVzaCIsInN1Yi..."
        val rawToken = req.refreshToken

        // ── 1단계: JWT 서명 + 만료 검증 ──
        //
        // jwtProvider.validateToken(rawToken):
        //   JWT의 HMAC-SHA384 서명을 검증하고, 만료 시간을 확인한다.
        //   - 서명이 올바르지 않으면 (위조된 토큰): false
        //   - 만료 시간이 지났으면: false
        //   - 정상: true
        //
        //   내부적으로 io.jsonwebtoken.Jwts.parser()를 사용한다.
        //   잘못된 토큰이면 JwtException이 발생하고, catch하여 false를 반환한다.
        if (!jwtProvider.validateToken(rawToken)) {
            throw BusinessException("유효하지 않거나 만료된 리프레시 토큰입니다")
        }

        // ── 2단계: 토큰 타입 확인 ──
        //
        // jwtProvider.getTokenType(rawToken):
        //   JWT의 Claims에서 "type" 클레임을 추출한다.
        //   - Access Token: type = "access"
        //   - Refresh Token: type = "refresh"
        //
        // 왜 이 검사가 필요한가?
        //   Access Token으로 갱신을 시도하는 것을 방지하기 위함이다.
        //   Access Token과 Refresh Token은 같은 비밀키로 서명되므로,
        //   타입을 구분하지 않으면 Access Token으로도 갱신이 가능해진다.
        if (jwtProvider.getTokenType(rawToken) != "refresh") {
            throw BusinessException("리프레시 토큰이 아닙니다")
        }

        // ── 3단계: SHA-256 해시로 DB 조회 ──
        //
        // sha256(rawToken):
        //   Refresh Token 원본을 SHA-256으로 해시한다.
        //   DB에는 원본이 아닌 해시값만 저장되어 있으므로,
        //   원본을 해시하여 DB의 해시값과 비교해야 한다.
        //
        //   왜 해시로 저장하나?
        //   DB가 유출(SQL injection, 백업 파일 탈취 등)되어도
        //   해시에서 원본 토큰을 복원할 수 없어 안전하다.
        //
        // refreshTokenRepo.findByTokenHashAndRevokedFalse(tokenHash):
        //   Spring Data JPA 파생 쿼리.
        //   메서드명을 파싱하여 자동 생성되는 SQL:
        //   → SELECT * FROM refresh_tokens
        //     WHERE token_hash = ? AND revoked = false
        //
        //   반환: RefreshToken? (null이면 해당 토큰이 없거나 이미 폐기됨)
        //
        // ?: throw BusinessException(...):
        //   DB에서 찾지 못하면 = 이미 사용되었거나 폐기된 토큰
        //   → 400 Bad Request
        val tokenHash = sha256(rawToken)
        val storedToken = refreshTokenRepo.findByTokenHashAndRevokedFalse(tokenHash)
            ?: throw BusinessException("이미 사용되었거나 폐기된 리프레시 토큰입니다")

        // ── 4단계: Rotation — 기존 토큰 폐기 + 새 토큰 쌍 발급 ──
        //
        // storedToken.revoked = true:
        //   JPA Dirty Checking으로 자동 UPDATE.
        //   → UPDATE refresh_tokens SET revoked = true WHERE id = ?
        //
        //   이 시점부터 기존 Refresh Token은 더 이상 사용할 수 없다.
        //   만약 공격자가 탈취한 토큰으로 갱신을 시도하면,
        //   이미 revoked=true이므로 3단계에서 실패한다.
        //
        // storedToken.user:
        //   RefreshToken 엔티티의 @ManyToOne 관계로 연결된 User 엔티티.
        //   JPA가 자동으로 JOIN하여 User를 로드한다 (FetchType.LAZY이지만
        //   같은 트랜잭션 내이므로 접근 시 자동 로드).
        //
        // issueTokens(storedToken.user):
        //   새 Access Token + 새 Refresh Token을 발급하고,
        //   새 Refresh Token의 해시를 DB에 INSERT한다.
        storedToken.revoked = true
        return issueTokens(storedToken.user)
    }

    // ════════════════════════════════════════════════════════════════
    //  로그아웃 (Logout)
    // ════════════════════════════════════════════════════════════════

    /**
     * 로그아웃 처리
     *
     * 해당 사용자의 모든 유효한 Refresh Token을 한 번에 폐기한다.
     * 이렇게 하면 모든 기기/세션에서 동시에 로그아웃되는 효과가 있다.
     *
     * ── Access Token은 즉시 무효화할 수 없다 ──
     *
     * Access Token은 서버에 저장되지 않는 "무상태(stateless)" 토큰이다.
     * JWT 자체에 만료 시간이 포함되어 있으므로, 서버가 개별 토큰을 무효화할 수 없다.
     * 따라서 Access Token은 만료 시간(15분)이 지나면 자연스럽게 무효화된다.
     *
     * 만약 Access Token의 즉시 무효화가 필요하다면:
     * - Token Blacklist: 로그아웃된 Access Token의 jti를 Redis에 저장
     * - 매 요청마다 Blacklist를 확인하여 거부
     * - 이 예제에서는 복잡도를 줄이기 위해 다루지 않는다
     *
     * @param userId 로그아웃할 사용자의 ID (Controller에서 JWT로부터 추출하여 전달)
     */
    @Transactional
    fun logout(userId: Int) {

        // refreshTokenRepo.revokeAllByUserId(userId):
        //   RefreshTokenRepository에 정의된 @Modifying @Query 메서드.
        //   벌크 UPDATE 쿼리를 실행한다:
        //   → UPDATE refresh_tokens SET revoked = true
        //     WHERE user_id = ? AND revoked = false
        //
        //   벌크 UPDATE의 장점:
        //   - 여러 행을 한 번의 SQL로 처리 (N개의 토큰 -> 1번의 쿼리)
        //   - JPA의 엔티티 단위 UPDATE보다 훨씬 효율적
        //
        //   @Modifying 어노테이션:
        //   - SELECT가 아닌 UPDATE/DELETE 쿼리임을 JPA에 알린다
        //   - 이 어노테이션이 없으면 "Not supported for DML operations" 에러 발생
        refreshTokenRepo.revokeAllByUserId(userId)
    }

    // ════════════════════════════════════════════════════════════════
    //  토큰 쌍 발급 (내부 헬퍼 메서드)
    // ════════════════════════════════════════════════════════════════

    /**
     * Access Token + Refresh Token 동시 발급 및 DB 저장
     *
     * 이 메서드는 register(), login(), refresh() 세 곳에서 호출된다.
     * 토큰 발급 로직을 한 곳에 집중시켜 중복을 제거했다 (DRY 원칙).
     *
     * 처리 순서:
     * 1. JwtProvider로 Access Token 생성 (수명: 15분)
     * 2. JwtProvider로 Refresh Token 생성 (수명: 7일)
     * 3. Refresh Token을 SHA-256 해시하여 DB에 저장
     * 4. TokenResponse DTO로 묶어서 반환
     *
     * ── private 접근 제한자 ──
     *
     * 이 메서드는 AuthService 내부에서만 사용하므로 private으로 선언한다.
     * 외부(Controller, 다른 Service)에서 직접 토큰을 발급하는 것을 방지한다.
     *
     * @param user 토큰을 발급받을 사용자 엔티티
     * @return TokenResponse (accessToken, refreshToken, userId, username, role)
     */
    private fun issueTokens(user: User): TokenResponse {

        // ── Access Token 생성 ──
        //
        // jwtProvider.generateAccessToken(userId, username, role):
        //   JWT를 생성한다. 내부 구조:
        //   {
        //     "sub": "testuser",          ← subject (사용자명)
        //     "userId": 1,                ← 커스텀 클레임
        //     "role": "USER",             ← 커스텀 클레임
        //     "type": "access",           ← 토큰 유형 구분
        //     "jti": "550e8400-e29b...",   ← 고유 ID (UUID)
        //     "iat": 1709136000,          ← 발급 시각 (Unix timestamp)
        //     "exp": 1709136900           ← 만료 시각 (발급 + 15분)
        //   }
        //
        //   user.id: 사용자 PK (Int). JWT의 "userId" 클레임에 포함된다.
        //   user.username: 사용자명. JWT의 "sub" (subject) 클레임에 포함된다.
        //   user.role.name: Enum의 문자열 이름 (예: "USER", "ADMIN", "MANAGER")
        val accessToken = jwtProvider.generateAccessToken(
            user.id,          // userId 클레임
            user.username,    // subject 클레임
            user.role.name    // role 클레임
        )

        // ── Refresh Token 생성 ──
        //
        // Access Token과 구조는 동일하지만:
        // - type = "refresh" (JwtAuthFilter에서 API 접근 차단)
        // - 만료 시간 = 7일 (Access Token의 672배)
        // - 용도: Access Token이 만료되었을 때 새 토큰을 발급받는 데만 사용
        val refreshToken = jwtProvider.generateRefreshToken(
            user.id,
            user.username,
            user.role.name
        )

        // ── Refresh Token 해시를 DB에 저장 ──
        //
        // 왜 원본이 아닌 해시를 저장하나?
        //   보안 원칙: "서버가 보관하는 비밀은 해시로 저장한다"
        //   비밀번호를 BCrypt로 해시하여 저장하는 것과 같은 원리이다.
        //   DB가 유출되어도 원본 Refresh Token을 복원할 수 없다.
        //
        // 왜 BCrypt가 아닌 SHA-256을 사용하나?
        //   BCrypt는 의도적으로 느린 해시(brute-force 방어)이지만,
        //   Refresh Token은 이미 충분히 긴 랜덤 문자열(JWT)이므로
        //   brute-force 공격이 현실적으로 불가능하다.
        //   SHA-256은 빠르므로 매 요청마다 해시 계산 비용이 낮다.
        //
        // sha256(refreshToken):
        //   이 클래스의 private 메서드. JWT 문자열을 SHA-256으로 해시한다.
        //   예: "eyJhbGci..." -> "a1b2c3d4e5f6..."
        //
        // refreshTokenRepo.save(RefreshToken(...)):
        //   → INSERT INTO refresh_tokens (user_id, token_hash, expires_at, created_at, revoked)
        //     VALUES (?, ?, ?, NOW(), false)
        //
        // jwtProvider.getRefreshExpirationMs():
        //   application.yaml의 jwt.refresh-expiration 값 (밀리초 단위).
        //   604800000ms = 7일
        //
        // LocalDateTime.now().plusSeconds(...):
        //   현재 시각 + 만료 시간 = 토큰 만료 일시
        //   밀리초를 초로 변환: / 1000
        //   예: 604800000 / 1000 = 604800초 = 7일
        refreshTokenRepo.save(
            RefreshToken(
                user = user,                     // @ManyToOne 관계 설정
                tokenHash = sha256(refreshToken), // SHA-256 해시값
                expiresAt = LocalDateTime.now()    // 만료 일시 계산
                    .plusSeconds(jwtProvider.getRefreshExpirationMs() / 1000)
            )
        )

        // ── TokenResponse DTO 생성 및 반환 ──
        //
        // TokenResponse:
        //   Controller가 클라이언트에게 반환할 JSON 응답 구조.
        //   {
        //     "accessToken": "eyJhbGci...",
        //     "refreshToken": "eyJhbGci...",
        //     "userId": 1,
        //     "username": "testuser",
        //     "role": "USER"
        //   }
        //
        // 주의: DB에는 해시만 저장하고, 원본 토큰은 이 응답으로만 클라이언트에 전달된다.
        // 서버는 원본 Refresh Token을 보관하지 않는다!
        return TokenResponse(
            accessToken = accessToken,     // 15분 수명, API 호출에 사용
            refreshToken = refreshToken,   // 7일 수명, 토큰 갱신에만 사용
            userId = user.id,              // 사용자 PK
            username = user.username,      // 사용자명
            role = user.role               // 권한 (Enum)
        )
    }

    // ════════════════════════════════════════════════════════════════
    //  SHA-256 해시 유틸리티
    // ════════════════════════════════════════════════════════════════

    /**
     * 문자열을 SHA-256으로 해시하여 16진수 문자열로 반환한다.
     *
     * ── 처리 과정 ──
     *
     * 1. MessageDigest.getInstance("SHA-256"):
     *    Java 표준 암호화 API에서 SHA-256 해시 엔진을 가져온다.
     *    MessageDigest는 java.security 패키지의 클래스로,
     *    다양한 해시 알고리즘(MD5, SHA-1, SHA-256, SHA-512 등)을 지원한다.
     *
     * 2. .digest(input.toByteArray()):
     *    문자열을 바이트 배열로 변환한 후 SHA-256 해시를 계산한다.
     *    반환: ByteArray (32바이트 = 256비트)
     *    예: [0x2c, 0xf2, 0x4d, 0xba, ...] (32개의 바이트)
     *
     * 3. .joinToString("") { "%02x".format(it) }:
     *    각 바이트를 2자리 16진수 문자열로 변환하고 연결한다.
     *    - "%02x": 2자리 소문자 16진수 (0 패딩)
     *    - 예: 0x2c -> "2c", 0x0f -> "0f"
     *    - 결과: 64자의 16진수 문자열
     *    예: "2cf24dba5fb0a30e26e83b2ac5b9e29e..."
     *
     * ── 왜 SHA-256인가? ──
     *
     * - 단방향 해시: 해시에서 원본을 역산할 수 없다
     * - 충돌 저항성: 서로 다른 입력이 같은 해시를 만들 확률이 극히 낮다
     * - 빠른 계산: 매 토큰 갱신 요청마다 해시를 계산해야 하므로 속도가 중요
     * - 업계 표준: NIST가 권장하는 해시 알고리즘
     *
     * @param input 해시할 원본 문자열 (Refresh Token JWT)
     * @return 64자의 16진수 SHA-256 해시 문자열
     */
    private fun sha256(input: String): String =
        MessageDigest.getInstance("SHA-256")       // SHA-256 해시 엔진 획득
            .digest(input.toByteArray())            // 바이트 배열로 변환 후 해시 계산
            .joinToString("") { "%02x".format(it) } // 각 바이트를 16진수로 변환하여 연결
}
