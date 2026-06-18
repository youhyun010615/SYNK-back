package com.synk.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.synk.dto.request.CollageCallbackRequest;
import com.synk.dto.response.CollageResponse;
import com.synk.entity.Collage;
import com.synk.entity.Mission;
import com.synk.entity.Submission;
import com.synk.global.exception.CustomException;
import com.synk.global.exception.ErrorCode;
import com.synk.repository.CollageRepository;
import com.synk.repository.MissionRepository;
import com.synk.repository.RoomMemberRepository;
import com.synk.repository.RoomRepository;
import com.synk.repository.SubmissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;
import software.amazon.awssdk.services.lambda.model.InvocationType;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CollageService {

    private final CollageRepository collageRepository;
    private final MissionRepository missionRepository;
    private final SubmissionRepository submissionRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final RoomRepository roomRepository;
    private final LambdaClient lambdaClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${aws.lambda.function-name}")
    private String lambdaFunctionName;

    @Value("${collage.callback.secret}")
    private String callbackSecret;

    @Value("${collage.callback-url}")
    private String callbackUrl;

    @Transactional
    public void triggerCollageIfReady(Mission mission) {
        int totalMembers = roomMemberRepository.countByRoom(mission.getRoom());
        List<Submission> submissions = submissionRepository.findByMission(mission);
        long submittedCount = submissions.stream()
                .filter(s -> s.getStatus() == Submission.SubmissionStatus.SUBMITTED)
                .count();

        if (submittedCount == 0) return;

        if (collageRepository.findByMission(mission).isPresent()) return;

        Collage collage = collageRepository.save(Collage.builder()
                .mission(mission)
                .room(mission.getRoom())
                .totalMembers(totalMembers)
                .build());

        invokeLambda(mission, submissions, collage, totalMembers);
    }

    private void invokeLambda(Mission mission, List<Submission> submissions, Collage collage, int totalMembers) {
        try {
            List<Map<String, Object>> submissionPayloads = submissions.stream()
                    .filter(s -> s.getStatus() == Submission.SubmissionStatus.SUBMITTED
                            && s.getVideoUrl() != null)
                    .map(s -> {
                        Map<String, Object> m = new HashMap<>();
                        m.put("userId", s.getUser().getId());
                        m.put("videoUrl", s.getVideoUrl());
                        m.put("status", s.getStatus().name());
                        return m;
                    })
                    .toList();

            if (submissionPayloads.isEmpty()) {
                collage.fail();
                return;
            }

            Map<String, Object> payload = new HashMap<>();
            payload.put("missionId", mission.getId());
            payload.put("submissions", submissionPayloads);
            payload.put("callbackUrl", callbackUrl);
            payload.put("callbackSecret", callbackSecret);

            String payloadJson = objectMapper.writeValueAsString(payload);

            InvokeRequest request = InvokeRequest.builder()
                    .functionName(lambdaFunctionName)
                    .invocationType(InvocationType.EVENT) // async
                    .payload(SdkBytes.fromUtf8String(payloadJson))
                    .build();

            lambdaClient.invoke(request);
            log.info("Lambda 비동기 호출: missionId={}, submissions={}", mission.getId(), submissionPayloads.size());

        } catch (Exception e) {
            log.error("Lambda 호출 실패: missionId={}", mission.getId(), e);
            collage.fail();
        }
    }

    @Transactional
    public void handleCallback(String receivedSecret, CollageCallbackRequest request) {
        if (!callbackSecret.equals(receivedSecret)) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }

        Mission mission = missionRepository.findById(request.getMissionId())
                .orElseThrow(() -> new CustomException(ErrorCode.MISSION_NOT_FOUND));

        Collage collage = collageRepository.findByMission(mission)
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_REQUEST));

        if (request.isSuccess()) {
            int totalMembers = collage.getTotalMembers() != null ? collage.getTotalMembers() : 1;
            int submittedCount = request.getSubmittedCount() != null ? request.getSubmittedCount() : 0;
            int participationRate = totalMembers > 0 ? (submittedCount * 100 / totalMembers) : 0;
            int completionTime = 0; // optional: compute from submission timestamps

            collage.complete(
                    request.getCollageVideoUrl(),
                    request.getThumbnailUrl(),
                    participationRate,
                    completionTime,
                    submittedCount
            );
            log.info("콜라주 완료: missionId={}, url={}", request.getMissionId(), request.getCollageVideoUrl());
        } else {
            collage.fail();
            log.warn("콜라주 실패: missionId={}, error={}", request.getMissionId(), request.getError());
        }
    }

    @Transactional(readOnly = true)
    public List<CollageResponse> getCollagesForDate(Long roomId, LocalDate date) {
        com.synk.entity.Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.ROOM_NOT_FOUND));
        return collageRepository.findByRoomAndMission_Date(room, date).stream()
                .map(CollageResponse::from)
                .toList();
    }
}
