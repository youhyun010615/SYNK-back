// 방 참여 성공 시 방 기본 정보를 돌려준다

package com.synk.dto.response;

import com.synk.entity.Room;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class JoinRoomResponse {

    private Long roomId;
    private String roomName;
    private int maxMembers;

    public static JoinRoomResponse from(Room room) {
        return JoinRoomResponse.builder()
                .roomId(room.getId())
                .roomName(room.getName())
                .maxMembers(room.getMaxMembers())
                .build();
    }
}

