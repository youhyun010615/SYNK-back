package com.synk.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "room_chats")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoomChat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false,
            length = 20)
    private MessageType messageType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "created_at", nullable = false,
            updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public RoomChat(Room room, User user, MessageType
            messageType, String content) {
        this.room = room;
        this.user = user;
        this.messageType = messageType;
        this.content = content;
        this.createdAt = LocalDateTime.now();
    }

    public enum MessageType {
        TEXT, IMAGE, EMOJI
    }



}
