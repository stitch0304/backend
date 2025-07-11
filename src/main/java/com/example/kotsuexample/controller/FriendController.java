package com.example.kotsuexample.controller;

import com.example.kotsuexample.config.CurrentUser;
import com.example.kotsuexample.dto.ResponseData;
import com.example.kotsuexample.dto.UserResponse;
import com.example.kotsuexample.entity.Friend;
import com.example.kotsuexample.entity.enums.FriendStatus;
import com.example.kotsuexample.entity.enums.NotificationType;
import com.example.kotsuexample.exception.NoneInputValueException;
import com.example.kotsuexample.service.FriendService;
import com.example.kotsuexample.service.NotificationService;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/friend")
public class FriendController {

    private final FriendService friendService;
    private final NotificationService notificationService;

    @GetMapping("/")
    public ResponseEntity<List<UserResponse>> getFriends(@CurrentUser Integer userId) {
        List<UserResponse> friends = friendService.getFriends(userId);
        return ResponseEntity.ok(friends);
    }

    // 요청 및 취소
    // t/f 로 상태 표현하기
    @PostMapping("/request")
    public ResponseEntity<ResponseData<Boolean>> requestOrCancelFriend(
            @CurrentUser Integer userId, @RequestBody Map<String, Integer> payload) {

        Integer addresseeId = payload.get("addresseeId");
        if (addresseeId == null) throw new NoneInputValueException("입력된 아이디 값이 없습니다.");

        // true면 "요청", false면 "취소"
        Boolean isFriend = friendService.requestOrCancelFriend(userId, addresseeId);

        // 알림은 "새 요청"일 때만 보냄
        if (isFriend) {
            String content = "새 친구 요청이 도착했습니다!";
            notificationService.sseNotifyRequest(userId, addresseeId, content, NotificationType.FRIEND);
        }

        return ResponseEntity.ok(ResponseData.<Boolean>builder().data(isFriend).build());
    }

    // 내가 보낸 요청
    @GetMapping("/my-request")
    public ResponseEntity<List<UserResponse>> getMyRequester(@CurrentUser Integer userId) {
        List<UserResponse> result = friendService.getRequestedFriends(userId);
        return ResponseEntity.ok(result);
    }

    // 내가 받은 요청
    @GetMapping("/my-accept")
    public ResponseEntity<List<UserResponse>> getMyAddressee(@CurrentUser Integer userId) {
        List<UserResponse> result = friendService.getPendingRequests(userId);
        return ResponseEntity.ok(result);
    }

    // 친구 삭제
    @DeleteMapping("/{friendId}")
    public ResponseEntity<Void> deleteFriend(@CurrentUser Integer userId, @PathVariable Integer friendId) {
        friendService.deleteFriend(userId, friendId);
        return ResponseEntity.noContent().build(); // 204 No Content
    }

    // 친구 요청 수락
    @PostMapping("/accept")
    public ResponseEntity<ResponseData<Boolean>> acceptFriendRequest(
            @CurrentUser Integer userId,
            @RequestBody Map<String, Integer> payload) {

        Integer requesterId = payload.get("requesterId");
        if (requesterId == null) throw new NoneInputValueException("요청한 유저 ID가 없습니다.");

        Boolean accepted = friendService.acceptFriendRequest(requesterId, userId); // 요청자 → 나
        return ResponseEntity.ok(ResponseData.<Boolean>builder().data(accepted).build());
    }

    @GetMapping("/status/{targetUserId}")
    public ResponseEntity<ResponseData<FriendDto>> getFriendStatus(
            @CurrentUser Integer userId,
            @PathVariable Integer targetUserId
    ) {
        Friend friend = friendService.getFriendRelation(userId, targetUserId); // 관계 null일 수도 있음
        FriendDto dto = (friend != null) ? FriendDto.from(friend) : null;
        return ResponseEntity.ok(ResponseData.<FriendDto>builder().data(dto).build());
    }

    // 친구 요청 거절 (받은 요청 삭제)
    @DeleteMapping("/reject")
    public ResponseEntity<Void> rejectFriendRequest(
            @CurrentUser Integer userId,
            @RequestBody Map<String, Integer> payload) {
        Integer requesterId = payload.get("requesterId");
        if (requesterId == null) throw new NoneInputValueException("요청한 유저 ID가 없습니다.");

        friendService.rejectFriendRequest(requesterId, userId); // 요청자 → 나(수락/거절)
        return ResponseEntity.noContent().build();
    }

    @Getter
    @Builder
    public static class FriendDto {
        private Integer id;
        private Integer requesterId;
        private Integer addresseeId;
        private FriendStatus status;
        private LocalDateTime createdAt;

        public static FriendDto from(Friend friend) {
            return FriendDto.builder()
                    .id(friend.getId())
                    .requesterId(friend.getRequesterId())
                    .addresseeId(friend.getAddresseeId())
                    .status(friend.getStatus())
                    .createdAt(friend.getCreatedAt())
                    .build();
        }
    }
}
