package com.synk.controller;

import com.synk.dto.request.AddReactionRequest;
import com.synk.dto.request.SendMessageRequest;
import com.synk.dto.response.ChatMessageResponse;
import com.synk.dto.response.ChatSocketResponse;
import com.synk.global.response.ApiResponse;
import com.synk.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/api/rooms/{roomId}/chats")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    // WebSocket STOMP: 메시지 발행 → 방 전체 브로드캐스트
    @MessageMapping("/rooms/{roomId}/chat")
    @SendTo("/topic/rooms/{roomId}")
    public ChatSocketResponse handleMessage(
            @DestinationVariable Long roomId,
            SendMessageRequest request,
            Principal principal) {
        Long userId = Long.parseLong(principal.getName());
        return chatService.sendMessageViaSocket(roomId, userId, request);
    }

    @GetMapping
    public ResponseEntity<ApiResponse<ChatMessageResponse>>
    getChats(@PathVariable Long roomId) {
        ChatMessageResponse response = chatService.getChats(roomId);

        return ResponseEntity.ok(ApiResponse.success(response, "채팅 조회 성공"));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ChatSocketResponse>> sendMessage(
            @PathVariable Long roomId,
            @RequestBody SendMessageRequest request) {
        ChatSocketResponse response = chatService.sendMessage(roomId, request);

        return ResponseEntity.ok(ApiResponse.success(response, "메시지 전송 완료"));
    }

    @PostMapping("/{messageId}/reactions")
    public ResponseEntity<ApiResponse<Void>> addReaction(
            @PathVariable Long roomId,
            @PathVariable Long messageId,
            @RequestBody AddReactionRequest request) {
        chatService.addReaction(roomId, messageId, request);

        return ResponseEntity.ok(ApiResponse.success("리액션 추가 완료"));
    }
}
