// 오늘/이번 주 알림을 구분해서 담는다.

package com.synk.dto.response;

import com.synk.entity.Notification;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class NotificationResponse {

    private List<NotificationInfo> today;
    private List<NotificationInfo> thisWeek;

    @Getter
    @Builder
    public static class NotificationInfo {
        private Long id;
        private String type;
        private String title;
        private String content;
        private LocalDateTime createdAt;
        private boolean isRead;
        private Long relatedId;

        public static NotificationInfo from(Notification notification) {
            return NotificationInfo.builder()
                    .id(notification.getId())
                    .type(notification.getType().name())
                    .title(notification.getTitle())
                    .content(notification.getContent())
                    .createdAt(notification.getCreatedAt())
                    .isRead(notification.isRead())
                    .relatedId(notification.getRelatedId())
                    .build();
        }
    }
}

