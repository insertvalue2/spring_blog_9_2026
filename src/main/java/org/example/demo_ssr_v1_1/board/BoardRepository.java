package org.example.demo_ssr_v1_1.board;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 게시글 Repository 인터페이스
 * 
 * 핵심 개념:
 * 1. JpaRepository<Entity, ID타입>: Spring Data JPA가 제공하는 인터페이스
 *    - Entity: 엔티티 클래스 (Board)
 *    - ID타입: 엔티티의 기본키 타입 (Long)
 * 
 * 2. 자동 제공 메서드 (별도 구현 없이 사용 가능):
 *    - save(T entity): 엔티티 저장 (INSERT 또는 UPDATE)
 *    - findById(ID id): ID로 엔티티 조회 (Optional<T> 반환)
 *    - findAll(): 모든 엔티티 조회
 *    - deleteById(ID id): ID로 엔티티 삭제
 *    - count(): 전체 개수 조회
 *    - existsById(ID id): ID 존재 여부 확인
 * 
 * 3. 쿼리 메서드 (Query Methods):
 *    - 메서드 이름만으로 쿼리를 자동 생성
 *    - Spring Data JPA가 메서드 이름을 분석하여 SQL 쿼리 생성
 *    - 예: findAllByOrderByCreatedAtDesc() 
 *      -> "SELECT * FROM board_tb ORDER BY created_at DESC"
 * 
 * 4. @Repository: Spring이 이 인터페이스를 스캔하여 프록시 객체를 자동 생성
 *    - 구현체는 Spring Data JPA가 런타임에 자동 생성
 *    - @Repository 생략 가능 (JpaRepository 상속 시 자동 인식)
 *    - 하지만 명시적으로 작성하는 것이 좋음 (의도 명확화)
 * 
 * 5. Repository 네이밍 규칙:
 *    - 일반적으로 "Entity명 + Repository" 형태
 *    - 예: BoardRepository, UserRepository
 *    - BoardPersistRepository → BoardRepository로 변경 (표준 네이밍)
 */
@Repository
public interface BoardRepository extends JpaRepository<Board, Long> {
    
    /**
     * 게시글 전체 조회 (생성일 기준 내림차순 정렬)
     * 
     * 쿼리 메서드 네이밍 규칙:
     * - findAll: 전체 조회
     * - By: 조건 시작 (여기서는 조건 없음)
     * - OrderBy: 정렬 시작
     * - CreatedAt: 정렬할 필드명 (엔티티의 필드명과 일치해야 함)
     * - Desc: 내림차순 (Asc는 오름차순)
     * 
     * 자동 생성되는 SQL:
     * SELECT * FROM board_tb ORDER BY created_at DESC
     * 
     * 사용 예시:
     * List<Board> boards = boardRepository.findAllByOrderByCreatedAtDesc();
     */
    List<Board> findAllByOrderByCreatedAtDesc();
    
    /**
     * 게시글 전체 조회 (작성자 정보 포함, JOIN FETCH 사용)
     * 
     * JOIN FETCH란?
     * - 연관된 엔티티를 한 번의 쿼리로 함께 조회하는 JPA 기능
     * - N+1 문제를 해결하고 성능을 최적화
     * 
     * 동작 방식:
     * - Board와 User를 한 번의 쿼리로 함께 조회
     * - LAZY 로딩이 설정되어 있어도 즉시 로딩됨
     * - OSIV false 환경에서도 안전하게 사용 가능
     * 
     * 생성되는 SQL:
     * SELECT b.*, u.* 
     * FROM board_tb b 
     * INNER JOIN user_tb u ON b.user_id = u.id 
     * ORDER BY b.created_at DESC
     * 
     * @return 작성자 정보가 포함된 게시글 목록 (생성일 기준 내림차순)
     */
    @Query("SELECT b FROM Board b JOIN FETCH b.user ORDER BY b.createdAt DESC")
    List<Board> findAllWithUserOrderByCreatedAtDesc();
    
    /**
     * 게시글 ID로 조회 (작성자 정보 포함, JOIN FETCH 사용)
     * 
     * JOIN FETCH를 사용하여 Board와 User를 한 번의 쿼리로 함께 조회합니다.
     * 
     * 생성되는 SQL:
     * SELECT b.*, u.* 
     * FROM board_tb b 
     * INNER JOIN user_tb u ON b.user_id = u.id 
     * WHERE b.id = ?
     * 
     * @param id 게시글 ID
     * @return 작성자 정보가 포함된 게시글 (Optional)
     */
    @Query("SELECT b FROM Board b JOIN FETCH b.user WHERE b.id = :id")
    Optional<Board> findByIdWithUser(@Param("id") Long id);
    
    /**
     * 게시글 페이징 조회 (작성자 정보 포함, JOIN FETCH 사용)
     *
     * [@Query 의 value 와 countQuery — 왜 쿼리가 두 개인가?]
     * 페이징을 하려면 사실 SQL 두 번이 필요하다.
     *   1) value     : 현재 페이지의 실제 데이터를 가져오는 메인 쿼리 (LIMIT/OFFSET 자동 적용)
     *   2) countQuery: 전체가 총 몇 건인지 세는 카운트 쿼리
     *                  → Page<T> 의 totalElements / totalPages 계산에 사용됨
     *
     * countQuery 를 적지 않으면 Spring Data 가 메인 쿼리에서 SELECT 절을 떼어내고
     * COUNT(*) 로 바꿔 자동 생성을 시도한다. 하지만 JOIN FETCH 같은 복잡한 쿼리에서는
     * 자동 변환이 실패하거나 비효율적이다. 그래서 명시적으로 적어 준다.
     *
     * 비유: 책장에서 책을 꺼낼 때, "이번에 보여줄 책 5권을 꺼내는 일" 과 "이 책장에 총
     * 몇 권이 꽂혀 있는지 세는 일" 은 별개의 작업이다. 두 번째 작업에는 굳이 책 표지를
     * 펴서 작가 정보까지 볼 필요가 없다. 그래서 countQuery 에는 JOIN FETCH 를 안 넣는다.
     *
     * [DISTINCT 가 필요한 이유]
     * JOIN FETCH 는 결과 행 수를 부풀릴 수 있다(특히 @OneToMany). 안전하게 중복을 제거하기
     * 위해 메인 쿼리와 카운트 쿼리 모두 DISTINCT 를 쓴다.
     *
     * 생성되는 SQL:
     *   메인  : SELECT DISTINCT b.*, u.*
     *           FROM board_tb b
     *           INNER JOIN user_tb u ON b.user_id = u.id
     *           ORDER BY b.created_at DESC
     *           LIMIT ? OFFSET ?
     *   카운트: SELECT COUNT(DISTINCT b.id) FROM board_tb b
     *
     * @param pageable 페이징 정보 (페이지 번호, 페이지 크기, 정렬)
     * @return 페이징된 게시글 목록 (작성자 정보 포함)
     */
    @Query(value      = "SELECT DISTINCT b FROM Board b JOIN FETCH b.user ORDER BY b.createdAt DESC",
           countQuery = "SELECT COUNT(DISTINCT b) FROM Board b")
    Page<Board> findAllWithUserOrderByCreatedAtDesc(Pageable pageable);
    
    /**
     * JpaRepository에서 자동 제공되는 메서드들:
     * 
     * 1. save(Board board): 
     *    - ID가 null이면 INSERT, 있으면 UPDATE (더티 체킹)
     *    - 트랜잭션이 끝나면 자동으로 DB에 반영
     *    - 사용 예: boardRepository.save(board);
     * 
     * 2. Optional<Board> findById(Long id):
     *    - ID로 엔티티 조회
     *    - Optional로 반환하여 null 안전성 보장
     *    - 사용 예: boardRepository.findById(id).orElseThrow(...)
     * 
     * 3. void deleteById(Long id):
     *    - ID로 엔티티 삭제
     *    - 엔티티가 없으면 예외 발생 (EmptyResultDataAccessException)
     *    - 사용 예: boardRepository.deleteById(id);
     * 
     * 4. List<Board> findAll():
     *    - 모든 엔티티 조회 (정렬 없음)
     *    - 사용 예: List<Board> boards = boardRepository.findAll();
     * 
     * 5. long count():
     *    - 전체 엔티티 개수 조회
     *    - 사용 예: long count = boardRepository.count();
     * 
     * 6. boolean existsById(Long id):
     *    - ID 존재 여부 확인
     *    - 사용 예: boolean exists = boardRepository.existsById(id);
     * 
     * 더티 체킹 (Dirty Checking):
     * - JPA는 영속성 컨텍스트(Persistence Context)에 있는 엔티티의 변경을 자동 감지
     * - 트랜잭션이 끝날 때 변경된 필드만 자동으로 UPDATE 쿼리 실행
     * - 개발자가 직접 UPDATE 쿼리를 작성할 필요 없음
     * 
     * 예시:
     * @Transactional
     * public void updateBoard(Long id) {
     *     Board board = boardRepository.findById(id).orElseThrow(...);
     *     board.setTitle("새 제목"); // 필드 값 변경
     *     // 트랜잭션 종료 시 자동으로 UPDATE 쿼리 실행 (더티 체킹)
     * }
     */
}

