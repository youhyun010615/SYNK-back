// 미션 영상 제출, 제출 현황 조회, 개별 영상 조회 로직

package com.synk.service;

import com.synk.dto.request.SubmissionRequest;
import com.synk.dto.response.SubmissionDetailResponse;
import com.synk.dto.response.SubmissionResponse;
import com.synk.dto.response.SubmissionStatusResponse;
import com.synk.entity.Mission;
import com.synk.entity.Room;
import com.synk.entity.Submission;
import com.synk.entity.User;
import com.synk.global.exception.CustomException;
import com.synk.global.exception.ErrorCode;
import com.synk.repository.MissionRepository;
import com.synk.repository.RoomRepository;
import com.synk.repository.SubmissionRepository;
import com.synk.repository.UserRepository;
import com.synk.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
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
    private final UserRepository userRepository;

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

        if (submissionRepository.existsByMissionAndUser(mission,
                user)) {
            throw new CustomException(ErrorCode.ALREADY_SUBMITTED);
        }

        Room room = roomRepository.findById(request.getRoomId())
                .orElseThrow(() -> new
                        CustomException(ErrorCode.ROOM_NOT_FOUND));

        Submission submission =
                submissionRepository.save(Submission.builder()
                        .user(user)
                        .room(room)
                        .mission(mission)
                        .videoUrl(request.getVideoUrl())
                        .status(Submission.SubmissionStatus.SUBMITTED)
                        .build());

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

