package com.example.kotsuexample.service;

import com.example.kotsuexample.entity.enums.NotificationType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SseService {

    private final Map<Integer, SseEmitter> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(Integer userId) {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        emitters.put(userId, emitter);

        emitter.onCompletion(() -> emitters.remove(userId));
        emitter.onTimeout(() -> emitters.remove(userId));
        emitter.onError((e) -> emitters.remove(userId));

        // ✅ 연결 즉시 더미 데이터라도 전송 (크롬 브라우저에서 필수!)
        try {
            emitter.send(SseEmitter.event()
                    .name("connect")
                    .data("connected!")); // 혹은 빈 문자열도 OK
        } catch (IOException e) {
            emitter.completeWithError(e);
        }

        return emitter;
    }

    public void send(Integer receiverId, NotificationType type, Object data) {
        SseEmitter emitter = emitters.get(receiverId);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event()
                        .name(type.name().toLowerCase()) // ex: "friend", "study", "system", "chat"
                        .data(data));
            } catch (IOException e) {
                emitters.remove(receiverId);
            }
        }
    }
}
