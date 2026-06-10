package com.synk.dto.response;

import com.synk.entity.RoomMember;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class RoomMemberResponse {

    private Long userId;
    private String name;
    private String profileImage;
    @JsonProperty("isOwner")
    private boolean isOwner;
    private LocalDateTime joinedAt;

    public static RoomMemberResponse from(RoomMember member) {
        return RoomMemberResponse.builder()
                .userId(member.getUser().getId())
                .name(member.getUser().getName())
                .profileImage(member.getUser().getProfileImage())
                .isOwner(member.isOwner())
                .joinedAt(member.getJoinedAt())
                .build();
    }
}