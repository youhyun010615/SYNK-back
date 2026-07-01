// 내가 참여한 방을 활성(미션 진행)/대기(모집 중)로 구분해서 담는다.

package com.synk.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class MyRoomsResponse {

    private List<ActiveRoom> active;
    private List<WaitingRoom> waiting;

    @Getter
    @Builder
    public static class ActiveRoom {
        private Long id;
        private String name;
        private int totalMissions;
        private int completedMissions;
        private int dailyMissionCount;
        @JsonProperty("isAllCompleted")
        private boolean isAllCompleted;
        private String roomThumbnail;
        private List<MemberProfile> memberProfiles;
        private Long lastMessageId;
        private boolean hasUnreadChat;
    }

    @Getter
    @Builder
    public static class WaitingRoom {
        private Long id;
        private String name;
        private int currentMembers;
        private int maxMembers;
        private int waitingCount;
        private String roomThumbnail;
        private List<MemberProfile> memberProfiles;
    }

    @Getter
    @Builder
    public static class MemberProfile {
        private Long userId;
        private String name;
        private String profileImage;
    }
}
