package com.synk.service;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;
import com.synk.entity.User;
import com.synk.entity.UserFcmToken;
import com.synk.repository.NotificationRepository;
import com.synk.repository.UserFcmTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class FcmService {

    private final NotificationRepository notificationRepository;
    private final UserFcmTokenRepository userFcmTokenRepository;

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

        // 유저가 등록한 모든 기기(폰, 맥북 등)로 각각 전송
        List<UserFcmToken> tokens = userFcmTokenRepository.findByUser(user);
        if (tokens.isEmpty()) {
            log.warn("FCM 토큰 없음 — push 미발송: userId={}, type={}", user.getId(), type);
        }
        for (UserFcmToken userFcmToken : tokens) {
            sendToToken(user.getId(), userFcmToken.getToken(), title, body);
        }
    }

    // data 페이로드 포함 전송 (포그라운드에서도 FE가 직접 처리할 수 있도록)
    public void sendDataMessage(User user, com.synk.entity.Notification.NotificationType type,
                                String title, String body, Long relatedId, Map<String, String> data) {
        notificationRepository.save(com.synk.entity.Notification.builder()
                .user(user)
                .type(type)
                .title(title)
                .content(body)
                .relatedId(relatedId)
                .build());

        List<UserFcmToken> tokens = userFcmTokenRepository.findByUser(user);
        if (tokens.isEmpty()) {
            log.warn("FCM 토큰 없음 — push 미발송: userId={}, type={}", user.getId(), type);
        }
        for (UserFcmToken userFcmToken : tokens) {
            sendToTokenWithData(user.getId(), userFcmToken.getToken(), title, body, data);
        }
    }

    private void sendToToken(Long userId, String token, String title, String body) {
        sendToTokenWithData(userId, token, title, body, Map.of());
    }

    private void sendToTokenWithData(Long userId, String token, String title, String body, Map<String, String> data) {
        try {
            Message.Builder builder = Message.builder()
                    .setNotification(Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build())
                    .putAllData(data)
                    .setToken(token);
            FirebaseMessaging.getInstance().send(builder.build());
            log.info("FCM 전송 성공: userId={}", userId);
        } catch (FirebaseMessagingException e) {
            log.warn("FCM 전송 실패: userId={}, error={}", userId, e.getMessage());
            if (e.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED) {
                userFcmTokenRepository.deleteByToken(token);
            }
        }
    }
}
