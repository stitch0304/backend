package com.example.kotsuexample.controller;

import com.example.kotsuexample.config.CurrentUser;
import com.example.kotsuexample.dto.BoardListDTO;
import com.example.kotsuexample.entity.Board;
import com.example.kotsuexample.service.BoardService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/board")
public class BoardController {

    private final BoardService boardService;

    // 게시글 등록
    @PostMapping
    public ResponseEntity<Board> createBoard(
            @RequestBody Board board,
            @CurrentUser Integer userId
    ) {
        return ResponseEntity.ok(boardService.saveBoard(board, userId));
    }

    // 게시글 목록 조회: 최신순 or 조회순
//    최신순:
//            /boards?page=0&size=10&sort=createdAt,desc
//
//    조회순:
//            /boards?page=0&size=10&sort=viewCount,desc
    @GetMapping // ex) /boards?page=0&size=10&sort=createdAt,desc or sort=viewCount,desc
    public ResponseEntity<Page<BoardListDTO>> getBoards(Pageable pageable) {
        return ResponseEntity.ok(boardService.getBoardPage(pageable));
    }

    // 상세 조회 + 조회수 증가
    @GetMapping("/{boardId}")
    public ResponseEntity<Board> getBoard(@PathVariable Integer boardId) {
        boardService.increaseViewCount(boardId);
        return boardService.getBoardById(boardId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // 게시글 수정
    @PutMapping("/{boardId}")
    public ResponseEntity<Board> updateBoard(
            @PathVariable Integer boardId,
            @CurrentUser Integer userId,
            @RequestBody Board board) {
        Board updated = boardService.updateBoard(boardId, userId, board.getTitle(), board.getContent());
        return ResponseEntity.ok(updated);
    }

    // 게시글 삭제
    @DeleteMapping("/{boardId}")
    public ResponseEntity<Void> deleteBoard(
            @PathVariable Integer boardId,
            @CurrentUser Integer userId) {
        boardService.deleteBoard(boardId, userId);
        return ResponseEntity.noContent().build();
    }

    // 내 거
    @GetMapping("/my")
    public ResponseEntity<Page<BoardListDTO>> getMyBoards(
            @CurrentUser Integer userId,
            Pageable pageable
    ) {
        return ResponseEntity.ok(boardService.getBoardsByUser(userId, pageable));
    }

    // 자유게시판 list중 4개만 뽑아오기
    @GetMapping("/latest")
    public ResponseEntity<List<BoardListDTO>> getLatestBoards() {
        return ResponseEntity.ok(boardService.getLatestFourBoards());
    }
}
