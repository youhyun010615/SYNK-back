// 유저 관련 비즈니스 로직
// 프로필 조회/수정, 알림 설정 변경, 회원 탈퇴를 처리

package com.synk.service;

import com.synk.dto.request.UpdateNotificationRequest;
import com.synk.dto.request.UpdateProfileRequest;
import com.synk.dto.response.UserResponse;
import com.synk.entity.User;
import com.synk.entity.UserFcmToken;
import com.synk.global.exception.CustomException;
import com.synk.global.exception.ErrorCode;
import com.synk.repository.UserFcmTokenRepository;
import com.synk.repository.UserRepository;
import com.synk.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserFcmTokenRepository userFcmTokenRepository;

    @Transactional(readOnly = true)
    public UserResponse getMyProfile() {
        User user = getUser();
        return UserResponse.from(user);
    }

    @Transactional
    public void updateProfile(UpdateProfileRequest request) {
        User user = getUser();
        user.updateProfile(request.getName(), request.getProfileImage());
    }

    @Transactional
    public void updateNotification(UpdateNotificationRequest request) {
        User user = getUser();
        user.updateAlertSettings(
                request.isMissionNotification(),
                request.isResultNotification(),
                request.isHighlightNotification()
        );
    }

    @Transactional
    public void updateFcmToken(String fcmToken) {
        if (fcmToken == null || fcmToken.isBlank()) return;
        User user = getUser();

        // 같은 토큰(=같은 기기)이 이미 등록돼 있으면 소유자만 갱신, 없으면 새로 등록
        // (기기별로 따로 저장해서 한 유저가 여러 기기를 써도 서로 토큰을 안 덮어씀)
        userFcmTokenRepository.findByToken(fcmToken)
                .ifPresentOrElse(
                        existing -> existing.reassignTo(user),
                        () -> userFcmTokenRepository.save(UserFcmToken.builder()
                                .user(user)
                                .token(fcmToken)
                                .build())
                );
    }

    @Transactional
    public void withdraw() {
        User user = getUser();
        user.withdraw();
    }

    private User getUser() {
        Long userId = SecurityUtil.getCurrentUserId();
        return userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }
}

