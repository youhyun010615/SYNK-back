// 채팅 메시지 조회, 전송, 리액션 추가 로직

package com.synk.service;

import com.synk.dto.request.AddReactionRequest;
import com.synk.dto.request.SendMessageRequest;
import com.synk.dto.response.ChatMessageResponse;
import com.synk.dto.response.ChatSocketResponse;
import com.synk.entity.*;
import com.synk.global.exception.CustomException;
import com.synk.global.exception.ErrorCode;
import com.synk.repository.*;
import com.synk.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final RoomChatRepository roomChatRepository;
    private final ChatReactionRepository chatReactionRepository;
    private final UserRepository userRepository;
    private final MissionRepository missionRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final FcmService fcmService;

    @Transactional
    public ChatMessageResponse getChats(Long roomId) {
        User user = getUser();
        Room room = getRoom(roomId);
        RoomMember member = getMember(user, room);

        List<RoomChat> chats = roomChatRepository.findByRoomOrderByCreatedAtAsc(room);
        int memberCount = roomMemberRepository.countByRoom(room);

        // 채팅 목록을 조회하면 그 시점의 최신 메시지까지 읽은 것으로 처리 (dot 해제용)
        if (!chats.isEmpty()) {
            member.markRead(chats.get(chats.size() - 1).getId());
        }

        List<ChatMessageResponse.MessageInfo> messages =
                chats.stream()
                        .map(chat -> {
                            List<ChatReaction> reactions = chatReactionRepository.findByChat(chat);
                            return ChatMessageResponse.toMessageInfo(chat, user.getId(), reactions);
                        })
                        .toList();

        LocalDate today = LocalDate.now();
        boolean todayMissionCompleted = missionRepository.findByRoomAndDate(room, today)
                .stream()
                .anyMatch(m -> m.getStatus() == Mission.MissionStatus.COMPLETED);

        return ChatMessageResponse.builder()
                .roomName(room.getName())
                .memberCount(memberCount)
                .todayMissionCompleted(todayMissionCompleted)
                .todayMissionDate(today.toString())
                .messages(messages)
                .build();
    }

    @Transactional
    public ChatSocketResponse sendMessage(Long roomId, SendMessageRequest request) {
        User user = getUser();
        Room room = getRoom(roomId);
        RoomMember member = getMember(user, room);

        RoomChat.MessageType messageType = request.getMessageType() != null
                ? request.getMessageType()
                : RoomChat.MessageType.TEXT;

        RoomChat chat = roomChatRepository.save(RoomChat.builder()
                        .room(room)
                        .user(user)
                        .messageType(messageType)
                        .content(request.getContent())
                        .build());

        // 본인이 보낸 메시지는 본인 기준으로는 이미 읽은 것 (dot 안 뜨도록)
        member.markRead(chat.getId());

        ChatSocketResponse response = ChatSocketResponse.builder()
                .messageId(chat.getId())
                .userId(user.getId())
                .userName(user.getName())
                .profileImage(user.getProfileImage())
                .messageType(messageType.name())
                .content(chat.getContent())
                .createdAt(chat.getCreatedAt())
                .build();

        // REST로 보낸 메시지도 WebSocket으로 방 전체에 실시간 브로드캐스트
        messagingTemplate.convertAndSend("/topic/rooms/" + roomId, response);

        // 다른 멤버에게 FCM 푸시 알림 (포그라운드 대응: data 페이로드 포함)
        String truncatedContent = chat.getContent() != null && chat.getContent().length() > 50
                ? chat.getContent().substring(0, 50) + "…"
                : chat.getContent();
        String fcmTitle = room.getName();
        String fcmBody = user.getName() + ": " + truncatedContent;
        Map<String, String> data = Map.of("type", "CHAT", "roomId", String.valueOf(roomId));

        roomMemberRepository.findByRoom(room).stream()
                .filter(m -> !m.getUser().getId().equals(user.getId()))
                .forEach(m -> fcmService.sendDataMessage(
                        m.getUser(),
                        Notification.NotificationType.CHAT,
                        fcmTitle, fcmBody,
                        chat.getId(),
                        data
                ));

        return response;
    }

    @Transactional
    public ChatSocketResponse sendMessageViaSocket(Long roomId, Long userId, SendMessageRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        Room room = getRoom(roomId);
        RoomMember member = getMember(user, room);

        RoomChat.MessageType messageType = request.getMessageType() != null
                ? request.getMessageType()
                : RoomChat.MessageType.TEXT;

        RoomChat chat = roomChatRepository.save(RoomChat.builder()
                .room(room)
                .user(user)
                .messageType(messageType)
                .content(request.getContent())
                .build());

        member.markRead(chat.getId());

        return ChatSocketResponse.builder()
                .messageId(chat.getId())
                .userId(user.getId())
                .userName(user.getName())
                .profileImage(user.getProfileImage())
                .messageType(messageType.name())
                .content(chat.getContent())
                .createdAt(chat.getCreatedAt())
                .build();
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

    private RoomMember getMember(User user, Room room) {
        return roomMemberRepository.findByUserAndRoom(user, room)
                .orElseThrow(() -> new CustomException(ErrorCode.ROOM_ACCESS_DENIED));
    }
}