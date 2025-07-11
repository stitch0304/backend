package com.example.kotsuexample.service;

import com.example.kotsuexample.dto.BoardListDTO;
import com.example.kotsuexample.entity.Board;
import com.example.kotsuexample.entity.User;
import com.example.kotsuexample.exception.NoneInputValueException;
import com.example.kotsuexample.repository.BoardRepository;
import io.lettuce.core.Value;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BoardService {
    private final BoardRepository boardRepository;
    private final UserService userService;

    // 게시글 페이지네이션 조회
    public Page<BoardListDTO> getBoardPage(Pageable pageable) {
        Page<Board> boards = boardRepository.findAll(pageable);

        return boards.map(board -> {
            BoardListDTO dto = new BoardListDTO();
            dto.setId(board.getId());
            dto.setTitle(board.getTitle());
            dto.setContent(board.getContent());
            dto.setViewCount(board.getViewCount());
            dto.setUser(board.getUser());
            dto.setCommentCount(board.getComments().size());
            dto.setCreatedAt(board.getCreatedAt());
            dto.setUpdatedAt(board.getUpdatedAt());
            return dto;
        });
    }

    // 조회수 증가
    @Transactional
    public void increaseViewCount(Integer id) {
        Board board = boardRepository.findById(id)
                .orElseThrow(() -> new NoneInputValueException("아이디 값에 따른 게시글이 존재하지 않습니다."));
        board.setViewCount(board.getViewCount() + 1);
        // save 불필요(JPA dirty checking)
    }

    public Board saveBoard(Board board, Integer userId) {
        User foundedUser = userService.getUserById(userId);
        board.setUser(foundedUser);
        board.setCreatedAt(LocalDateTime.now());
        return boardRepository.save(board);
    }

    public Optional<Board> getBoardById(Integer id) {
        return boardRepository.findById(id);
    }

    public Board updateBoard(Integer id, Integer userId, String title, String content) {
        User foundedUser = userService.getUserById(userId);
        Board foundedBoard = boardRepository.findByIdAndUser(id, foundedUser)
                .orElseThrow(() -> new NoneInputValueException("아이디와 유저 값에 따른 게시글이 존재하지 않습니다."));

        foundedBoard.setTitle(title);
        foundedBoard.setContent(content);
        foundedBoard.setUpdatedAt(LocalDateTime.now());
        return boardRepository.save(foundedBoard);
    }

    public void deleteBoard(Integer id, Integer userId) {
        User foundedUser = userService.getUserById(userId);
        Board foundedBoard = boardRepository.findByIdAndUser(id, foundedUser)
                .orElseThrow(() -> new NoneInputValueException("아이디와 유저 값에 따른 게시글이 존재하지 않습니다."));

        boardRepository.deleteById(foundedBoard.getId());
    }

    public Page<BoardListDTO> getBoardsByUser(Integer userId, Pageable pageable) {
        Page<Board> boards = boardRepository.findByUserId(userId, pageable);

        return boards.map(board -> {
            BoardListDTO dto = new BoardListDTO();
            dto.setId(board.getId());
            dto.setTitle(board.getTitle());
            dto.setContent(board.getContent());
            dto.setViewCount(board.getViewCount());
            dto.setUser(board.getUser());
            dto.setCommentCount(board.getComments().size());
            dto.setCreatedAt(board.getCreatedAt());
            dto.setUpdatedAt(board.getUpdatedAt());
            return dto;
        });
    }

    // 자유게시판 list중 4개만 뽑아오기
    public List<BoardListDTO> getLatestFourBoards() {
        List<Board> boards = boardRepository.findTop4ByOrderByCreatedAtDesc();

        return boards.stream().map(board -> {
            BoardListDTO dto = new BoardListDTO();
            dto.setId(board.getId());
            dto.setTitle(board.getTitle());
            dto.setContent(board.getContent());
            dto.setViewCount(board.getViewCount());
            dto.setUser(board.getUser());
            dto.setCommentCount(board.getComments().size());
            dto.setCreatedAt(board.getCreatedAt());
            dto.setUpdatedAt(board.getUpdatedAt());
            return dto;
        }).toList();
    }
}
