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
    private String code;
    private String inviteUrl;
    private String thumbnail;

    public static InviteResponse from(Room room) {
        return InviteResponse.builder()
                .roomId(room.getId())
                .roomName(room.getName())
                .code(room.getCode())
                .inviteUrl("synk.app/r/" + room.getCode())
                .thumbnail(room.getThumbnail())
                .build();
    }
}
