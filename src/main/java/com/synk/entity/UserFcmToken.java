// 유저 1명이 여러 기기(폰, 맥북 등)를 쓸 수 있어 기기별 FCM 토큰을 따로 저장한다.
// 같은 토큰 문자열은 항상 하나의 기기를 가리키므로 token에 unique 제약을 둔다.

package com.synk.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_fcm_tokens",
        uniqueConstraints = @UniqueConstraint(columnNames = {"token"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserFcmToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 255)
    private String token;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public UserFcmToken(User user, String token) {
        this.user = user;
        this.token = token;
        this.createdAt = LocalDateTime.now();
    }

    public void reassignTo(User user) {
        this.user = user;
    }
}
