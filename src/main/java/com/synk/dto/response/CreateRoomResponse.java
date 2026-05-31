// 방 생성 후 방 ID와 초대 코드를 돌려준다.

package com.synk.dto.response;

import com.synk.entity.Room;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class CreateRoomResponse {

    private Long roomId;
    private String code;
    private String name;
    private String thumbnail;
    private LocalDateTime createdAt;

    public static CreateRoomResponse from(Room room) {
        return CreateRoomResponse.builder()
                .roomId(room.getId())
                .code(room.getCode())
                .name(room.getName())
                .thumbnail(room.getThumbnail())
                .createdAt(room.getCreatedAt())
                .build();
    }
}

