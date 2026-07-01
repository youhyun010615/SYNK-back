// 알림 목록을 오늘/이번 주로 구분해서 조회

package com.synk.service;

import com.synk.dto.response.NotificationResponse;
import com.synk.entity.Notification;
import com.synk.entity.User;
import com.synk.global.exception.CustomException;
import com.synk.global.exception.ErrorCode;
import com.synk.repository.MissionRepository;
import com.synk.repository.NotificationRepository;
import com.synk.repository.UserRepository;
import com.synk.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final MissionRepository missionRepository;

    @Transactional(readOnly = true)
    public NotificationResponse getNotifications() {
        User user = getUser();

        List<Notification> all = notificationRepository.findByUserOrderByCreatedAtDesc(user);

        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime weekStart = LocalDate.now().minusDays(7).atStartOfDay();

        List<NotificationResponse.NotificationInfo> today = all.stream()
                        .filter(n -> n.getCreatedAt().isAfter(todayStart))
                        .map(n -> NotificationResponse.NotificationInfo.from(n, resolveRoomName(n)))
                        .toList();

        List<NotificationResponse.NotificationInfo> thisWeek = all.stream()
                .filter(n -> n.getCreatedAt().isAfter(weekStart) && n.getCreatedAt().isBefore(todayStart))
                .map(n -> NotificationResponse.NotificationInfo.from(n, resolveRoomName(n)))
                .toList();

        return NotificationResponse.builder()
                .today(today)
                .thisWeek(thisWeek)
                .build();
    }

    @Transactional
    public void markRead(Long notificationId) {
        User user = getUser();
        Notification notification = notificationRepository.findByIdAndUser(notificationId, user)
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_REQUEST));
        notification.read();
    }

    @Transactional
    public void markAllRead() {
        User user = getUser();
        List<Notification> all = notificationRepository.findByUserOrderByCreatedAtDesc(user);
        all.forEach(Notification::read);
    }

    private String resolveRoomName(Notification n) {
        if (n.getRelatedId() == null) return null;
        return missionRepository.findById(n.getRelatedId())
                .map(m -> m.getRoom().getName())
                .orElse(null);
    }

    private User getUser() {
        Long userId = SecurityUtil.getCurrentUserId();
        return userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }
}

