// 방 관련 비즈니스 로직 전체
// 방 생성, 참여, 조회, 수정, 나가기 처리

package com.synk.service;

import com.synk.entity.Notification;
import com.synk.service.FcmService;
import com.synk.dto.request.CreateRoomRequest;
import com.synk.dto.request.JoinRoomRequest;
import com.synk.dto.request.UpdateRoomRequest;
import com.synk.dto.response.CreateRoomResponse;
import com.synk.dto.response.InviteResponse;
import com.synk.dto.response.JoinRoomResponse;
import com.synk.dto.response.RoomDetailResponse;
import com.synk.dto.response.RoomMemberResponse;
import com.synk.entity.Room;
import com.synk.entity.RoomMember;
import com.synk.entity.User;
import com.synk.global.exception.CustomException;
import com.synk.global.exception.ErrorCode;
import com.synk.repository.MissionRepository;
import com.synk.repository.MissionTemplateRepository;
import com.synk.repository.MissionTimeSlotRepository;
import com.synk.repository.RoomMemberRepository;
import com.synk.repository.RoomRepository;
import com.synk.repository.UserRepository;
import com.synk.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.synk.dto.response.MyRoomsResponse;
import com.synk.entity.Mission;
import com.synk.entity.MissionTemplate;
import com.synk.entity.MissionTimeSlot;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RoomService {

    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final UserRepository userRepository;
    private final MissionRepository missionRepository;
    private final MissionTemplateRepository missionTemplateRepository;
    private final MissionTimeSlotRepository missionTimeSlotRepository;
    private final FcmService fcmService;

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
    public void kickMember(Long roomId, Long targetUserId) {
        User user = getUser();
        Room room = getRoom(roomId);
        validateOwner(user, room);
        User targetUser = userRepository.findById(targetUserId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        RoomMember member = roomMemberRepository.findByUserAndRoom(targetUser, room)
                .orElseThrow(() -> new CustomException(ErrorCode.ROOM_ACCESS_DENIED));
        roomMemberRepository.delete(member);
    }

    @Transactional(readOnly = true)
    public List<RoomMemberResponse> getRoomMembers(Long roomId) {
        User user = getUser();
        Room room = getRoom(roomId);
        validateMember(user, room);
        return roomMemberRepository.findByRoom(room).stream()
                .map(RoomMemberResponse::from)
                .toList();
    }

    @Transactional
    public RoomDetailResponse updateRoom(Long roomId, UpdateRoomRequest request) {
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
        List<RoomMember> members = roomMemberRepository.findByRoom(room);
        return RoomDetailResponse.from(room, members);
    }

    @Transactional
    public void leaveRoom(Long roomId) {
        User user = getUser();
        Room room = getRoom(roomId);
        RoomMember member = roomMemberRepository.findByUserAndRoom(user, room)
                .orElseThrow(() -> new CustomException(ErrorCode.ROOM_ACCESS_DENIED));
        roomMemberRepository.delete(member);
    }

    @Transactional
    public void deleteRoom(Long roomId) {
        User user = getUser();
        Room room = getRoom(roomId);
        validateOwner(user, room);
        roomMemberRepository.deleteAllByRoom(room);
        roomRepository.delete(room);
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

    @Transactional(readOnly = true)
    public MyRoomsResponse getMyRooms() {
        User user = getUser();
        List<RoomMember> myRooms =
                roomMemberRepository.findByUser(user);

        List<MyRoomsResponse.ActiveRoom> active = new
                java.util.ArrayList<>();
        List<MyRoomsResponse.WaitingRoom> waiting = new
                java.util.ArrayList<>();

        for (RoomMember rm : myRooms) {
            Room room = rm.getRoom();
            int currentMembers =
                    roomMemberRepository.countByRoom(room);

            List<MyRoomsResponse.MemberProfile> profiles =
                    roomMemberRepository.findByRoom(room)
                            .stream()
                            .map(m ->
                                    MyRoomsResponse.MemberProfile.builder()
                                            .userId(m.getUser().getId())

                                            .profileImage(m.getUser().getProfileImage())
                                            .build())
                            .toList();

            if (currentMembers >= room.getMaxMembers()) {
                List<Mission> missions = missionRepository.findByRoomAndDate(room,
                                java.time.LocalDate.now());
                int completed = (int) missions.stream()
                        .filter(m -> m.getStatus() ==
                                Mission.MissionStatus.COMPLETED)
                        .count();

                active.add(MyRoomsResponse.ActiveRoom.builder()
                        .id(room.getId())
                        .name(room.getName())
                        .totalMissions(missions.size())
                        .completedMissions(completed)
                        .isAllCompleted(completed ==
                                missions.size() && !missions.isEmpty())
                        .roomThumbnail(room.getThumbnail())
                        .memberProfiles(profiles)
                        .build());
            } else {

                waiting.add(MyRoomsResponse.WaitingRoom.builder()
                        .id(room.getId())
                        .name(room.getName())
                        .currentMembers(currentMembers)
                        .maxMembers(room.getMaxMembers())
                        .waitingCount(room.getMaxMembers() - currentMembers)
                        .roomThumbnail(room.getThumbnail())
                        .memberProfiles(profiles)
                        .build());
            }
        }

        return MyRoomsResponse.builder()
                .active(active)
                .waiting(waiting)
                .build();
    }


    @Transactional
    public Long sendTestNotification(Long roomId) {
        Room room = getRoom(roomId);

        // 기존 테스트 미션(ACTIVE) 있으면 재사용
        List<Mission> existing = missionRepository.findByRoomAndStatus(room, Mission.MissionStatus.ACTIVE);
        Mission mission;
        if (!existing.isEmpty()) {
            mission = existing.get(0);
        } else {
            MissionTemplate template = missionTemplateRepository.findAll().get(0);

            // 현재 KST 시간에 맞는 슬롯을 찾거나 새로 생성 (deadline = 지금 + 5분 보장)
            LocalTime nowKst = LocalTime.now(ZoneId.of("Asia/Seoul")).withSecond(0).withNano(0);
            MissionTimeSlot slot = missionTimeSlotRepository.findBySlotTime(nowKst)
                    .orElseGet(() -> missionTimeSlotRepository.save(
                            MissionTimeSlot.builder().slotTime(nowKst).build()));

            // 같은 날 같은 방 같은 슬롯 unique 제약 충돌 방지: 날짜를 내일로
            LocalDate missionDate = missionRepository.existsByRoomAndDateAndTimeSlot(room, LocalDate.now(), slot)
                    ? LocalDate.now().plusDays(1)
                    : LocalDate.now();

            mission = missionRepository.save(Mission.builder()
                    .room(room)
                    .missionTemplate(template)
                    .timeSlot(slot)
                    .date(missionDate)
                    .build());
            mission.activate();
            missionRepository.save(mission);
        }

        List<RoomMember> members = roomMemberRepository.findByRoom(room);
        for (RoomMember member : members) {
            fcmService.sendAndSave(
                    member.getUser(),
                    Notification.NotificationType.MISSION_START,
                    "미션 시작!",
                    room.getName() + " 방에 미션이 시작됐어요!",
                    mission.getId()
            );
        }
        return mission.getId();
    }

    private String generateCode() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 5).toUpperCase();
    }
}

