package com.example.kotsuexample.service;

import com.example.kotsuexample.config.redis.RedisPublisher;
import com.example.kotsuexample.dto.ChatMessageDTO;
import com.example.kotsuexample.dto.ChatReadEvent;
import com.example.kotsuexample.dto.GroupChatMessageDTO;
import com.example.kotsuexample.entity.*;
import com.example.kotsuexample.entity.enums.MessageType;
import com.example.kotsuexample.exception.StudyDataNotFoundException;
import com.example.kotsuexample.repository.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor

public class ChatMessageService {
    private final ChatMessageRepository chatMessageRepository;
    private final ChatReadStatusRepository chatReadStatusRepository;
    private final ChatRoomMemberRepository chatRoomMemberRepository;
    private final ChatReadService chatReadService;
    private final UserRepository userRepository;
    private final RedisPublisher redisPublisher;
    private final ChatRoomRepository chatRoomRepository;
    private final ObjectMapper objectMapper;

    public List<ChatMessageDTO> getChatMessagesWithReadStatus(Integer roomId, Integer myUserId) {
        // 1. 메시지 전체 조회
        List<ChatMessage> messages = chatMessageRepository.findByChatRoomIdOrderBySentAtAsc(roomId);

        // 2. 채팅방 멤버들 조회 (1:1 채팅 가정)
        List<Integer> members = chatRoomMemberRepository.findByChatRoomId(roomId)
                .stream().map(ChatRoomMember::getUserId).toList();

        Integer opponentId = members.stream()
                .filter(id -> !id.equals(myUserId))
                .findFirst()
                .orElse(null);

        // 3. 상대방의 마지막 읽음 시각
        LocalDateTime opponentLastReadAt = chatReadStatusRepository
                .findByChatRoomIdAndUserId(roomId, opponentId)
                .map(ChatReadStatus::getLastReadAt)
                .orElse(LocalDateTime.MIN);

        // 4. 메시지별로 isRead 계산
        return messages.stream()
                .map(msg -> ChatMessageDTO.builder()
                        .id(msg.getId())
                        .chatRoomId(msg.getChatRoomId())
                        .senderId(msg.getSenderId())
                        .message(msg.getMessage())
                        .messageType(msg.getMessageType())
                        .sentAt(msg.getSentAt().toString())
                        // 내가 보낸 메시지라면: 상대방의 읽음시각 이후라면 "안 읽음" (isRead=false), 아니면 "읽음"(isRead=true)
                        .isRead(!msg.getSenderId().equals(myUserId) || !msg.getSentAt().isAfter(opponentLastReadAt)) // 내가 아닌 메시지는 항상 읽음(true)
                        .build())
                .collect(Collectors.toList());
    }

    // 스터디방/그룹방: 메시지별 '읽지 않은 인원 수' 포함해서 반환
    public List<GroupChatMessageDTO> getGroupMessagesWithUnreadCount(Integer chatRoomId, Integer userId) {
        // 1. 스터디룸 → 그룹 채팅방 id 얻기
        ChatRoom chatRoom = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new StudyDataNotFoundException("채팅방이 존재하지 않습니다."));

        boolean isMember = chatRoomMemberRepository.existsByChatRoomIdAndUserId(chatRoomId, userId);
        if (!isMember) {
            throw new IllegalArgumentException("채팅방 멤버가 아닙니다.");
        }

        // 3. 메시지 불러오기
        List<ChatMessage> messages = chatMessageRepository.findByChatRoomIdOrderBySentAtAsc(chatRoom.getId());

        Set<Integer> senderIds = messages.stream().map(ChatMessage::getSenderId).collect(Collectors.toSet());
        Map<Integer, User> userMap = userRepository.findAllById(senderIds)
                .stream().collect(Collectors.toMap(User::getId, u -> u));

        return messages.stream()
                .map(msg -> {
                    User sender = userMap.get(msg.getSenderId());
                    return GroupChatMessageDTO.builder()
                            .id(msg.getId())
                            .chatRoomId(msg.getChatRoomId())
                            .senderId(msg.getSenderId())
                            .senderNickname(sender != null ? sender.getNickname() : null)
                            .senderProfileImage(sender != null ? sender.getProfileImage() : null)
                            .message(msg.getMessage())
                            .messageType(msg.getMessageType())
                            .sentAt(
                                    msg.getSentAt()
                                            .atOffset(ZoneOffset.UTC)
                                            .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                            )
                            .unreadCount(chatReadService.getUnreadMemberCountForMessage(chatRoom.getId(), msg.getId()))
                            .messageId(msg.getId())
                            .build();
                })
                .toList();
    }

    public void markMessagesAsRead(Integer roomId, Integer userId, LocalDateTime lastReadAt) throws JsonProcessingException {
        // 1. 읽음 상태 저장 (기존대로)
        chatReadService.markChatAsRead(roomId, userId, lastReadAt);

        // 2. 마지막 메시지 ID 가져오기
        Integer lastMessageId = chatMessageRepository.findTopByChatRoomIdOrderBySentAtDesc(roomId)
                .map(ChatMessage::getId)
                .orElse(null);

        if (lastMessageId != null) {
            // 3. 해당 메시지의 미확인 인원 수 계산
            int unreadCount = chatReadService.getUnreadMemberCountForMessage(roomId, lastMessageId);

            // 4. 읽음 이벤트 객체 생성
            ChatReadEvent event = ChatReadEvent.builder()
                    .messageType(MessageType.READ)
                    .messageId(lastMessageId)
                    .unreadCount(unreadCount)
                    .userId(userId)
                    .lastReadAt(lastReadAt.toString())
                    .chatRoomId(roomId)
                    .build();

            // 5. Redis Pub/Sub로 이벤트 전파
            redisPublisher.publish("chatroom:" + roomId, objectMapper.writeValueAsString(event));
        }
    }

    public void sendSystemMessageToGroup(Integer chatRoomId, String content) throws JsonProcessingException {
        // senderId는 0 또는 null로(혹은 별도 SYSTEM 유저)
        ChatMessage msg = ChatMessage.builder()
                .chatRoomId(chatRoomId)
                .senderId(0) // 시스템
                .messageType(MessageType.SYSTEM)
                .message(content)
                .sentAt(LocalDateTime.now())
                .build();
        chatMessageRepository.save(msg);

        // DTO로 변환해서 전송
        GroupChatMessageDTO dto = GroupChatMessageDTO.builder()
                .id(msg.getId())
                .chatRoomId(chatRoomId)
                .senderId(0)
                .senderNickname("SYSTEM")
                .messageType(MessageType.SYSTEM)
                .message(content)
                .sentAt(msg.getSentAt().toString())
                .build();

        // Redis PubSub 브로드캐스트 (프론트에도 즉시 전파)
        redisPublisher.publish("chatroom:" + chatRoomId, objectMapper.writeValueAsString(dto));
    }
}
