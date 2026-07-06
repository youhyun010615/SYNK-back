package com.synk.controller;

import com.synk.entity.Mission;
import com.synk.entity.MissionTemplate;
import com.synk.entity.MissionTimeSlot;
import com.synk.entity.Notification;
import com.synk.entity.Room;
import com.synk.entity.RoomMember;
import com.synk.global.exception.CustomException;
import com.synk.global.exception.ErrorCode;
import com.synk.global.response.ApiResponse;
import com.synk.entity.User;
import com.synk.entity.UserFcmToken;
import com.synk.repository.CollageRepository;
import com.synk.repository.MissionRepository;
import com.synk.repository.MissionTemplateRepository;
import com.synk.repository.MissionTimeSlotRepository;
import com.synk.repository.RoomMemberRepository;
import com.synk.repository.RoomRepository;
import com.synk.repository.UserFcmTokenRepository;
import com.synk.repository.UserRepository;
import com.synk.service.FcmService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/debug")
@RequiredArgsConstructor
public class DebugController {

    private final RoomRepository roomRepository;
    private final MissionRepository missionRepository;
    private final MissionTemplateRepository missionTemplateRepository;
    private final MissionTimeSlotRepository missionTimeSlotRepository;
    private final CollageRepository collageRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final FcmService fcmService;
    private final UserRepository userRepository;
    private final UserFcmTokenRepository userFcmTokenRepository;

    /** 개발용: 특정 방에 미션을 즉시 생성하고 ACTIVE 상태로 만든다. */
    @PostMapping("/rooms/{roomId}/trigger-mission")
    public ResponseEntity<ApiResponse<Long>> triggerMission(@PathVariable Long roomId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.ROOM_NOT_FOUND));

        // 이미 ACTIVE 미션 있으면 그 id 반환
        missionRepository.findByRoomAndStatus(room, Mission.MissionStatus.ACTIVE)
                .stream().findFirst().ifPresent(m -> {
                    throw new CustomException(ErrorCode.INVALID_REQUEST);
                });

        List<MissionTemplate> templates = missionTemplateRepository.findAll();
        Collections.shuffle(templates);
        MissionTemplate template = templates.get(0);

        LocalTime now = LocalTime.now().withSecond(0).withNano(0);
        MissionTimeSlot slot = missionTimeSlotRepository.findBySlotTime(now)
                .orElseGet(() -> missionTimeSlotRepository.save(
                        MissionTimeSlot.builder().slotTime(now).build()));

        LocalDate today = LocalDate.now();
        if (missionRepository.existsByRoomAndDateAndTimeSlot(room, today, slot)) {
            today = today.plusDays(1);
        }

        Mission mission = missionRepository.save(Mission.builder()
                .room(room)
                .missionTemplate(template)
                .timeSlot(slot)
                .date(today)
                .build());
        mission.activate();
        missionRepository.save(mission);

        String missionName = template.getTitle();
        List<RoomMember> members = roomMemberRepository.findByRoom(room);
        for (RoomMember member : members) {
            fcmService.sendAndSave(
                    member.getUser(),
                    Notification.NotificationType.MISSION_START,
                    "🚨 " + room.getName() + " 미션 떴다!",
                    "🔥 " + missionName + " · 지금 바로 찍어요!",
                    mission.getId()
            );
        }

        return ResponseEntity.ok(ApiResponse.success(mission.getId(), "미션 강제 트리거 완료"));
    }

    /** 개발용: userId + fcmToken 직접 등록 */
    @PostMapping("/users/{userId}/fcm-token")
    public ResponseEntity<ApiResponse<Void>> registerFcmToken(
            @PathVariable Long userId,
            @RequestBody java.util.Map<String, String> body) {
        String token = body.get("fcmToken");
        if (token == null || token.isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.failure("fcmToken 필요"));
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        userFcmTokenRepository.findByToken(token)
                .orElseGet(() -> userFcmTokenRepository.save(
                        UserFcmToken.builder().user(user).token(token).build()));
        return ResponseEntity.ok(ApiResponse.success(null, "FCM 토큰 등록 완료"));
    }
}
