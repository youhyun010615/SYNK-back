package com.synk.controller;

import com.synk.dto.response.NotificationResponse;
import com.synk.global.response.ApiResponse;
import com.synk.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<ApiResponse<NotificationResponse>> getNotifications() {
        NotificationResponse response = notificationService.getNotifications();
        return ResponseEntity.ok(ApiResponse.success(response, "알림 조회 성공"));
    }

    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<ApiResponse<Void>> markRead(@PathVariable Long notificationId) {
        notificationService.markRead(notificationId);
        return ResponseEntity.ok(ApiResponse.success("읽음 처리 완료"));
    }

    @PatchMapping("/read-all")
    public ResponseEntity<ApiResponse<Void>> markAllRead() {
        notificationService.markAllRead();
        return ResponseEntity.ok(ApiResponse.success("전체 읽음 처리 완료"));
    }
}

