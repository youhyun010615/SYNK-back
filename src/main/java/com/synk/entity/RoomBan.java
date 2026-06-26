// 강퇴된 멤버가 같은 방에 초대 코드로 재입장하지 못하도록 막기 위한 기록

package com.synk.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "room_bans",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "room_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoomBan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "banned_at", nullable = false, updatable = false)
    private LocalDateTime bannedAt;

    @Builder
    public RoomBan(Room room, User user) {
        this.room = room;
        this.user = user;
        this.bannedAt = LocalDateTime.now();
    }
}
