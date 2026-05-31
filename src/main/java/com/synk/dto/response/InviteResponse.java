// 초대 코드와 초대 URL을 담는다

package com.synk.dto.response;

import com.synk.entity.Room;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class InviteResponse {

    private Long roomId;
    private String roomName;
    private int maxMembers;

    public static JoinRoomResponse from(Room room, int currentMembers) {
        return JoinRoomResponse.builder()
                .roomId(room.getId())
                .roomName(room.getName())
                .maxMembers(room.getMaxMembers())
                .build();
    }
}
