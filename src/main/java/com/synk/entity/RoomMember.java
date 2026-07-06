package com.synk.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "room_members",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "room_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoomMember {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @Column(name = "is_owner", nullable = false)
    private boolean isOwner;

    @Column(name = "joined_at", nullable = false,
            updatable = false)
    private LocalDateTime joinedAt;

    @Column(name = "last_read_message_id")
    private Long lastReadMessageId;

    // 방별 채팅 알림 on/off (종 아이콘). 기본 on
    @Column(name = "chat_alert_enabled", nullable = false)
    private boolean chatAlertEnabled = true;

    @Builder
    public RoomMember(User user, Room room, boolean
            isOwner) {
        this.user = user;
        this.room = room;
        this.isOwner = isOwner;
        this.joinedAt = LocalDateTime.now();
        this.chatAlertEnabled = true;
    }

    public void setChatAlertEnabled(boolean enabled) {
        this.chatAlertEnabled = enabled;
    }

    public void promoteToOwner() {
        this.isOwner = true;
    }

    public void markRead(Long messageId) {
        if (messageId == null) return;
        if (this.lastReadMessageId == null || messageId > this.lastReadMessageId) {
            this.lastReadMessageId = messageId;
        }
    }

}
