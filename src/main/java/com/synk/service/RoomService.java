// 방 관련 비즈니스 로직 전체
// 방 생성, 참여, 조회, 수정, 나가기 처리

package com.synk.service;

import com.synk.dto.request.CreateRoomRequest;
import com.synk.dto.request.JoinRoomRequest;
import com.synk.dto.request.UpdateRoomRequest;
import com.synk.dto.response.CreateRoomResponse;
import com.synk.dto.response.InviteResponse;
import com.synk.dto.response.JoinRoomResponse;
import com.synk.dto.response.RoomDetailResponse;
import com.synk.entity.Room;
import com.synk.entity.RoomMember;
import com.synk.entity.User;
import com.synk.global.exception.CustomException;
import com.synk.global.exception.ErrorCode;
import com.synk.repository.RoomMemberRepository;
import com.synk.repository.RoomRepository;
import com.synk.repository.UserRepository;
import com.synk.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RoomService {

    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final UserRepository userRepository;

    @Transactional
    public CreateRoomResponse createRoom(CreateRoomRequest request) {
        User user = getUser();

        String code = generateCode();

        Room room = roomRepository.save(Room.builder()
                .name(request.getName())
                .code(code)
                .thumbnail(request.getThumbnail())
                .owner(user)
                .maxMembers(request.getMaxMembers())
                .dailyMissionCount(request.getDailyMissionCount())
                .missionStartTime(request.getMissionStartTime())
                .missionEndTime(request.getMissionEndTime())
                .build());

        roomMemberRepository.save(RoomMember.builder()
                .user(user)
                .room(room)
                .isOwner(true)
                .build());

        return CreateRoomResponse.from(room);
    }

    @Transactional
    public JoinRoomResponse joinRoom(JoinRoomRequest request) {
        User user = getUser();

        Room room = roomRepository.findByCode(request.getCode())
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_INVITE_CODE));

        if (roomMemberRepository.existsByUserAndRoom(user, room)) {
            throw new CustomException(ErrorCode.ROOM_ALREADY_JOINED);
        }

        int currentMembers = roomMemberRepository.countByRoom(room);
        if (currentMembers >= room.getMaxMembers()) {
            throw new CustomException(ErrorCode.ROOM_FULL);
        }

        roomMemberRepository.save(RoomMember.builder()
                .user(user)
                .room(room)
                .isOwner(false)
                .build());

        return JoinRoomResponse.from(room);
    }

    @Transactional(readOnly = true)
    public InviteResponse getInviteInfo(Long roomId) {
        User user = getUser();
        Room room = getRoom(roomId);
        validateMember(user, room);
        return InviteResponse.from(room);
    }

    @Transactional(readOnly = true)
    public RoomDetailResponse getRoomDetail(Long roomId) {
        User user = getUser();
        Room room = getRoom(roomId);
        validateMember(user, room);
        List<RoomMember> members = roomMemberRepository.findByRoom(room);
        return RoomDetailResponse.from(room, members);
    }

    @Transactional
    public void updateRoom(Long roomId, UpdateRoomRequest request) {
        User user = getUser();
        Room room = getRoom(roomId);
        validateOwner(user, room);
        room.updateSettings(
                request.getName(),
                request.getThumbnail(),
                request.getMaxMembers(),
                request.getDailyMissionCount(),
                request.getMissionStartTime(),
                request.getMissionEndTime()
        );
    }

    @Transactional
    public void leaveRoom(Long roomId) {
        User user = getUser();
        Room room = getRoom(roomId);
        RoomMember member = roomMemberRepository.findByUserAndRoom(user, room)
                .orElseThrow(() -> new CustomException(ErrorCode.ROOM_ACCESS_DENIED));
        roomMemberRepository.delete(member);
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
        if (!roomMemberRepository.existsByUserAndRoom(user, room)) {
            throw new CustomException(ErrorCode.ROOM_ACCESS_DENIED);
        }
    }

    private void validateOwner(User user, Room room) {
        if (!room.getOwner().getId().equals(user.getId())) {
            throw new CustomException(ErrorCode.ROOM_OWNER_REQUIRED);
        }
    }

    private String generateCode() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 7).toUpperCase();
    }
}

