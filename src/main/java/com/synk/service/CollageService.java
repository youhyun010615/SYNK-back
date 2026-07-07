package com.synk.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.synk.dto.request.CollageCallbackRequest;
import com.synk.dto.response.CollageResponse;
import com.synk.entity.Collage;
import com.synk.entity.Mission;
import com.synk.entity.Notification;
import com.synk.entity.RoomMember;
import com.synk.entity.Submission;
import com.synk.entity.User;
import com.synk.global.exception.CustomException;
import com.synk.global.exception.ErrorCode;
import com.synk.repository.CollageRepository;
import com.synk.repository.CollectionRecordRepository;
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
    private final CollectionRecordRepository collectionRecordRepository;
    private final MissionRepository missionRepository;
    private final SubmissionRepository submissionRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final RoomRepository roomRepository;
    private final LambdaClient lambdaClient;
    private final FcmService fcmService;
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
            Map<Long, Submission> submissionByUserId = submissions.stream()
                    .collect(java.util.stream.Collectors.toMap(s -> s.getUser().getId(), s -> s, (a, b) -> a));

            // 같은 방이면 콜라주 셀 위치가 항상 고정되도록 입장 순서(RoomMember id)로 정렬
            List<RoomMember> members = roomMemberRepository.findByRoom(mission.getRoom()).stream()
                    .sorted(java.util.Comparator.comparing(RoomMember::getId))
                    .toList();

            // 전원 분량의 셀을 유지하기 위해 미제출자도 videoUrl=null로 포함시킨다 (Lambda가 빈 칸으로 렌더링)
            List<Map<String, Object>> submissionPayloads = members.stream()
                    .map(member -> {
                        Submission s = submissionByUserId.get(member.getUser().getId());
                        Map<String, Object> m = new HashMap<>();
                        m.put("userId", member.getUser().getId());
                        m.put("name", member.getUser().getName());
                        m.put("videoUrl", s != null ? s.getVideoUrl() : null);
                        m.put("horizontal", s != null && s.isHorizontal());
                        m.put("facingMode", s != null ? s.getFacingMode() : "user");
                        m.put("status", s != null ? s.getStatus().name() : "MISSED");
                        return m;
                    })
                    .toList();

            String missionTitleForPayload = mission.getMissionTemplate() != null
                    ? mission.getMissionTemplate().getTitle() : "";

            Map<String, Object> payload = new HashMap<>();
            payload.put("missionId", mission.getId());
            payload.put("missionTitle", missionTitleForPayload);
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

            // 이 미션의 CollectionRecord 썸네일을 콜라주 썸네일로 업데이트
            collectionRecordRepository.findBySubmission_Mission(mission)
                    .forEach(record -> record.updateThumbnail(request.getThumbnailUrl()));

            // 결과 알림: 콜라주가 완성되면 방 멤버 전원에게 알림 (resultAlert 켠 사람만)
            String missionTitle = mission.getMissionTemplate() != null
                    ? mission.getMissionTemplate().getTitle() : "미션";
            for (RoomMember member : roomMemberRepository.findByRoom(mission.getRoom())) {
                User memberUser = member.getUser();
                if (memberUser.isResultAlert()) {
                    log.info("MISSION_COMPLETE 발송 대상: userId={}, name={}, resultAlert=true",
                            memberUser.getId(), memberUser.getName());
                    fcmService.sendDataMessage(
                            memberUser,
                            Notification.NotificationType.MISSION_COMPLETE,
                            "✨ 콜라주 완성!",
                            "📽️ " + mission.getRoom().getName() + " · 다들 어떻게 찍었는지 확인해봐요!",
                            mission.getId(),
                            java.util.Map.of(
                                    "type", "MISSION_COMPLETE",
                                    "roomId", String.valueOf(mission.getRoom().getId()),
                                    "missionId", String.valueOf(mission.getId())
                            )
                    );
                } else {
                    log.info("MISSION_COMPLETE 스킵(resultAlert=false): userId={}, name={}",
                            memberUser.getId(), memberUser.getName());
                }
            }
        } else {
            collage.fail();
            log.warn("콜라주 실패: missionId={}, error={}", request.getMissionId(), request.getError());
        }
    }

    @Transactional(readOnly = true)
    public List<CollageResponse> getCollagesForDate(Long roomId, LocalDate date) {
        com.synk.entity.Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.ROOM_NOT_FOUND));
        List<com.synk.entity.RoomMember> members = roomMemberRepository.findByRoom(room);
        return collageRepository.findByRoomAndMission_Date(room, date).stream()
                .map(collage -> {
                    List<Submission> submissions = submissionRepository.findByMission(collage.getMission());
                    return CollageResponse.from(collage, members, submissions);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public CollageResponse getCollageByMission(Long missionId) {
        Mission mission = missionRepository.findById(missionId)
                .orElseThrow(() -> new CustomException(ErrorCode.MISSION_NOT_FOUND));
        Collage collage = collageRepository.findByMission(mission)
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_REQUEST));
        List<com.synk.entity.RoomMember> members = roomMemberRepository.findByRoom(mission.getRoom());
        List<Submission> submissions = submissionRepository.findByMission(mission);
        return CollageResponse.from(collage, members, submissions);
    }
}
