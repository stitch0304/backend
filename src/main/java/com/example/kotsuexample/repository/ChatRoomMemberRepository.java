package com.example.kotsuexample.repository;

import com.example.kotsuexample.entity.ChatRoomMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatRoomMemberRepository extends JpaRepository<ChatRoomMember, Integer> {
    List<ChatRoomMember> findByChatRoomId(Integer chatRoomId);

    List<ChatRoomMember> findByUserId(Integer userId);

    boolean existsByChatRoomIdAndUserId(Integer id, Integer id1);

    void deleteByChatRoomIdAndUserId(Integer id, Integer userId);

    void deleteByChatRoomId(Integer id);
}
