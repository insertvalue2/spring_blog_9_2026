package org.example.demo_ssr_v1_1.board;

import lombok.RequiredArgsConstructor;
import org.example.demo_ssr_v1_1._core.errors.exception.Exception403;
import org.example.demo_ssr_v1_1._core.errors.exception.Exception404;
import org.example.demo_ssr_v1_1._core.errors.exception.Exception500;
import org.example.demo_ssr_v1_1.reply.ReplyRepository;
import org.example.demo_ssr_v1_1.user.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 서비스 레이어 (Service Layer)
 * 
 * 핵심 개념:
 * 1. 서비스 레이어의 역할:
 *    - 비즈니스 로직을 처리하는 계층
 *    - Controller와 Repository 사이의 중간 계층
 *    - 트랜잭션 관리 (@Transactional)
 *    - 여러 Repository를 조합하여 복잡한 비즈니스 로직 처리
 * 
 * 2. 계층 구조 (3-Tier Architecture):
 *    Controller (표현 계층) 
 *      ↓ 요청
 *    Service (비즈니스 계층) ← 현재 위치
 *      ↓ 요청
 *    Repository (데이터 접근 계층)
 *      ↓
 *    Database
 * 
 * 3. @Service:
 *    - Spring이 이 클래스를 서비스 빈으로 등록
 *    - @Component의 특수한 형태
 *    - 비즈니스 로직을 담당하는 클래스임을 명시
 * 
 * 4. @RequiredArgsConstructor:
 *    - final 필드에 대한 생성자를 자동 생성
 *    - 의존성 주입(DI)을 위한 생성자 주입 방식
 *    - @Autowired 대신 생성자 주입을 사용 (권장 방식)
 * 
 * 5. @Transactional:
 *    - 메서드 실행 시 트랜잭션을 시작하고 종료 시 커밋
 *    - 예외 발생 시 자동 롤백
 *    - 더티 체킹(Dirty Checking) 활성화
 *    - 여러 DB 작업을 하나의 트랜잭션으로 묶어 일관성 보장
 */
@Service
@RequiredArgsConstructor
public class BoardService {

    private final BoardRepository boardRepository;
    private final ReplyRepository replyRepository;

    /**
     * 게시글 목록 조회 (페이징)
     *
     * 페이지 번호 규칙:
     * - 호출자(Controller)는 항상 1-base 페이지 번호를 넘긴다. (1, 2, 3 ...)
     * - Spring Data JPA의 PageRequest는 0-base이므로 이 메서드 안에서 -1 변환.
     * - 0-base 변환은 이 한 곳에서만 일어나므로, 0/1 base 혼동이 코드 전체로 퍼지지 않는다.
     *
     * 방어 로직:
     * - page < 1 이면 1로 보정 (음수, 0 방어)
     * - size 는 1 ~ 50 으로 제한 (size=10000 같은 악성/오타 요청 방어)
     *
     * @param page 페이지 번호 (1-base)
     * @param size 페이지 크기
     * @return 페이징 정보 + 게시글 목록 DTO
     */
    @Transactional(readOnly = true)
    public BoardResponse.PageDTO 게시글목록조회(int page, int size) {
        // 화면 기준으로 넘어오는 값 (사용자에게 보이는 기준): 0이 아니라 1부터 시작.
        // 반면 Spring Data의 PageRequest는 0부터 시작 — 내부적으로 OFFSET = pageIndex * size 로
        // SQL을 만드는데, DB의 OFFSET 자체가 0부터이기 때문이다.
        // 그래서 여기서 -1 해서 0-base로 맞춰준다.
        // Math.max(0, ...)는 사용자가 스크립트로 page=-5 같은 음수를 보내도
        // 음수 인덱스로 안 떨어지게 막는 방어 코드(마이너스 입력 방어).
        int pageIndex = Math.max(0, page - 1);

        // size 안전 범위 보정. "기본값 5"는 이 줄이 아니라
        // Controller의 @RequestParam(defaultValue = "5") 가 정해 준다.
        // 이 줄은 단지 "1보다 작거나 50보다 큰 값이 들어와도 안전 범위로 잘라낸다" 가 전부.
        // 예) ?size=0     → 1 로 보정
        //     ?size=10000 → 50 으로 보정
        int validSize = Math.max(1, Math.min(50, size));

        // 정렬: 생성일 내림차순 (최신순)
        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt");

        // [Pageable 이란?]
        // "어떤 페이지를(pageIndex), 몇 개씩(validSize), 어떤 정렬로(sort) 가져올지"
        // 를 한 묶음으로 표현한 Spring Data 의 표준 페이징 요청 인터페이스.
        // Repository 메서드에 인자로 넘기면 Spring Data 가 LIMIT + OFFSET SQL 을 자동 생성해 준다.
        // → 우리가 페이징 SQL 을 손으로 쓸 일이 없어진다.
        Pageable pageable = PageRequest.of(pageIndex, validSize, sort);

        // [Page<T> 란?]
        // "조회된 데이터 한 페이지" 와 "페이징 메타데이터" 를 한꺼번에 담아주는 결과 컨테이너.
        //   - getContent()        : 현재 페이지의 데이터 목록
        //   - getNumber()         : 현재 페이지 번호 (0-base)
        //   - getTotalElements()  : 전체 항목 수
        //   - getTotalPages()     : 전체 페이지 수
        //   - isFirst() / isLast(): 첫/마지막 페이지 여부
        // Spring Data 가 LIMIT 쿼리 + count(*) 쿼리를 함께 실행해서 위 값들을 한 번에 채워준다.
        // 즉, 우리가 따로 count 쿼리를 날리거나 Math.ceil 로 전체 페이지 수를 계산할 필요가 없다.
        //
        // 또한 이 메서드는 JOIN FETCH 로 Board + User 를 한 번에 조회한다.
        // (N+1 방지 + OSIV false 환경 대응)
        Page<Board> boardPage = boardRepository.findAllWithUserOrderByCreatedAtDesc(pageable);

        // [왜 Page<Board> 를 그대로 안 쓰고 PageDTO 로 한 번 더 감싸나?]
        // Page<Board> 를 모델에 그대로 담아 화면에 넘겨도 동작 자체는 한다.
        // 그런데도 한 겹 감싸는 이유:
        //  1) Board 는 DB 용 엔티티. 화면 모델과 분리해야 password 같은 민감 필드가 새지 않는다.
        //  2) createdAt(Timestamp) 을 보기 좋은 문자열로 포맷팅하는 일이 ListDTO 안에 있다.
        //  3) Mustache 는 산술/비교 연산을 못 해서 prevPage, nextPage, active 같은 값을
        //     자바 쪽에서 미리 계산해 줘야 한다 — 그 계산 결과를 담을 그릇이 PageDTO 다.
        //  4) OSIV 가 false 라 트랜잭션이 끝나면 LAZY 필드 접근이 막힌다. DTO 패턴이면
        //     화면이 엔티티를 직접 만지지 않으니 미래에 LAZY 필드가 늘어나도 사고가 적다.
        return new BoardResponse.PageDTO(boardPage);
    }

    /**
     * 게시글 목록 조회 (전체 조회, 페이징 없음)
     * 
     * 기존 메서드 유지 (하위 호환성)
     * 
     * @return 게시글 목록 DTO (생성일 기준 내림차순)
     */
    @Transactional(readOnly = true)
    public List<BoardResponse.ListDTO> 게시글목록조회전체() {
        // JOIN FETCH로 Board와 User를 한 번의 쿼리로 함께 조회
        // N+1 문제 해결 및 OSIV False 환경 대응
        List<Board> boardList = boardRepository.findAllWithUserOrderByCreatedAtDesc();
        
        // 트랜잭션 내에서 엔티티를 DTO로 변환
        // Stream을 사용하여 각 Board 엔티티를 ListDTO로 변환
        
        // [방법 1] 메서드 참조 사용 (현재 사용 중인 방법)
        // BoardResponse.ListDTO::new 는 생성자 메서드 참조
        // 각 Board 객체를 ListDTO 생성자에 전달하여 변환
        return boardList.stream()
                .map(BoardResponse.ListDTO::new)  // 메서드 참조: board -> new BoardResponse.ListDTO(board) 와 동일
                .collect(Collectors.toList());
        
        // [방법 2] 람다 표현식 사용
        // 메서드 참조 대신 람다 표현식으로 명시적으로 작성
        // return boardList.stream()
        //         .map(board -> new BoardResponse.ListDTO(board))  // 람다 표현식
        //         .collect(Collectors.toList());
        
        // [방법 3] for문 사용 (전통적인 방법)
        // Stream을 사용하지 않고 for문으로 직접 변환
        // List<BoardResponse.ListDTO> dtoList = new ArrayList<>();
        // for (Board board : boardList) {
        //     BoardResponse.ListDTO dto = new BoardResponse.ListDTO(board);
        //     dtoList.add(dto);
        // }
        // return dtoList;
    }

    /**
     * 게시글 상세 조회
     * 
     * OSIV False 환경 대응:
     * - 트랜잭션 내에서 필요한 데이터를 모두 조회하고 DTO로 변환
     * - JOIN FETCH로 Board와 User를 한 번의 쿼리로 함께 조회
     * - 엔티티를 DTO로 변환하여 반환 (LAZY 로딩 문제 방지)
     * 
     * Optional 처리:
     * - findByIdWithUser()는 Optional<Board>를 반환
     * - orElseThrow(): 값이 없으면 예외 발생, 있으면 Board 반환
     * 
     * @param id 게시글 ID
     * @return 게시글 상세 DTO
     * @throws Exception404 게시글이 없을 경우
     */
    @Transactional(readOnly = true)
    public BoardResponse.DetailDTO 게시글상세조회(Long id) {
        // JOIN FETCH로 Board와 User를 한 번의 쿼리로 함께 조회
        // N+1 문제 해결 및 OSIV False 환경 대응
        Board board = boardRepository.findByIdWithUser(id)
                .orElseThrow(() -> new Exception404("게시글을 찾을 수 없어요 : "));
        
        // 트랜잭션 내에서 엔티티를 DTO로 변환
        return new BoardResponse.DetailDTO(board);
    }

    /**
     * 게시글 작성
     * 
     * 트랜잭션:
     * - 기본 트랜잭션 (읽기/쓰기)
     * - save() 메서드 실행 시 INSERT 쿼리 실행
     * 
     * @param saveDTO 게시글 작성 DTO
     * @param user 작성자 (세션에서 가져온 사용자)
     * @return 저장된 게시글 엔티티
     */
    @Transactional
    public Board 게시글작성(BoardRequest.SaveDTO saveDTO, User user) {
        // DTO를 엔티티로 변환
        Board board = saveDTO.toEntity(user);
        
        // JpaRepository의 save() 메서드
        // - ID가 null이면 INSERT
        // - ID가 있으면 UPDATE
        return boardRepository.save(board);
    }

    /**
     * 게시글 수정 화면용 조회 (인가 검사 포함)
     * 
     * OSIV False 환경 대응:
     * - 트랜잭션 내에서 필요한 데이터를 모두 조회하고 DTO로 변환
     * - JOIN FETCH로 Board와 User를 한 번의 쿼리로 함께 조회
     * - 엔티티를 DTO로 변환하여 반환 (LAZY 로딩 문제 방지)
     * 
     * 인가 검사:
     * - 게시글 소유자만 수정 가능
     * - isOwner() 메서드로 소유자 확인
     * 
     * @param id 게시글 ID
     * @param userId 현재 로그인한 사용자 ID
     * @return 게시글 수정 화면 DTO
     * @throws Exception404 게시글이 없을 경우
     * @throws Exception403 수정 권한이 없을 경우
     */
    @Transactional(readOnly = true)
    public BoardResponse.UpdateFormDTO 게시글수정화면(Long id, Long userId) {
        // JOIN FETCH로 Board와 User를 한 번의 쿼리로 함께 조회
        Board board = boardRepository.findByIdWithUser(id)
                .orElseThrow(() -> new Exception404("게시글을 찾을 수 없습니다"));

        // 인가 검사: 게시글 소유자인지 확인
        if (!board.isOwner(userId)) {
            throw new Exception403("게시글 수정 권한 없음");
        }

        // 트랜잭션 내에서 엔티티를 DTO로 변환
        return new BoardResponse.UpdateFormDTO(board);
    }

    /**
     * 게시글 수정 처리
     * 
     * 더티 체킹 (Dirty Checking):
     * - 엔티티를 조회한 후 필드 값을 변경
     * - 트랜잭션이 끝날 때 자동으로 UPDATE 쿼리 실행
     * - save()를 호출해도 되지만, @Transactional이 있으면 자동으로 UPDATE 됨
     * 
     * @param id 게시글 ID
     * @param updateDTO 게시글 수정 DTO
     * @param userId 현재 로그인한 사용자 ID
     * @throws Exception404 게시글이 없을 경우
     * @throws Exception403 수정 권한이 없을 경우
     * @throws Exception500 수정 실패 시
     */
    @Transactional
    public void 게시글수정(Long id, BoardRequest.UpdateDTO updateDTO, Long userId) {
        // 게시글 조회
        Board board = boardRepository.findById(id)
                .orElseThrow(() -> new Exception404("게시글을 찾을 수 없습니다"));

        // 인가 검사: 게시글 소유자인지 확인
        if (!board.isOwner(userId)) {
            throw new Exception403("게시글 수정 권한이 없습니다");
        }

        // 더티 체킹을 활용한 수정 처리
        try {
            // 엔티티의 상태 값 변경
            board.update(updateDTO);
            
            // save() 호출: 변경된 엔티티 저장
            // 참고: @Transactional이 있으면 save() 없이도 자동으로 UPDATE 됨
            // 하지만 명시적으로 save()를 호출하는 것이 더 명확함
            boardRepository.save(board);
        } catch (Exception e) {
            throw new Exception500("게시글 수정 실패: " + e.getMessage());
        }
    }

    /**
     * 게시글 삭제
     * 
     * 삭제 처리:
     * - 게시글에 댓글이 있으면 먼저 댓글을 삭제해야 함 (외래키 제약조건)
     * - deleteById() 메서드 사용
     * - 엔티티가 없으면 EmptyResultDataAccessException 발생
     * 
     * @param id 게시글 ID
     * @param userId 현재 로그인한 사용자 ID
     * @throws Exception404 게시글이 없을 경우
     * @throws Exception403 삭제 권한이 없을 경우
     */
    @Transactional
    public void 게시글삭제(Long id, Long userId) {
        // 게시글 조회
        Board board = boardRepository.findById(id)
                .orElseThrow(() -> new Exception404("게시글을 찾을 수 없습니다"));

        // 인가 검사: 게시글 소유자인지 확인
        if (!board.isOwner(userId)) {
            throw new Exception403("삭제 권한이 없습니다");
        }

        // 게시글에 댓글이 있으면 먼저 댓글 삭제 (외래키 제약조건 해결)
        // 게시글 삭제 전에 해당 게시글의 모든 댓글을 먼저 삭제
        replyRepository.deleteByBoardId(id);
        
        // JpaRepository의 deleteById() 메서드
        // 엔티티가 없으면 EmptyResultDataAccessException 발생
        boardRepository.deleteById(id);
    }
}

