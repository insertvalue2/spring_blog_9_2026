package org.example.demo_ssr_v1_1.board;

import lombok.Data;
import org.example.demo_ssr_v1_1.utils.MyDateUtil;
import org.springframework.data.domain.Page;

import java.util.ArrayList;
import java.util.List;

/**
 * 게시글 응답 DTO
 *
 * Open Session in View가 false일 때:
 * - 트랜잭션이 끝나면 세션이 종료되어 LAZY 로딩 불가
 * - Service에서 필요한 데이터를 모두 조회하고 DTO로 변환하여 반환
 * - 엔티티를 직접 반환하지 않고 DTO를 반환하여 계층 간 결합도 감소
 */
public class BoardResponse {

    /**
     * 게시글 목록 응답 DTO
     */
    @Data
    public static class ListDTO {
        private Long id;
        private String title;
        private String username;  // 작성자명
        private String createdAt; // 포맷된 생성일

        public ListDTO(Board board) {
            this.id = board.getId();
            this.title = board.getTitle();
            // JOIN FETCH로 이미 로딩된 user 사용 (추가 쿼리 없음)
            if (board.getUser() != null) {
                this.username = board.getUser().getUsername();
            }
            // 날짜 포맷팅
            if (board.getCreatedAt() != null) {
                this.createdAt = MyDateUtil.timestampFormat(board.getCreatedAt());
            }
        }
    }

    /**
     * 게시글 상세 응답 DTO
     */
    @Data
    public static class DetailDTO {
        private Long id;
        private String title;
        private String content;
        private Long userId;      // 작성자 ID
        private String username;  // 작성자명
        private String createdAt; // 포맷된 생성일

        public DetailDTO(Board board) {
            this.id = board.getId();
            this.title = board.getTitle();
            this.content = board.getContent();
            // JOIN FETCH로 이미 로딩된 user 사용 (추가 쿼리 없음)
            if (board.getUser() != null) {
                this.userId = board.getUser().getId();
                this.username = board.getUser().getUsername();
            }
            // 날짜 포맷팅
            if (board.getCreatedAt() != null) {
                this.createdAt = MyDateUtil.timestampFormat(board.getCreatedAt());
            }
        }
    }

    /**
     * 게시글 수정 화면 응답 DTO
     */
    @Data
    public static class UpdateFormDTO {
        private Long id;
        private String title;
        private String content;
        private String username;  // 작성자명 (평탄화)

        public UpdateFormDTO(Board board) {
            this.id = board.getId();
            this.title = board.getTitle();
            this.content = board.getContent();
            // JOIN FETCH로 이미 로딩된 user 사용 (추가 쿼리 없음)
            if (board.getUser() != null) {
                this.username = board.getUser().getUsername();
            }
        }
    }

    /**
     * 페이징 정보 DTO (단순화 버전)
     *
     * 설계 원칙:
     * 1. 뷰(템플릿)는 항상 1부터 시작하는 페이지 번호를 본다
     *    - Spring Data JPA의 Page.getNumber()는 0부터 시작하므로
     *      DTO 생성 시점에 +1 해서 1-base로 정규화한다.
     *
     * 2. 템플릿(Mustache)은 산술/비교 연산을 못한다
     *    - 이전/다음 페이지 번호와 표시할 페이지 번호 목록은
     *      DTO에서 미리 계산해 넘긴다.
     *    - "현재 페이지인가?"라는 비교도 Mustache가 못 하므로,
     *      페이지 번호 하나하나를 PageItem(number, active)으로 감싼다.
     */
    @Data
    public static class PageDTO {
        private List<ListDTO> list;
        private int currentPage;
        private int size;
        private int totalPages;
        private long totalElements;
        private boolean first;
        private boolean last;
        private int prevPage;
        private int nextPage;
        private List<PageItem> pageNumbers;

        public PageDTO(Page<Board> page) {
            // 엔티티 → DTO 변환
            this.list = page.getContent().stream()
                    .map(board -> new ListDTO(board))
                    .toList();

            // 0-base → 1-base 정규화
            this.currentPage = page.getNumber() + 1;
            this.size = page.getSize();
            this.totalPages = page.getTotalPages();
            this.totalElements = page.getTotalElements();
            this.first = page.isFirst();
            this.last = page.isLast();

            // 이전/다음 페이지 번호 (템플릿이 산술을 못 하므로 미리 계산)
            // 경계에서는 자기 자신을 넣고, 화면에서는 first/last 플래그로 disabled 처리.
            this.prevPage = this.first ? this.currentPage : this.currentPage - 1;
            this.nextPage = this.last ? this.currentPage : this.currentPage + 1;

            // 페이지 번호 윈도우: 현재 페이지 기준 앞뒤 2페이지 (최대 5개)
            // 예) 전체 10페이지, 현재 5 → [3, 4, 5, 6, 7]
            // 각 번호에 "현재 페이지인가?" 정보를 함께 담아 PageItem으로 만든다.
            int start = Math.max(1, this.currentPage - 2);
            int end = Math.min(this.totalPages, this.currentPage + 2);

            // 빈 리스트를 먼저 만들고, for문으로 하나씩 채워 넣는다.
            // 게시글이 0개라 totalPages가 0이면 end가 0이 되어 (start=1, end=0)
            // 반복문이 한 번도 안 돌고 빈 리스트 그대로 남는다.
            this.pageNumbers = new ArrayList<>();
            for (int i = start; i <= end; i++) {
                boolean isActive = (i == this.currentPage); // 이 번호가 현재 페이지면 true
                this.pageNumbers.add(new PageItem(i, isActive));
            }
        }
    }

    /**
     * 페이지 번호 한 칸을 표현하는 DTO.
     *
     * Mustache는 "현재 페이지인가?" 같은 비교 연산을 못 한다.
     * 그래서 자바에서 각 페이지 번호마다 active 플래그를 미리 계산해 담아준다.
     * - number : 화면에 표시할 페이지 번호 (1-base)
     * - active : 현재 페이지면 true (Bootstrap "active" 클래스 부여용)
     */
    @Data
    public static class PageItem {
        private final int number;
        private final boolean active;
    }
}
