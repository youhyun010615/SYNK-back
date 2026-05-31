// 내 프로필 정보를 담음
package com.synk.dto.response;

import com.synk.entity.User;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserResponse {

    private Long userId;
    private String name;
    private String profileImage;
    private boolean missionNotification;
    private boolean resultNotification;
    private boolean highlightNotification;

    public static UserResponse from(User user) {
        return UserResponse.builder()
                .userId(user.getId())
                .name(user.getName())
                .profileImage(user.getProfileImage())
                .missionNotification(user.isMissionAlert())
                .resultNotification(user.isResultAlert())
                .highlightNotification(user.isHighlightAlert())
                .build();
    }
}



