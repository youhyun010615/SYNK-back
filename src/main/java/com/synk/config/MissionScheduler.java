// 1. 자정 - 각 방의 당일 미션을 미리 생성 (PENDING)
// 2. 매분 - PENDING 미션 중 발동 시간 된 것을 ACTIVE로 변경 + 푸시 알림
// 3. 매분 - ACTIVE 미션 중 deadline 지난 것을 처리 (미제출자 MISSED INSERT, 상태 변경)

package com.synk.config;

import com.synk.entity.*;
import com.synk.repository.*;
import com.synk.service.FcmService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import
        org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
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
    private final FcmService fcmService;

    // 자정에 당일 미션 생성 (KST 기준)
    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
    @Transactional
    public void createDailyMissions() {
        LocalDate today = LocalDate.now();
        List<Room> rooms = roomRepository.findAll();
        List<MissionTemplate> templates = missionTemplateRepository.findAll();
        List<MissionTimeSlot> allSlots = missionTimeSlotRepository.findAll();

        for (Room room : rooms) {
            int currentMembers = roomMemberRepository.countByRoom(room);
            if (currentMembers < room.getMaxMembers()) continue; // 풀방이 아니면 미션 생성 안 함

            List<MissionTimeSlot> availableSlots =
                    allSlots.stream()
                            .filter(slot ->
                                    !slot.getSlotTime().isBefore(room.getMissionStartTime())
                                            &&
                                            !slot.getSlotTime().isAfter(room.getMissionEndTime()))
                            .collect(java.util.stream.Collectors.toList());

            Collections.shuffle(availableSlots);
            Collections.shuffle(templates);

            int count = Math.min(room.getDailyMissionCount(), availableSlots.size());

            for (int i = 0; i < count; i++) {
                missionRepository.save(Mission.builder()
                        .room(room)
                        .missionTemplate(templates.get(i % templates.size()))
                        .timeSlot(availableSlots.get(i))
                        .date(today)
                        .build());
            }
        }
        log.info("당일 미션 생성 완료: {}", today);
    }

    // 매분 PENDING → ACTIVE
    @Scheduled(fixedDelay = 60000) // 60초
    @Transactional
    public void activateMissions() {
        LocalTime now = LocalTime.now().withSecond(0).withNano(0);
        List<Mission> pendingMissions = missionRepository.findByStatus(Mission.MissionStatus.PENDING);

        for (Mission mission : pendingMissions) {
            if (mission.getDate().equals(LocalDate.now())
                    &&
                    mission.getTimeSlot().getSlotTime().equals(now)) {
                mission.activate();
                log.info("미션 활성화: missionId={}", mission.getId());

                String missionName = mission.getMissionTemplate().getName();
                List<RoomMember> members = roomMemberRepository.findByRoom(mission.getRoom());
                for (RoomMember member : members) {
                    User user = member.getUser();
                    if (user.isMissionAlert()) {
                        fcmService.sendAndSave(
                                user,
                                Notification.NotificationType.MISSION_START,
                                "미션 시작!",
                                "지금 바로 미션을 수행하세요: " + missionName,
                                mission.getId()
                        );
                    }
                }
            }
        }
    }

    // 매분 deadline 지난 ACTIVE 미션 처리
    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void processExpiredMissions() {
        LocalDateTime now = LocalDateTime.now();
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
                for (Submission submission : submissions) {
                    if (submission.getStatus() == Submission.SubmissionStatus.SUBMITTED
                            && !collectionRecordRepository.existsBySubmission(submission)) {
                        collectionRecordRepository.save(CollectionRecord.builder()
                                .user(submission.getUser())
                                .missionTemplate(mission.getMissionTemplate())
                                .room(mission.getRoom())
                                .submission(submission)
                                .date(mission.getDate())
                                .thumbnail(submission.getVideoUrl())
                                .build());
                        log.info("도감 기록 생성: userId={}, missionTemplateId={}",
                                submission.getUser().getId(), mission.getMissionTemplate().getId());
                    }
                }

                mission.complete();
                log.info("미션 완료 처리: missionId={}", mission.getId());
            }
        }
    }
}
