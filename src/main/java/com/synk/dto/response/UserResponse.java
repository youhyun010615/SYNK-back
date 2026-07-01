// 내 프로필 정보를 담음
package com.synk.dto.response;

import com.synk.entity.User;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.ZoneId;

@Getter
@Builder
public class UserResponse {

    private Long userId;
    private String name;
    private String profileImage;
    private boolean missionNotification;
    private boolean resultNotification;
    private boolean highlightNotification;
    private int daysSinceJoined;

    public static UserResponse from(User user) {
        LocalDate joinedDate = user.getCreatedAt().toLocalDate();
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        int days = (int) (today.toEpochDay() - joinedDate.toEpochDay()) + 1;

        return UserResponse.builder()
                .userId(user.getId())
                .name(user.getName())
                .profileImage(user.getProfileImage())
                .missionNotification(user.isMissionAlert())
                .resultNotification(user.isResultAlert())
                .highlightNotification(user.isHighlightAlert())
                .daysSinceJoined(days)
                .build();
    }
}



