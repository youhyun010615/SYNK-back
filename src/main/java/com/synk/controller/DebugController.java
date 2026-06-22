package com.synk.controller;

import com.synk.dto.response.AlbumResponse;
import com.synk.entity.Mission;
import com.synk.entity.MissionTemplate;
import com.synk.entity.MissionTimeSlot;
import com.synk.entity.Room;
import com.synk.global.exception.CustomException;
import com.synk.global.exception.ErrorCode;
import com.synk.global.response.ApiResponse;
import com.synk.repository.CollageRepository;
import com.synk.repository.MissionRepository;
import com.synk.repository.MissionTemplateRepository;
import com.synk.repository.MissionTimeSlotRepository;
import com.synk.repository.RoomMemberRepository;
import com.synk.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

    /** 개발용: 인증 없이 특정 방의 앨범 목록 조회 */
    @GetMapping("/rooms/{roomId}/albums")
    public ResponseEntity<ApiResponse<List<AlbumResponse>>> debugAlbums(@PathVariable Long roomId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.ROOM_NOT_FOUND));

        List<Mission> missions = missionRepository.findByRoom(room);
        Map<LocalDate, List<Mission>> missionsByDate = missions.stream()
                .collect(Collectors.groupingBy(Mission::getDate));

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy.MM.dd");
        List<AlbumResponse> result = missionsByDate.entrySet().stream()
                .sorted(Map.Entry.<LocalDate, List<Mission>>comparingByKey().reversed())
                .map(entry -> {
                    var collage = entry.getValue().stream()
                            .flatMap(m -> collageRepository.findByMission(m).stream())
                            .filter(c -> c.getThumbnail() != null)
                            .findFirst()
                            .orElse(null);
                    var profiles = roomMemberRepository.findByRoom(room).stream()
                            .map(rm -> AlbumResponse.MemberProfile.builder()
                                    .userId(rm.getUser().getId())
                                    .profileImage(rm.getUser().getProfileImage())
                                    .build())
                            .toList();
                    return AlbumResponse.from(entry.getKey().format(fmt), collage, profiles);
                }).toList();

        return ResponseEntity.ok(ApiResponse.success(result, "디버그 앨범 조회"));
    }

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

        return ResponseEntity.ok(ApiResponse.success(mission.getId(), "미션 강제 트리거 완료"));
    }
}
