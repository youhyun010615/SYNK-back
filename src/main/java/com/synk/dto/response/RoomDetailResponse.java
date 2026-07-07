// 방 상세 정보와 맴버 목록을 담는다.

package com.synk.dto.response;

import com.synk.entity.Room;
import com.synk.entity.RoomMember;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Getter
@Builder
public class RoomDetailResponse {

    private Long id;
    private String name;
    private String code;
    private String thumbnail;
    private Long ownerId;
    private int currentMembers;
    private int maxMembers;
    private int dailyMissionCount;
    private LocalTime missionStartTime;
    private LocalTime missionEndTime;
    private LocalDateTime createdAt;   // 방 생성 시각 (ISO 8601, 튜토리얼 안내 문구 분기용)
    private List<MemberInfo> members;

    @Getter
    @Builder
    public static class MemberInfo {
        private Long userId;
        private String name;
        private String profileImage;
    }

    public static RoomDetailResponse from(Room room, List<RoomMember> members) {
        List<MemberInfo> memberInfos = members.stream()
                .map(m -> MemberInfo.builder()
                        .userId(m.getUser().getId())
                        .name(m.getUser().getName())
                        .profileImage(m.getUser().getProfileImage())
                        .build())
                .toList();

        return RoomDetailResponse.builder()
                .id(room.getId())
                .name(room.getName())
                .code(room.getCode())
                .thumbnail(room.getThumbnail())
                .ownerId(room.getOwner().getId())
                .currentMembers(members.size())
                .maxMembers(room.getMaxMembers())
                .dailyMissionCount(room.getDailyMissionCount())
                .missionStartTime(room.getMissionStartTime())
                .missionEndTime(room.getMissionEndTime())
                .createdAt(room.getCreatedAt())
                .members(memberInfos)
                .build();
    }
}

