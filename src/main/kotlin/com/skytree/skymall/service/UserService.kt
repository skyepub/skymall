package com.skytree.skymall.service

// ── import 설명 ──
// * (star import): dto 패키지의 모든 클래스를 한 번에 가져온다.
// 개별 import 대신 사용하면 코드가 간결해지지만,
// 어떤 DTO를 사용하는지 명시적으로 보이지 않는 단점이 있다.
// 프로젝트 규모가 작을 때는 star import를 사용해도 무방하다.
import com.skytree.skymall.dto.*
// User 엔티티: users 테이블과 매핑되는 JPA 엔티티 클래스
import com.skytree.skymall.entity.User
// 커스텀 예외 클래스들 — GlobalExceptionHandler에서 HTTP 상태 코드로 변환
import com.skytree.skymall.exception.BusinessException     // -> 400 Bad Request
import com.skytree.skymall.exception.DuplicateException    // -> 409 Conflict
import com.skytree.skymall.exception.EntityNotFoundException // -> 404 Not Found
// Repository: 데이터베이스 접근 계층 (Spring Data JPA가 구현체를 자동 생성)
import com.skytree.skymall.repository.SalesOrderRepository
import com.skytree.skymall.repository.UserRepository
// Spring Data의 페이징 관련 클래스
import org.springframework.data.domain.Page     // 페이징된 결과 (데이터 + 메타 정보)
import org.springframework.data.domain.Pageable // 페이징 요청 정보 (page, size, sort)
// @Service: Spring 빈으로 등록 + "서비스 계층" 의미 표시
import org.springframework.stereotype.Service
// @Transactional: 데이터베이스 트랜잭션 관리 어노테이션
import org.springframework.transaction.annotation.Transactional

/**
 * ================================================================
 * 사용자 서비스 (UserService)
 * ================================================================
 *
 * 사용자 관리 비즈니스 로직을 담당하는 서비스 계층.
 *
 * ── 의존성 구조 ──
 *
 *   UserService
 *     +-- UserRepository          : 사용자 CRUD (users 테이블)
 *     +-- SalesOrderRepository    : 주문 이력 조회 (sales_orders 테이블)
 *
 * 왜 UserService에서 SalesOrderRepository를 사용하나?
 *   Service 계층의 핵심 역할은 "여러 Repository를 조합"하는 것이다.
 *   단순히 하나의 Repository를 호출하는 것이라면 Service 계층이 필요 없다.
 *
 * ── 복합 Repository 활용 사례 ──
 *
 * 1. 사용자 삭제 (deleteUser):
 *    UserRepo에서 사용자를 찾고, OrderRepo에서 주문 이력을 확인한다.
 *    주문이 있으면 삭제를 차단한다 (데이터 무결성 보호).
 *    → 2개의 Repository 결과를 조합하여 비즈니스 규칙을 적용하는 전형적인 패턴
 *
 * 2. 사용자 프로필 (getUserProfile):
 *    UserRepo에서 기본 정보를, OrderRepo에서 집계(count, sum, avg)를 조회하여
 *    하나의 응답으로 결합한다.
 *    → Service 계층이 없으면 Controller에서 이 조합 로직을 처리해야 하는데,
 *      그러면 Controller가 "두꺼운 계층"이 되어 테스트와 재사용이 어려워진다.
 *
 * ── internal 접근 제한자 ──
 *
 * Kotlin에는 Java에 없는 internal 접근 제한자가 있다.
 *
 * 접근 제한자 비교:
 * - private:   같은 클래스 내에서만 접근 가능
 * - internal:  같은 모듈(같은 Gradle 프로젝트) 내에서 접근 가능
 * - protected: 같은 클래스 + 하위 클래스에서 접근 가능
 * - public:    어디서든 접근 가능 (기본값)
 *
 * findUserOrThrow()를 internal로 선언한 이유:
 * - private이면 OrderService에서 사용할 수 없다
 * - public이면 Controller 등 외부에서도 접근 가능해져 의도치 않은 사용이 발생할 수 있다
 * - internal이면 같은 모듈의 Service끼리만 공유할 수 있어 적절한 균형점이다
 *
 * ── @Service 어노테이션 ──
 *
 * Spring의 컴포넌트 스캔(@ComponentScan)이 이 어노테이션을 발견하면:
 * 1. 이 클래스의 인스턴스를 생성한다 (싱글톤: 앱 전체에 단 하나의 인스턴스)
 * 2. Spring IoC 컨테이너에 빈(Bean)으로 등록한다
 * 3. 생성자에 선언된 의존성을 자동 주입한다 (DI: Dependency Injection)
 *
 * ── @Transactional(readOnly = true) — 클래스 레벨 기본 설정 ──
 *
 * 이 클래스의 모든 public 메서드에 읽기 전용 트랜잭션을 적용한다.
 *
 * readOnly = true일 때 일어나는 일:
 * 1. Hibernate의 FlushMode가 MANUAL로 설정된다
 *    -> 메서드가 끝나도 자동으로 flush(DB에 쓰기)하지 않는다
 * 2. Dirty Checking을 위한 스냅샷을 생성하지 않는다
 *    -> 엔티티를 읽기만 하면 스냅샷이 필요 없으므로 메모리 절약
 * 3. JDBC 커넥션에 readOnly=true 힌트를 설정한다
 *    -> MySQL의 경우 읽기 전용 Replica로 라우팅될 수 있다
 *
 * 데이터를 변경하는 메서드(create, update, delete)에는
 * @Transactional (readOnly 없음)을 개별 선언하여 이 기본 설정을 오버라이드한다.
 *
 * ── 생성자 주입 패턴 ──
 *
 * class UserService(
 *     private val userRepo: UserRepository,    // Spring이 자동 주입
 *     private val orderRepo: SalesOrderRepository  // Spring이 자동 주입
 * )
 *
 * Kotlin의 주 생성자(primary constructor)에 의존성을 선언하면
 * Spring이 자동으로 주입한다. Java의 @Autowired + 필드 주입보다 권장되는 패턴:
 * - 의존성이 final(val)로 선언되어 불변성이 보장된다
 * - 필수 의존성이 누락되면 컴파일 타임에 에러가 발생한다
 * - 테스트 시 생성자로 Mock 객체를 직접 주입할 수 있다
 */
@Service
@Transactional(readOnly = true)
class UserService(
    private val userRepo: UserRepository,         // users 테이블 접근
    private val orderRepo: SalesOrderRepository   // sales_orders 테이블 접근 (주문 이력 확인용)
) {

    // ════════════════════════════════════════════════════════════════
    //  조회 (Read) — readOnly = true 기본 설정 사용
    // ════════════════════════════════════════════════════════════════

    /**
     * 전체 사용자 목록 조회 (페이징)
     *
     * ── Pageable 파라미터 ──
     *
     * Controller에서 @PageableDefault(size = 20) pageable: Pageable로 받은 것이
     * 그대로 전달된다. Pageable 객체에는 다음 정보가 포함된다:
     * - page: 페이지 번호 (0부터 시작). 예: page=0 -> 첫 번째 페이지
     * - size: 페이지당 항목 수. 예: size=20 -> 한 페이지에 20개
     * - sort: 정렬 조건. 예: sort=username,asc -> 사용자명 오름차순
     *
     * ── userRepo.findAll(pageable) ──
     *
     * JpaRepository가 제공하는 기본 메서드.
     * 내부적으로 실행되는 SQL:
     *   SELECT * FROM users ORDER BY ... LIMIT ? OFFSET ?
     *   SELECT COUNT(*) FROM users  (전체 건수 조회 - 페이지 메타 정보용)
     *
     * 반환 타입: Page<User>
     *   - content: List<User> — 현재 페이지의 데이터
     *   - totalElements: Long — 전체 데이터 수
     *   - totalPages: Int — 전체 페이지 수
     *   - number: Int — 현재 페이지 번호
     *   - size: Int — 페이지 크기
     *
     * ── .map { UserResponse.from(it) } ──
     *
     * Page의 각 요소(User 엔티티)를 UserResponse DTO로 변환한다.
     * Page.map()은 데이터만 변환하고 페이지 메타 정보는 그대로 유지한다.
     *
     * 결과: Page<UserResponse> — 페이지 정보는 유지, 데이터만 DTO로 변환
     *
     * 왜 Entity를 직접 반환하지 않고 DTO로 변환하나?
     * 1. Entity에는 password 같은 민감한 필드가 있다 — DTO에는 제외
     * 2. Entity의 구조가 변해도 API 응답은 유지할 수 있다 (계층 분리)
     * 3. 순환 참조 방지 (Entity 간 양방향 관계가 있으면 JSON 직렬화 시 무한 루프)
     *
     * @param pageable 페이징 정보 (page, size, sort)
     * @return Page<UserResponse> — 페이징된 사용자 목록 (DTO)
     */
    fun getAllUsers(pageable: Pageable): Page<UserResponse> =
        userRepo.findAll(pageable)                 // Page<User> 조회
            .map { UserResponse.from(it) }         // Page<UserResponse>로 변환

    /**
     * 사용자 단건 조회 (ID로)
     *
     * findUserOrThrow(id):
     *   이 클래스의 internal 메서드. ID로 User 엔티티를 조회한다.
     *   없으면 EntityNotFoundException (-> 404 Not Found).
     *
     * UserResponse.from(user):
     *   User 엔티티를 UserResponse DTO로 변환하는 정적 팩토리 메서드.
     *   DTO의 companion object에 정의되어 있다.
     *
     * @param id 조회할 사용자의 PK (users 테이블의 id 컬럼)
     * @return UserResponse DTO
     * @throws EntityNotFoundException 해당 ID의 사용자가 없을 때 (404)
     */
    fun getUser(id: Int): UserResponse =
        UserResponse.from(findUserOrThrow(id))     // 조회 + DTO 변환

    /**
     * 사용자명으로 조회
     *
     * ── userRepo.findByUsername(username) ──
     *
     * Spring Data JPA의 "파생 쿼리(Derived Query)" 메서드.
     * 메서드 이름을 파싱하여 SQL을 자동 생성한다:
     *   findByUsername -> SELECT * FROM users WHERE username = ?
     *
     * 반환 타입: User? (Kotlin nullable)
     * - 사용자가 있으면: User 객체
     * - 사용자가 없으면: null
     *
     * ── ?: throw EntityNotFoundException(...) ──
     *
     * Kotlin의 Elvis 연산자: "왼쪽이 null이면 오른쪽을 실행"
     *
     * 동등한 Java 코드:
     *   User user = userRepo.findByUsername(username);
     *   if (user == null) {
     *       throw new EntityNotFoundException("사용자", username);
     *   }
     *   return UserResponse.from(user);
     *
     * Kotlin이 훨씬 간결하다!
     *
     * EntityNotFoundException("사용자", username):
     *   커스텀 예외. 생성자에 엔티티 종류와 식별값을 전달한다.
     *   GlobalExceptionHandler에서 404 Not Found로 변환된다.
     *   응답 예: {"status": 404, "message": "사용자를 찾을 수 없습니다: testuser"}
     *
     * @param username 조회할 사용자명
     * @return UserResponse DTO
     * @throws EntityNotFoundException 해당 사용자명이 없을 때 (404)
     */
    fun getUserByUsername(username: String): UserResponse {
        val user = userRepo.findByUsername(username)                // DB 조회 (User?)
            ?: throw EntityNotFoundException("사용자", username)     // null이면 404
        return UserResponse.from(user)                              // DTO 변환
    }

    // ════════════════════════════════════════════════════════════════
    //  생성 (Create) — @Transactional로 쓰기 트랜잭션
    // ════════════════════════════════════════════════════════════════

    /**
     * 사용자 생성 (관리자 기능)
     *
     * 주의: 이 메서드는 AuthService.register()와 다르다!
     * - register(): 비밀번호를 BCrypt로 해시하여 저장 (회원가입용)
     * - createUser(): 비밀번호를 그대로 저장 (관리자가 직접 생성 시)
     *   → 실제 프로덕션에서는 이 메서드에서도 BCrypt 해시를 적용해야 한다!
     *   → 이 예제에서는 서비스 간 역할 분리를 보여주기 위해 단순화했다.
     *
     * ── 비즈니스 검증: 중복 체크 ──
     *
     * DB의 UNIQUE 제약 조건에만 의존하지 않고, Service에서 먼저 검증하는 이유:
     * 1. DB 예외보다 의미 있는 에러 메시지를 반환할 수 있다
     *    - DB: "Duplicate entry 'john' for key 'UK_username'" (기술적)
     *    - Service: "이미 존재하는 사용자명입니다: john" (비즈니스적)
     * 2. 409 Conflict 같은 적절한 HTTP 상태 코드를 설정할 수 있다
     *    - DB 예외는 보통 500 Internal Server Error로 변환된다
     *
     * ── @Transactional (readOnly 없음) ──
     *
     * INSERT 쿼리를 실행해야 하므로 readOnly = true이면 안 된다.
     * 이 어노테이션이 클래스 레벨의 @Transactional(readOnly = true)를 오버라이드한다.
     *
     * @param req 사용자 생성 요청 DTO (username, email, password, role)
     * @return UserResponse DTO (생성된 사용자 정보)
     * @throws DuplicateException 사용자명 또는 이메일이 중복될 때 (409)
     */
    @Transactional
    fun createUser(req: CreateUserRequest): UserResponse {

        // ── 사용자명 중복 검사 ──
        //
        // userRepo.findByUsername(req.username):
        //   → SELECT * FROM users WHERE username = ?
        //   반환: User? (null이면 중복 없음)
        //
        // ?.let { ... }:
        //   null이 아니면 (= 이미 존재하면) 블록을 실행한다.
        //   블록 안에서 DuplicateException을 던진다.
        //
        //   let의 파라미터 it:
        //   Kotlin에서 단일 파라미터 람다의 기본 이름.
        //   여기서는 "이미 존재하는 User 엔티티"를 가리키지만 사용하지 않는다.
        //   (예외를 던지는 것이 목적이므로)
        userRepo.findByUsername(req.username)?.let {
            throw DuplicateException("사용자명", req.username)
        }

        // ── 이메일 중복 검사 ──
        // 사용자명과 동일한 패턴.
        // 이메일도 users 테이블에 UNIQUE 제약이 설정되어 있다.
        userRepo.findByEmail(req.email)?.let {
            throw DuplicateException("이메일", req.email)
        }

        // ── User 엔티티 생성 + DB 저장 ──
        //
        // User(...):
        //   Kotlin의 named argument로 생성자 호출.
        //   id는 @GeneratedValue(IDENTITY)이므로 생략 → DB가 auto_increment로 생성.
        //
        // userRepo.save(user):
        //   JpaRepository의 save() 메서드.
        //   새 엔티티(id = 0)이므로 EntityManager.persist()를 호출한다.
        //   → INSERT INTO users (username, email, password, role, ...) VALUES (?, ?, ?, ?, ...)
        //
        //   save()의 반환값:
        //   DB가 생성한 id가 설정된 엔티티를 반환한다.
        //   예: save(User(id=0, ...)) -> User(id=42, ...) (DB가 할당한 ID)
        //
        //   persist vs merge:
        //   - id가 0(기본값)이면: persist (INSERT)
        //   - id가 0이 아니면: merge (SELECT + INSERT or UPDATE)
        //   Spring Data JPA의 save()가 이 판단을 자동으로 한다.
        val user = userRepo.save(
            User(
                username = req.username,    // 사용자명 (로그인 ID)
                email = req.email,          // 이메일
                password = req.password,    // 비밀번호 (주의: 여기서는 해시 안 됨!)
                role = req.role             // 권한 (USER, MANAGER, ADMIN)
            )
        )

        // User 엔티티를 UserResponse DTO로 변환하여 반환.
        // UserResponse에는 password 필드가 없으므로 비밀번호가 노출되지 않는다.
        return UserResponse.from(user)
    }

    // ════════════════════════════════════════════════════════════════
    //  수정 (Update) — Partial Update + Dirty Checking
    // ════════════════════════════════════════════════════════════════

    /**
     * 사용자 수정 — Partial Update (부분 업데이트)
     *
     * ── Partial Update란? ──
     *
     * 클라이언트가 변경하고 싶은 필드만 JSON에 포함하면 된다.
     * 포함되지 않은 필드(= null)는 기존 값을 유지한다.
     *
     * 예시:
     *   PATCH /api/users/1
     *   Body: {"alias": "새별명"}
     *   → alias만 변경, email/role/isEnabled 등은 기존 값 유지
     *
     *   PATCH /api/users/1
     *   Body: {"email": "new@test.com", "role": "MANAGER"}
     *   → email과 role만 변경, 나머지 유지
     *
     * ── PATCH vs PUT ──
     *
     * HTTP 메서드의 의미:
     * - PUT: 전체 교체 — 모든 필드를 보내야 한다. 보내지 않은 필드는 null로 설정
     * - PATCH: 부분 수정 — 변경할 필드만 보내면 된다. REST API에서 권장
     *
     * ── Dirty Checking (변경 감지) ──
     *
     * JPA의 핵심 기능. 이 메서드에서 userRepo.save()를 호출하지 않아도
     * 변경 사항이 자동으로 DB에 반영된다!
     *
     * 동작 원리:
     * 1. @Transactional 메서드 시작
     * 2. findUserOrThrow(id) 호출 -> JPA가 User 엔티티를 로드하면서
     *    원본 상태를 "스냅샷"으로 메모리에 보관
     * 3. user.alias = "새별명" -> 엔티티의 필드 값 변경
     * 4. 메서드 종료 -> 트랜잭션 커밋
     * 5. JPA가 현재 상태와 스냅샷을 비교 -> 변경된 필드 발견
     * 6. 자동으로 UPDATE 쿼리 생성 및 실행:
     *    → UPDATE users SET alias = '새별명' WHERE id = 1
     *
     * ── 이메일 변경 시 중복 검사 ──
     *
     * 이메일은 UNIQUE 제약이 있으므로, 다른 사용자의 이메일과 중복되면 안 된다.
     * 단, 자기 자신의 현재 이메일은 "중복"으로 취급하지 않는다.
     * (이메일을 변경하지 않고 다른 필드만 변경하는 경우를 허용)
     *
     * @param id 수정할 사용자의 PK
     * @param req 수정 요청 DTO (null인 필드는 변경하지 않음)
     * @return UserResponse DTO (수정 후 사용자 정보)
     * @throws EntityNotFoundException 해당 ID의 사용자가 없을 때 (404)
     * @throws DuplicateException 이메일이 다른 사용자와 중복될 때 (409)
     */
    @Transactional
    fun updateUser(id: Int, req: UpdateUserRequest): UserResponse {

        // ── 1단계: 수정 대상 사용자 조회 ──
        // findUserOrThrow(id): User 엔티티 조회. 없으면 404.
        // 이 시점부터 user는 JPA의 "managed" 상태이다.
        // managed 상태의 엔티티는 dirty checking 대상이 된다.
        val user = findUserOrThrow(id)

        // ── 2단계: null이 아닌 필드만 반영 ──
        //
        // req.alias?.let { user.alias = it }:
        //   "req.alias가 null이 아니면, user.alias에 그 값을 대입한다"
        //
        //   ?.let 패턴의 분해:
        //   - req.alias?   : alias가 null인지 체크 (safe call)
        //   - .let { ... } : null이 아닌 경우에만 블록 실행
        //   - user.alias = it : it = req.alias의 값
        //
        //   동등한 Java 코드:
        //   if (req.getAlias() != null) {
        //       user.setAlias(req.getAlias());
        //   }
        //
        //   이렇게 하면 JSON에서 alias를 생략하면 기존 값이 유지되고,
        //   alias를 포함하면 새 값으로 변경된다.
        req.alias?.let { user.alias = it }

        // ── 이메일 변경 시 중복 검사 ──
        //
        // req.email?.let { newEmail -> ... }:
        //   email이 전송된 경우에만 실행.
        //   it 대신 newEmail이라는 이름을 부여했다 (가독성 향상).
        //
        // userRepo.findByEmail(newEmail)?.let { existing -> ... }:
        //   새 이메일로 DB를 검색한다.
        //   이미 존재하는 사용자가 있으면 existing에 바인딩된다.
        //
        // if (existing.id != id):
        //   "찾은 사용자가 나 자신이 아닌 경우"에만 중복 에러를 던진다.
        //   → 자기 이메일을 그대로 보내도 에러가 발생하지 않도록 하는 안전장치
        //
        //   예: 사용자 #1의 이메일이 "a@test.com"
        //     - {"email": "a@test.com"} → existing.id(1) == id(1) → 중복 아님 (OK)
        //     - {"email": "b@test.com"} → findByEmail이 null → 중복 없음 (OK)
        //     - {"email": "c@test.com"} → existing.id(3) != id(1) → 중복! (409 에러)
        req.email?.let { newEmail ->
            userRepo.findByEmail(newEmail)?.let { existing ->
                if (existing.id != id) throw DuplicateException("이메일", newEmail)
            }
            user.email = newEmail   // 중복이 없으면 이메일 변경
        }

        // role 변경: USER, MANAGER, ADMIN 중 하나
        req.role?.let { user.role = it }

        // 활성화 상태 변경: true(활성) / false(비활성)
        req.isEnabled?.let { user.isEnabled = it }

        // ── 3단계: DTO 변환 후 반환 ──
        //
        // 여기서 userRepo.save(user)를 호출하지 않아도 된다!
        // @Transactional 메서드가 종료되면 JPA가 dirty checking으로
        // 변경된 필드를 자동으로 UPDATE 쿼리로 실행한다.
        //
        // 실행되는 SQL 예시:
        // UPDATE users SET alias='새별명', email='new@test.com' WHERE id=1
        // (변경된 필드만 SET 절에 포함된다 - Hibernate의 @DynamicUpdate와 유사)
        return UserResponse.from(user)
    }

    // ════════════════════════════════════════════════════════════════
    //  삭제 (Delete) — 복합 Repository 검증
    // ════════════════════════════════════════════════════════════════

    /**
     * 사용자 삭제 — 주문 이력 검증 포함
     *
     * ── 비즈니스 규칙 ──
     *
     * "주문 이력이 있는 사용자는 삭제할 수 없다"
     *
     * 왜 이런 규칙이 필요한가?
     * - 주문(sales_orders) 테이블에 user_id 외래 키가 있다
     * - 사용자를 삭제하면 주문 기록의 user_id가 참조하는 대상이 사라진다
     * - 주문 이력은 비즈니스적으로 중요한 데이터이므로 보존해야 한다
     *   (세금 신고, 감사, 고객 문의 대응 등)
     *
     * 대안:
     * - 물리적 삭제(DELETE) 대신 논리적 삭제(isEnabled = false)를 권장
     * - GDPR 등 개인정보 삭제 요청이 있을 때는 개인 정보만 마스킹 처리
     *
     * ── 이것이 "복합 Repository 검증"의 대표적인 사례 ──
     *
     * UserRepo: 삭제 대상 사용자가 존재하는지 확인
     * OrderRepo: 해당 사용자에게 주문 이력이 있는지 확인
     * → 두 Repository의 결과를 조합하여 삭제 가능 여부를 판단한다
     *
     * 이런 "여러 테이블을 참조하는 비즈니스 검증"이
     * Service 계층이 존재하는 핵심 이유이다.
     *
     * @param id 삭제할 사용자의 PK
     * @throws EntityNotFoundException 해당 ID의 사용자가 없을 때 (404)
     * @throws BusinessException 주문 이력이 있어 삭제할 수 없을 때 (400)
     */
    @Transactional
    fun deleteUser(id: Int) {

        // ── 1단계: 사용자 존재 확인 ──
        // 없으면 EntityNotFoundException (404) 발생.
        // 존재하지 않는 사용자를 삭제하려는 시도를 방지.
        findUserOrThrow(id)

        // ── 2단계: 주문 이력 확인 ──
        //
        // orderRepo.countByUserId(id):
        //   SalesOrderRepository의 파생 쿼리 메서드.
        //   → SELECT COUNT(*) FROM sales_orders WHERE user_id = ?
        //   반환: Long (주문 건수)
        //
        // 여기서 SalesOrderRepository를 사용하는 것이
        // "복합 Repository 활용"의 핵심이다.
        // UserService가 OrderRepository를 사용하여
        // 데이터 무결성을 보호하는 크로스-도메인 검증을 수행한다.
        val orderCount = orderRepo.countByUserId(id)

        // ── 3단계: 주문이 있으면 삭제 차단 ──
        //
        // BusinessException:
        //   비즈니스 규칙 위반을 나타내는 커스텀 예외.
        //   GlobalExceptionHandler에서 400 Bad Request로 변환된다.
        //
        // 문자열 템플릿 "${orderCount}":
        //   Kotlin의 문자열 보간(String Interpolation).
        //   변수 값을 문자열에 직접 삽입한다.
        //   예: "주문 이력이 3건 있어 삭제할 수 없습니다. 비활성화를 사용하세요."
        if (orderCount > 0) {
            throw BusinessException(
                "주문 이력이 ${orderCount}건 있어 삭제할 수 없습니다. 비활성화를 사용하세요."
            )
        }

        // ── 4단계: 사용자 삭제 ──
        //
        // userRepo.deleteById(id):
        //   JpaRepository의 기본 메서드.
        //   → DELETE FROM users WHERE id = ?
        //
        //   이 메서드는 내부적으로 먼저 SELECT를 실행하여 엔티티를 조회한 후,
        //   EntityManager.remove()를 호출한다.
        //   조회된 엔티티가 없으면 EmptyResultDataAccessException이 발생하지만,
        //   위에서 findUserOrThrow()로 이미 존재 여부를 확인했으므로 안전하다.
        userRepo.deleteById(id)
    }

    // ════════════════════════════════════════════════════════════════
    //  복합 조회 — UserRepo + OrderRepo 조합
    // ════════════════════════════════════════════════════════════════

    /**
     * 사용자 프로필 + 주문 통계 조회
     *
     * 2개의 Repository를 조합하여 하나의 복합 응답을 만드는 Service 계층의 핵심 역할.
     *
     * ── 실행되는 SQL (총 4개) ──
     *
     * 1. SELECT * FROM users WHERE id = ?           (사용자 기본 정보)
     * 2. SELECT COUNT(*) FROM sales_orders WHERE user_id = ?  (주문 건수)
     * 3. SELECT SUM(total_amount) FROM sales_orders WHERE user_id = ?  (총 지출액)
     * 4. SELECT AVG(total_amount) FROM sales_orders WHERE user_id = ?  (평균 주문액)
     *
     * 이 4개의 SQL이 하나의 @Transactional 안에서 실행된다.
     * readOnly = true이므로 DB 커넥션이 읽기 전용으로 최적화된다.
     *
     * ── ?: 0.0 (Elvis 연산자) ──
     *
     * SUM()과 AVG()는 데이터가 없으면(주문이 0건이면) null을 반환한다.
     * Kotlin의 Elvis 연산자로 null을 0.0으로 대체한다.
     *
     * 예:
     * - 주문 3건 → sum=150000.0, avg=50000.0
     * - 주문 0건 → sum=null → 0.0, avg=null → 0.0
     *
     * @param id 조회할 사용자의 PK
     * @return UserProfileResponse (사용자 정보 + 주문 건수 + 총액 + 평균)
     * @throws EntityNotFoundException 해당 ID의 사용자가 없을 때 (404)
     */
    fun getUserProfile(id: Int): UserProfileResponse {

        // 사용자 엔티티 조회 (없으면 404)
        val user = findUserOrThrow(id)

        // UserProfileResponse DTO 생성:
        // User 기본 정보(UserResponse)와 주문 통계(count, sum, avg)를 결합한다.
        return UserProfileResponse(
            user = UserResponse.from(user),                           // 사용자 기본 정보 DTO
            orderCount = orderRepo.countByUserId(id),                 // 주문 건수 (Long)
            totalSpent = orderRepo.sumTotalAmountByUserId(id) ?: 0.0, // 총 지출액 (null -> 0.0)
            avgOrderAmount = orderRepo.avgTotalAmountByUserId(id) ?: 0.0 // 평균 주문액 (null -> 0.0)
        )
    }

    // ════════════════════════════════════════════════════════════════
    //  내부 헬퍼 메서드
    // ════════════════════════════════════════════════════════════════

    /**
     * 사용자 엔티티 조회 — 없으면 404 예외
     *
     * ── internal 접근 제한자 ──
     *
     * 이 메서드는 같은 모듈(같은 Gradle 프로젝트) 내에서만 접근 가능하다.
     *
     * 사용 위치:
     * 1. UserService 내부 — getUser(), updateUser(), deleteUser(), getUserProfile()
     * 2. OrderService — createOrder()에서 주문자 검증
     * 3. AuthService — (직접 UserRepository를 사용하므로 이 메서드는 사용하지 않음)
     *
     * 이 패턴의 장점:
     * - "사용자 존재 검증 + 404 예외 처리" 로직이 한 곳에 집중된다
     * - OrderService에서 같은 검증 로직을 중복 작성하지 않아도 된다 (DRY 원칙)
     * - private이 아니므로 다른 Service에서 재사용 가능
     * - public이 아니므로 Controller에서 직접 호출하는 것은 방지
     *
     * ── userRepo.findById(id) ──
     *
     * JpaRepository의 기본 메서드.
     * → SELECT * FROM users WHERE id = ?
     *
     * 반환 타입: Optional<User> (Java의 Optional)
     * - 데이터가 있으면: Optional.of(user)
     * - 데이터가 없으면: Optional.empty()
     *
     * ── .orElseThrow { ... } ──
     *
     * Optional에 값이 없으면(empty) 람다의 예외를 던진다.
     * Optional에 값이 있으면 그 값(User 엔티티)을 반환한다.
     *
     * EntityNotFoundException("사용자", id):
     *   커스텀 예외. GlobalExceptionHandler에서 404로 변환.
     *   응답: {"status": 404, "message": "사용자를 찾을 수 없습니다: 42"}
     *
     * @param id 조회할 사용자의 PK
     * @return User 엔티티 (JPA managed 상태)
     * @throws EntityNotFoundException 해당 ID의 사용자가 없을 때 (404)
     */
    internal fun findUserOrThrow(id: Int): User =
        userRepo.findById(id)                                  // Optional<User> 반환
            .orElseThrow { EntityNotFoundException("사용자", id) } // 없으면 404 예외
}
