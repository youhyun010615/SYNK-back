// 카카오 / 구글로 로그인한 사용자 정보를 제공하는 테이블
// OAuth 제공자 정보, 프로필, FCM 토큰(푸시 알림용), 알림 설정이 여기 들어감

package com.synk.entity;

import com.synk.global.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "users",
        uniqueConstraints =
        @UniqueConstraint(columnNames = {"auth_provider", "auth_provider_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "auth_provider", nullable = false, length = 20)
    private AuthProvider authProvider;

    @Column(name = "auth_provider_id", nullable = false, length = 255)
    private String authProviderId;

    @Column(nullable = false, length = 1000)
    private String name;

    @Column(name = "profile_image", length = 1000)
    private String profileImage;

    @Column(length = 255)
    private String email;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "mission_alert", nullable = false)
    private boolean missionAlert = true;

    @Column(name = "result_alert", nullable = false)
    private boolean resultAlert = true;

    @Column(name = "highlight_alert", nullable = false)
    private boolean highlightAlert = true;


    @Builder
    public User(AuthProvider authProvider, String
                        authProviderId, String name,
                String profileImage, String email) {
        this.authProvider = authProvider;
        this.authProviderId = authProviderId;
        this.name = name;
        this.profileImage = profileImage;
        this.email = email;
        this.status = "active";
        this.missionAlert = true;
        this.resultAlert = true;
        this.highlightAlert = true;
    }

    // 기존 유저가 이메일 없이 가입했던 경우 로그인 시 채워넣기 위함
    public void updateEmailIfAbsent(String email) {
        if ((this.email == null || this.email.isBlank()) && email != null && !email.isBlank()) {
            this.email = email;
        }
    }

    public enum AuthProvider {
        kakao, google
    }

    public void updateProfile(String name, String
            profileImage) {
        if (name != null) this.name = name;
        if (profileImage != null) this.profileImage = profileImage;
    }

    public void updateAlertSettings(boolean
                                            missionAlert, boolean resultAlert, boolean
                                            highlightAlert) {
        this.missionAlert = missionAlert;
        this.resultAlert = resultAlert;
        this.highlightAlert = highlightAlert;
    }

    public void withdraw() {
        this.status = "pending_delete";
        this.deletedAt = LocalDateTime.now();
    }
}
