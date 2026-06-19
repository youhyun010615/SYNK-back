package com.synk.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class RoomEventResponse {
    private String type;        // MISSION_FIRED | MEMBER_SUBMITTED
    private Long missionId;
    private String missionTitle;
    private LocalDateTime deadline;
    private Long userId;
    private Integer submittedCount;
    private Integer totalMembers;

    public static RoomEventResponse missionFired(Long missionId, String missionTitle, LocalDateTime deadline) {
        return RoomEventResponse.builder()
                .type("MISSION_FIRED")
                .missionId(missionId)
                .missionTitle(missionTitle)
                .deadline(deadline)
                .build();
    }

    public static RoomEventResponse memberSubmitted(Long missionId, Long userId, int submittedCount, int totalMembers) {
        return RoomEventResponse.builder()
                .type("MEMBER_SUBMITTED")
                .missionId(missionId)
                .userId(userId)
                .submittedCount(submittedCount)
                .totalMembers(totalMembers)
                .build();
    }
}
