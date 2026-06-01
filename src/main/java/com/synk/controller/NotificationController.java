package com.synk.controller;

import com.synk.dto.response.NotificationResponse;
import com.synk.global.response.ApiResponse;
import com.synk.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import
        org.springframework.web.bind.annotation.RequestMapping;
import
        org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<ApiResponse<NotificationResponse>> getNotifications() {
        NotificationResponse response = notificationService.getNotifications();

        return
                ResponseEntity.ok(ApiResponse.success(response, "알림 조회 성공"));
    }
}

