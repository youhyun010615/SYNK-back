// 채팅 메시지 전송 타입과 내용을 담는다.

package com.synk.dto.request;

import com.synk.entity.RoomChat;
import lombok.Getter;

@Getter
public class SendMessageRequest {

    private RoomChat.MessageType messageType;
    private String content;
}
