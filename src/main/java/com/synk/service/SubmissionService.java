// 미션 영상 제출, 제출 현황 조회, 개별 영상 조회 로직

package com.synk.service;

import com.synk.dto.request.SubmissionRequest;
import com.synk.dto.response.RoomEventResponse;
import com.synk.dto.response.SubmissionDetailResponse;
import com.synk.dto.response.SubmissionResponse;
import com.synk.dto.response.SubmissionStatusResponse;
import com.synk.entity.Mission;
import com.synk.entity.Room;
import com.synk.entity.Submission;
import com.synk.entity.User;
import com.synk.global.exception.CustomException;
import com.synk.global.exception.ErrorCode;
import com.synk.entity.CollectionRecord;
import com.synk.entity.Synklog;
import com.synk.repository.CollectionRecordRepository;
import com.synk.repository.SynklogRepository;
import com.synk.repository.MissionRepository;
import com.synk.repository.RoomMemberRepository;
import com.synk.repository.RoomRepository;
import com.synk.repository.SubmissionRepository;
import com.synk.repository.UserRepository;
import com.synk.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SubmissionService {

    private final SubmissionRepository submissionRepository;
    private final MissionRepository missionRepository;
    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final UserRepository userRepository;
    private final CollageService collageService;
    private final CollectionRecordRepository collectionRecordRepository;
    private final SynklogRepository synklogRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional
    public SubmissionResponse submit(SubmissionRequest request) {
        User user = getUser();

        Mission mission =
                missionRepository.findById(request.getMissionId())
                        .orElseThrow(() -> new
                                CustomException(ErrorCode.MISSION_NOT_FOUND));

        if (mission.getStatus() != Mission.MissionStatus.ACTIVE) {
            throw new CustomException(ErrorCode.MISSION_EXPIRED);
        }

        Room room = roomRepository.findById(request.getRoomId())
                .orElseThrow(() -> new
                        CustomException(ErrorCode.ROOM_NOT_FOUND));

        // 재촬영: 이미 제출한 경우 videoUrl만 업데이트
        Submission submission = submissionRepository.findByMissionAndUser(mission, user)
                .map(existing -> {
                    existing.updateVideo(request.getVideoUrl(), request.isHorizontal(), request.getFacingMode());
                    return existing;
                })
                .orElseGet(() -> submissionRepository.save(Submission.builder()
                        .user(user)
                        .room(room)
                        .mission(mission)
                        .videoUrl(request.getVideoUrl())
                        .horizontal(request.isHorizontal())
                        .facingMode(request.getFacingMode())
                        .status(Submission.SubmissionStatus.SUBMITTED)
                        .build()));

        // 전원 제출 시 즉시 미션 완료 처리
        int totalMembers = roomMemberRepository.countByRoom(room);
        List<Submission> allSubmissions = submissionRepository.findByMission(mission);
        long submittedCount = allSubmissions.stream()
                .filter(s -> s.getStatus() == Submission.SubmissionStatus.SUBMITTED)
                .count();
        if (submittedCount >= totalMembers) {
            mission.complete();
            // 도감 기록 생성
            for (Submission s : allSubmissions) {
                if (s.getStatus() == Submission.SubmissionStatus.SUBMITTED
                        && !collectionRecordRepository.existsBySubmission(s)) {
                    collectionRecordRepository.save(CollectionRecord.builder()
                            .user(s.getUser())
                            .missionTemplate(mission.getMissionTemplate())
                            .room(room)
                            .submission(s)
                            .date(mission.getDate())
                            .thumbnail(null)
                            .build());
                }
            }
            collageService.triggerCollageIfReady(mission);

            // Synklog 자동 생성
            if (synklogRepository.findByRoomAndDate(room, mission.getDate()).isEmpty()) {
                synklogRepository.save(Synklog.builder()
                        .room(room)
                        .date(mission.getDate())
                        .build());
            }
        }

        // WebSocket MEMBER_SUBMITTED 이벤트 push
        messagingTemplate.convertAndSend(
                "/topic/rooms/" + room.getId(),
                RoomEventResponse.memberSubmitted(room.getId(), mission.getId(), user.getId(), (int) submittedCount, totalMembers)
        );

        return SubmissionResponse.from(submission);
    }

    @Transactional(readOnly = true)
    public SubmissionStatusResponse getSubmissionStatus(Long
                                                                missionId) {
        Mission mission = missionRepository.findById(missionId)
                .orElseThrow(() -> new
                        CustomException(ErrorCode.MISSION_NOT_FOUND));

        List<Submission> submissions =
                submissionRepository.findByMission(mission);

        long remainingSeconds = Math.max(
                ChronoUnit.SECONDS.between(LocalDateTime.now(),
                        mission.getDeadline()), 0);

        return SubmissionStatusResponse.from(submissions,
                remainingSeconds);
    }

    @Transactional(readOnly = true)
    public SubmissionDetailResponse getSubmission(Long submissionId) {
        User user = getUser();

        Submission submission =
                submissionRepository.findById(submissionId)
                        .orElseThrow(() -> new
                                CustomException(ErrorCode.SUBMISSION_NOT_FOUND));

        return SubmissionDetailResponse.from(submission);
    }

    private User getUser() {
        Long userId = SecurityUtil.getCurrentUserId();
        return userRepository.findById(userId)
                .orElseThrow(() -> new
                        CustomException(ErrorCode.USER_NOT_FOUND));
    }
}

