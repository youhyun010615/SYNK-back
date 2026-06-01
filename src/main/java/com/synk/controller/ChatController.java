package com.synk.controller;

import com.synk.dto.request.AddReactionRequest;
import com.synk.dto.request.SendMessageRequest;
import com.synk.dto.response.ChatMessageResponse;
import com.synk.global.response.ApiResponse;
import com.synk.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/rooms/{roomId}/chats")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @GetMapping
    public ResponseEntity<ApiResponse<ChatMessageResponse>>
    getChats(@PathVariable Long roomId) {
        ChatMessageResponse response = chatService.getChats(roomId);

        return ResponseEntity.ok(ApiResponse.success(response, "채팅 조회 성공"));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Long>> sendMessage(
            @PathVariable Long roomId,
            @RequestBody SendMessageRequest request) {
        Long messageId = chatService.sendMessage(roomId, request);

        return ResponseEntity.ok(ApiResponse.success(messageId, "메시지 전송 완료"));
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
