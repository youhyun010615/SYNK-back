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

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "profile_image", length = 255)
    private String profileImage;

    @Column(name = "fcm_token", length = 255)
    private String fcmToken;

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
                String profileImage, String fcmToken) {
        this.authProvider = authProvider;
        this.authProviderId = authProviderId;
        this.name = name;
        this.profileImage = profileImage;
        this.fcmToken = fcmToken;
        this.status = "active";
        this.missionAlert = true;
        this.resultAlert = true;
        this.highlightAlert = true;
    }

    public enum AuthProvider {
        kakao, google
    }

    public void updateProfile(String name, String
            profileImage) {
        this.name = name;
        this.profileImage = profileImage;
    }

    public void updateFcmToken(String fcmToken) {
        this.fcmToken = fcmToken;
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
