// 알림 목록을 오늘/이번 주로 구분해서 조회

package com.synk.service;

import com.synk.dto.response.NotificationResponse;
import com.synk.entity.Notification;
import com.synk.entity.User;
import com.synk.global.exception.CustomException;
import com.synk.global.exception.ErrorCode;
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

    @Transactional(readOnly = true)
    public NotificationResponse getNotifications() {
        User user = getUser();

        List<Notification> all = notificationRepository.findByUserOrderByCreatedAtDesc(user);

        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime weekStart = LocalDate.now().minusDays(7).atStartOfDay();

        List<NotificationResponse.NotificationInfo> today = all.stream()
                        .filter(n -> n.getCreatedAt().isAfter(todayStart))
                        .map(NotificationResponse.NotificationInfo::from)
                        .toList();

        List<NotificationResponse.NotificationInfo> thisWeek = all.stream()
                .filter(n -> n.getCreatedAt().isAfter(weekStart) && n.getCreatedAt().isBefore(todayStart))
                .map(NotificationResponse.NotificationInfo::from)
                .toList();

        return NotificationResponse.builder()
                .today(today)
                .thisWeek(thisWeek)
                .build();
    }

    private User getUser() {
        Long userId = SecurityUtil.getCurrentUserId();
        return userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }
}

