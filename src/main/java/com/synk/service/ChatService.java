// 채팅 메시지 조회, 전송, 리액션 추가 로직

package com.synk.service;

import com.synk.dto.request.AddReactionRequest;
import com.synk.dto.request.SendMessageRequest;
import com.synk.dto.response.ChatMessageResponse;
import com.synk.entity.*;
import com.synk.global.exception.CustomException;
import com.synk.global.exception.ErrorCode;
import com.synk.repository.*;
import com.synk.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final RoomChatRepository roomChatRepository;
    private final ChatReactionRepository chatReactionRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public ChatMessageResponse getChats(Long roomId) {
        User user = getUser();
        Room room = getRoom(roomId);
        validateMember(user, room);

        List<RoomChat> chats = roomChatRepository.findByRoomOrderByCreatedAtAsc(room);
        int memberCount = roomMemberRepository.countByRoom(room);

        List<ChatMessageResponse.MessageInfo> messages =
                chats.stream()
                        .map(chat -> {
                            List<ChatReaction> reactions = chatReactionRepository.findByChat(chat);
                            return ChatMessageResponse.toMessageInfo(chat, user.getId(), reactions);
                        })
                        .toList();

        return ChatMessageResponse.builder()
                .roomName(room.getName())
                .memberCount(memberCount)
                .messages(messages)
                .build();
    }

    @Transactional
    public Long sendMessage(Long roomId, SendMessageRequest request) {
        User user = getUser();
        Room room = getRoom(roomId);
        validateMember(user, room);

        RoomChat chat = roomChatRepository.save(RoomChat.builder()
                        .room(room)
                        .user(user)
                        .messageType(request.getMessageType())
                        .content(request.getContent())
                        .build());

        return chat.getId();
    }

    @Transactional
    public void addReaction(Long roomId, Long messageId, AddReactionRequest request) {
        User user = getUser();
        Room room = getRoom(roomId);
        validateMember(user, room);

        RoomChat chat = roomChatRepository.findById(messageId)
                        .orElseThrow(() -> new CustomException(ErrorCode.INVALID_REQUEST));

        chatReactionRepository.save(ChatReaction.builder()
                .chat(chat)
                .user(user)
                .emoji(request.getEmoji())
                .build());
    }

    private User getUser() {
        Long userId = SecurityUtil.getCurrentUserId();
        return userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }

    private Room getRoom(Long roomId) {
        return roomRepository.findById(roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.ROOM_NOT_FOUND));
    }

    private void validateMember(User user, Room room) {
        if (!roomMemberRepository.existsByUserAndRoom(user,
                room)) {
            throw new CustomException(ErrorCode.ROOM_ACCESS_DENIED);
        }
    }
}