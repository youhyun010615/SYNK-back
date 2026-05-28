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

    @Builder
    public RoomMember(User user, Room room, boolean
            isOwner) {
        this.user = user;
        this.room = room;
        this.isOwner = isOwner;
        this.joinedAt = LocalDateTime.now();
    }


}
