package com.synk.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ChatSocketResponse {
    private Long messageId;
    private Long userId;
    private String userName;
    private String profileImage;
    private String messageType;
    private String content;
    private LocalDateTime createdAt;
}
