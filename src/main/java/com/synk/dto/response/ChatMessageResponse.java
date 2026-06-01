// 채팅 메시지 목록과 리액션을 담는다.


package com.synk.dto.response;

import com.synk.entity.ChatReaction;
import com.synk.entity.RoomChat;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Getter
@Builder
public class ChatMessageResponse {

    private String roomName;
    private int memberCount;
    private List<MessageInfo> messages;

    @Getter
    @Builder
    public static class MessageInfo {
        private Long messageId;
        private Long userId;
        private String userName;
        private String profileImage;
        private String messageType;
        private String content;
        private LocalDateTime createdAt;
        private boolean isMyMessage;
        private List<ReactionInfo> reactions;
    }

    @Getter
    @Builder
    public static class ReactionInfo {
        private String emoji;
        private long count;
    }

    public static MessageInfo toMessageInfo(RoomChat chat, Long currentUserId, List<ChatReaction> reactions) {
        Map<String, Long> reactionMap = reactions.stream()
                .collect(Collectors.groupingBy(ChatReaction::getEmoji, Collectors.counting()));

        List<ReactionInfo> reactionInfos = reactionMap.entrySet().stream()
                        .map(e -> ReactionInfo.builder()
                                .emoji(e.getKey())
                                .count(e.getValue())
                                .build())
                        .toList();

        return MessageInfo.builder()
                .messageId(chat.getId())
                .userId(chat.getUser().getId())
                .userName(chat.getUser().getName())
                .profileImage(chat.getUser().getProfileImage())
                .messageType(chat.getMessageType().name())
                .content(chat.getContent())
                .createdAt(chat.getCreatedAt())
                .isMyMessage(chat.getUser().getId().equals(currentUserId))
                .reactions(reactionInfos.isEmpty() ? null : reactionInfos)
                .build();
    }
}
