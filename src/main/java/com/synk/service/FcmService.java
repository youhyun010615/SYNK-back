package com.synk.service;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import com.synk.entity.User;
import com.synk.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class FcmService {

    private final NotificationRepository notificationRepository;

    public void sendAndSave(User user, com.synk.entity.Notification.NotificationType type,
                            String title, String body, Long relatedId) {
        // DB 저장
        notificationRepository.save(com.synk.entity.Notification.builder()
                .user(user)
                .type(type)
                .title(title)
                .content(body)
                .relatedId(relatedId)
                .build());

        // FCM 전송 (토큰 없으면 스킵)
        String token = user.getFcmToken();
        if (token == null || token.isBlank()) return;

        try {
            Message message = Message.builder()
                    .setNotification(Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build())
                    .setToken(token)
                    .build();
            FirebaseMessaging.getInstance().send(message);
            log.info("FCM 전송 성공: userId={}", user.getId());
        } catch (FirebaseMessagingException e) {
            log.warn("FCM 전송 실패: userId={}, error={}", user.getId(), e.getMessage());
        }
    }
}
