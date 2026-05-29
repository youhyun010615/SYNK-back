// 채팅 메시지에 달리는 이모지 리액션을 저장하는 테이블
// 같은 유저가 같은 메시지에 같은 이모지를 중복으로 달 수 없다.

package com.synk.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "chat_reactions",
        uniqueConstraints = @UniqueConstraint(columnNames = {"chat_id", "user_id", "emoji"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatReaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_id", nullable = false)
    private RoomChat chat;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 10)
    private String emoji;

    @Column(name = "created_at", nullable = false,
            updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public ChatReaction(RoomChat chat, User user, String
            emoji) {
        this.chat = chat;
        this.user = user;
        this.emoji = emoji;
        this.createdAt = LocalDateTime.now();
    }

}
