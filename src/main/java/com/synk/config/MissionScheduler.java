// 1. 자정 - 각 방의 당일 미션을 미리 생성 (PENDING)
// 2. 매분 - PENDING 미션 중 발동 시간 된 것을 ACTIVE로 변경 + 푸시 알림
// 3. 매분 - ACTIVE 미션 중 deadline 지난 것을 처리 (미제출자 MISSED INSERT, 상태 변경)

package com.synk.config;

import com.synk.dto.response.RoomEventResponse;
import com.synk.entity.*;
import com.synk.repository.*;
import com.synk.service.CollageService;
import com.synk.service.FcmService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;



@Slf4j
@Component
@RequiredArgsConstructor
public class MissionScheduler {

    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final MissionRepository missionRepository;
    private final MissionTemplateRepository missionTemplateRepository;
    private final MissionTimeSlotRepository missionTimeSlotRepository;
    private final SubmissionRepository submissionRepository;
    private final CollectionRecordRepository collectionRecordRepository;
    private final CollageRepository collageRepository;
    private final SynklogRepository synklogRepository;
    private final FcmService fcmService;
    private final CollageService collageService;
    private final SimpMessagingTemplate messagingTemplate;

    // 같은 날 미션끼리 최소 이 간격(분) 이상 떨어지도록 보장 (deadline 5분 + 여유)
    private static final long MIN_SLOT_GAP_MINUTES = 30;

    // 자정에 당일 미션 생성 (KST 기준)
    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
    @Transactional
    public void createDailyMissions() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        for (Room room : roomRepository.findAll()) {
            if (!isFull(room)) continue; // 풀방이 아니면 미션 생성 안 함
            generateDailyMissionsForRoom(room, false, today); // 자정: 모든 시간대 슬롯 대상
        }
        log.info("자정 당일 미션 생성 완료: {}", today);
    }

    // 자가복구①: 서버가 자정 배치를 놓친 경우(크래시/재시작 등) 시작 시 당일 미션을 생성한다.
    // 오늘 미션이 하나라도 있으면(=자정 배치 정상) 건너뛴다.
    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    @Transactional
    public void catchUpMissionsOnStartup() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        if (missionRepository.existsByDate(today)) return;
        log.warn("서버 시작 — 오늘({}) 미션 없음, 자가복구로 당일 미션 생성 (남은 시간대 슬롯만)", today);
        for (Room room : roomRepository.findAll()) {
            if (!isFull(room)) continue;
            generateDailyMissionsForRoom(room, true, today); // 지난 시간대 슬롯 제외
        }
    }

    // 자가복구②: "풀방이 됐지만 오늘 미션이 0개"인 방을 주기적으로 찾아 남은 시간대 미션을 생성한다.
    // (자정엔 정원 미달이었다가 낮에 멤버가 들어와 풀방이 된 방 → 다음 자정까지 미션이 안 생기던 공백을 메움)
    // 최초 1회는 기동 30초 뒤, 이후 10분마다. 이미 오늘 미션이 있는 방은 건드리지 않아 중복 생성 없음.
    @Scheduled(initialDelay = 30000, fixedDelay = 600000)
    @Transactional
    public void backfillFullRoomsWithoutMissions() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        LocalTime now = LocalTime.now(ZoneId.of("Asia/Seoul"));
        if (now.getHour() >= 23) return; // 23시 이후면 남은 슬롯이 거의 없으니 다음날 자정 배치에 맡김

        for (Room room : roomRepository.findAll()) {
            if (!isFull(room)) continue;
            // 오늘 미션 시간대가 이미 끝난 방은 복구해도 생성할 슬롯이 없으므로 skip (불필요한 로그/조회 방지)
            if (room.getMissionEndTime() != null && !room.getMissionEndTime().isAfter(now)) continue;
            if (!missionRepository.findByRoomAndDate(room, today).isEmpty()) continue; // 오늘 미션 있으면 skip
            log.warn("풀방인데 오늘 미션 0개 감지 — 자가복구: roomId={}, name={}", room.getId(), room.getName());
            generateDailyMissionsForRoom(room, true, today); // 남은 시간대 슬롯만
        }
    }

    private boolean isFull(Room room) {
        return roomMemberRepository.countByRoom(room) >= room.getMaxMembers();
    }

    // 한 방의 당일 미션을 생성한다. skipPastSlots=true면 이미 지난 시간대 슬롯은 제외(발동 불가하므로).
    private void generateDailyMissionsForRoom(Room room, boolean skipPastSlots, LocalDate today) {
        LocalTime now = LocalTime.now(ZoneId.of("Asia/Seoul"));
        List<MissionTemplate> templates = missionTemplateRepository.findByDescriptionNot("튜토리얼");
        List<MissionTimeSlot> allSlots = missionTimeSlotRepository.findAll();
        if (templates.isEmpty()) {
            log.warn("미션 템플릿 없음 — 생성 취소: roomId={}", room.getId());
            return;
        }

        List<MissionTimeSlot> availableSlots =
                allSlots.stream()
                        .filter(slot ->
                                !slot.getSlotTime().isBefore(room.getMissionStartTime())
                                        &&
                                        !slot.getSlotTime().isAfter(room.getMissionEndTime())
                                        // 자가복구 시 이미 지난 시간대는 발동 못 하므로 제외
                                        && (!skipPastSlots || slot.getSlotTime().isAfter(now)))
                        .collect(java.util.stream.Collectors.toList());

        Collections.shuffle(availableSlots);
        Collections.shuffle(templates);

        int count = Math.min(room.getDailyMissionCount(), availableSlots.size());

        List<MissionTimeSlot> chosenSlots = new ArrayList<>();
        for (MissionTimeSlot slot : availableSlots) {
            if (chosenSlots.size() >= count) break;
            boolean tooClose = chosenSlots.stream().anyMatch(chosen ->
                    Math.abs(java.time.Duration.between(chosen.getSlotTime(), slot.getSlotTime()).toMinutes())
                            < MIN_SLOT_GAP_MINUTES);
            if (tooClose) continue;
            chosenSlots.add(slot);
        }

        int created = 0;
        for (int i = 0; i < chosenSlots.size(); i++) {
            MissionTimeSlot slot = chosenSlots.get(i);
            if (missionRepository.existsByRoomAndDateAndTimeSlot(room, today, slot)) continue;
            missionRepository.save(Mission.builder()
                    .room(room)
                    .missionTemplate(templates.get(i % templates.size()))
                    .timeSlot(slot)
                    .date(today)
                    .build());
            created++;
        }
        log.info("당일 미션 생성: roomId={}, name={}, 생성={}개 (자가복구={})",
                room.getId(), room.getName(), created, skipPastSlots);
    }

    // 매분 PENDING → ACTIVE
    @Scheduled(fixedDelay = 60000) // 60초
    @Transactional
    public void activateMissions() {
        ZoneId kst = ZoneId.of("Asia/Seoul");
        LocalTime now = LocalTime.now(kst).withSecond(0).withNano(0);
        LocalDate today = LocalDate.now(kst);
        List<Mission> pendingMissions = missionRepository.findByStatus(Mission.MissionStatus.PENDING);

        for (Mission mission : pendingMissions) {
            if (mission.getDate().equals(today)
                    &&
                    mission.getTimeSlot().getSlotTime().equals(now)) {

                // 대기중(waiting) 방 — 풀방이 아니면 미션 발동·푸시 안 함 (멤버가 나가 정원 미달인 경우)
                int currentMembers = roomMemberRepository.countByRoom(mission.getRoom());
                if (currentMembers < mission.getRoom().getMaxMembers()) {
                    log.info("미션 발동 스킵(대기중 방): missionId={}, 인원={}/{}",
                            mission.getId(), currentMembers, mission.getRoom().getMaxMembers());
                    continue;
                }

                mission.activate();
                log.info("미션 활성화: missionId={}", mission.getId());

                String missionName = mission.getMissionTemplate().getTitle();
                List<RoomMember> members = roomMemberRepository.findByRoom(mission.getRoom());
                for (RoomMember member : members) {
                    User user = member.getUser();
                    if (user.isMissionAlert()) {
                        String roomName = mission.getRoom().getName();
                        fcmService.sendAndSave(
                                user,
                                Notification.NotificationType.MISSION_START,
                                "🚨 " + roomName + " 미션 떴다!",
                                "🔥 " + missionName + " · 지금 바로 찍어요!",
                                mission.getId()
                        );
                    }
                }

                // WebSocket MISSION_FIRED 이벤트 push
                messagingTemplate.convertAndSend(
                        "/topic/rooms/" + mission.getRoom().getId(),
                        RoomEventResponse.missionFired(mission.getRoom().getId(), mission.getId(), missionName, mission.getDeadline())
                );
            }
        }
    }

    // 매분 deadline 지난 ACTIVE 미션 처리
    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void processExpiredMissions() {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
        List<Mission> activeMissions = missionRepository.findByStatus(Mission.MissionStatus.ACTIVE);

        for (Mission mission : activeMissions) {
            if (now.isAfter(mission.getDeadline())) {
                List<RoomMember> members = roomMemberRepository.findByRoom(mission.getRoom());
                List<Submission> submissions = submissionRepository.findByMission(mission);

                List<Long> submittedUserIds =
                        submissions.stream()
                                .map(s -> s.getUser().getId())
                                .toList();

                for (RoomMember member : members) {
                    if (!submittedUserIds.contains(member.getUser().getId())) {
                        submissionRepository.save(Submission.builder()
                                .user(member.getUser())
                                .room(mission.getRoom())
                                .mission(mission)
                                .videoUrl(null)
                                .status(Submission.SubmissionStatus.MISSED)
                                .build());
                    }
                }

                // SUBMITTED 유저 도감 기록 생성
                String collageThumbnail = collageRepository.findByMission(mission)
                        .map(Collage::getThumbnail).orElse(null);
                for (Submission submission : submissions) {
                    if (submission.getStatus() == Submission.SubmissionStatus.SUBMITTED
                            && !collectionRecordRepository.existsBySubmission(submission)) {
                        collectionRecordRepository.save(CollectionRecord.builder()
                                .user(submission.getUser())
                                .missionTemplate(mission.getMissionTemplate())
                                .room(mission.getRoom())
                                .submission(submission)
                                .date(mission.getDate())
                                .thumbnail(collageThumbnail)
                                .build());
                        log.info("도감 기록 생성: userId={}, missionTemplateId={}",
                                submission.getUser().getId(), mission.getMissionTemplate().getId());
                    }
                }

                mission.complete();
                log.info("미션 완료 처리: missionId={}", mission.getId());

                // Synklog 자동 생성
                if (synklogRepository.findByRoomAndDate(mission.getRoom(), mission.getDate()).isEmpty()) {
                    synklogRepository.save(Synklog.builder()
                            .room(mission.getRoom())
                            .date(mission.getDate())
                            .build());
                }

                // 콜라주 미생성이면 항상 트리거 — 전원 미제출이어도 Lambda가 missed 타일 영상 생성
                boolean collageExists = collageRepository.findByMission(mission).isPresent();
                if (!collageExists) {
                    collageService.triggerCollageIfReady(mission);
                }
            }
        }
    }
}
