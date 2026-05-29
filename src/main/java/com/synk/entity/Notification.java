// 유저에게 발송된 푸시 알림 내역을 저장하는 테이블
// 미션 시작, 미션 완료, SYNKLOG 생성, 맴버 참여 알림이 여기 기록

package com.synk.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "notifications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NotificationType type;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 500)
    private String content;

    @Column(name = "related_id")
    private Long relatedId;

    @Column(name = "is_read", nullable = false)
    private boolean isRead;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public Notification(User user, NotificationType
                                type, String title,
                        String content, Long relatedId)
    {
        this.user = user;
        this.type = type;
        this.title = title;
        this.content = content;
        this.relatedId = relatedId;
        this.isRead = false;
        this.createdAt = LocalDateTime.now();
    }

    public enum NotificationType {
        MISSION_START, MISSION_COMPLETE,
        SYNKLOG_CREATED, MEMBER_JOIN
    }

    public void read() {
        this.isRead = true;
    }

}
