package com.synk.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Map;

@Getter
@Builder
public class RoomEventResponse {
    private String type;

    @JsonProperty("room_id")
    private Long roomId;

    private Object payload;

    public static RoomEventResponse missionFired(Long roomId, Long missionId, String missionTitle, LocalDateTime deadline) {
        return RoomEventResponse.builder()
                .type("MISSION_FIRED")
                .roomId(roomId)
                .payload(Map.of(
                        "missionId", missionId,
                        "missionTitle", missionTitle,
                        "deadline", deadline.toString()
                ))
                .build();
    }

    public static RoomEventResponse memberSubmitted(Long roomId, Long missionId, Long userId, int submittedCount, int totalMembers) {
        return RoomEventResponse.builder()
                .type("MEMBER_SUBMITTED")
                .roomId(roomId)
                .payload(Map.of(
                        "missionId", missionId,
                        "userId", userId,
                        "submittedCount", submittedCount,
                        "totalMembers", totalMembers
                ))
                .build();
    }
}
