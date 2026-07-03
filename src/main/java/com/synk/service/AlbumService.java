// 앨범 목록 조회, SYNKLOG 생성 요청, SYNKLOG 조회 로직

package com.synk.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.synk.dto.request.SynklogCallbackRequest;
import com.synk.dto.response.AlbumResponse;
import com.synk.dto.response.SynklogResponse;
import com.synk.entity.*;
import com.synk.global.exception.CustomException;
import com.synk.global.exception.ErrorCode;
import com.synk.repository.*;
import com.synk.util.SecurityUtil;
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
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlbumService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy.MM.dd");

    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final MissionRepository missionRepository;
    private final CollageRepository collageRepository;
    private final SynklogRepository synklogRepository;
    private final SubmissionRepository submissionRepository;
    private final UserRepository userRepository;
    private final LambdaClient lambdaClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${aws.lambda.synklog-function-name}")
    private String synklogLambdaFunctionName;

    @Value("${collage.callback.secret}")
    private String callbackSecret;

    @Value("${synklog.callback-url}")
    private String synklogCallbackUrl;

    @Transactional(readOnly = true)
    public List<AlbumResponse> getAlbums(Long roomId) {
        User user = getUser();
        Room room = getRoom(roomId);
        validateMember(user, room);

        List<Mission> missions = missionRepository.findByRoom(room);

        Map<LocalDate, List<Mission>> missionsByDate = missions.stream()
                        .collect(Collectors.groupingBy(Mission::getDate));

        return missionsByDate.entrySet().stream()
                .sorted(Map.Entry.<LocalDate, List<Mission>>comparingByKey().reversed())
                .map(entry -> {LocalDate date = entry.getKey();
                    List<Mission> dayMissions = entry.getValue();

                    Collage collage = dayMissions.stream()
                            .flatMap(m -> collageRepository.findByMission(m).stream())
                            .filter(c -> c.getThumbnail() != null)
                            .max(java.util.Comparator.comparing(Collage::getId))
                            .orElse(null);

                    List<AlbumResponse.MemberProfile>
                            profiles = roomMemberRepository
                            .findByRoom(room).stream()
                            .map(rm -> AlbumResponse.MemberProfile.builder()
                                    .userId(rm.getUser().getId())
                                    .profileImage(rm.getUser().getProfileImage())
                                    .build())
                            .toList();

                    return AlbumResponse.from(date.format(DATE_FORMAT), collage, profiles);
                }).toList();
    }

    @Transactional(readOnly = true)
    public List<AlbumResponse> getRecentAlbums(Long roomId, int limit) {
        User user = getUser();
        Room room = getRoom(roomId);
        validateMember(user, room);

        List<AlbumResponse.MemberProfile> profiles = roomMemberRepository
                .findByRoom(room).stream()
                .map(rm -> AlbumResponse.MemberProfile.builder()
                        .userId(rm.getUser().getId())
                        .profileImage(rm.getUser().getProfileImage())
                        .build())
                .toList();

        return collageRepository.findByRoomOrderByCreatedAtDesc(room).stream()
                .filter(c -> c.getThumbnail() != null)
                .limit(limit)
                .map(c -> AlbumResponse.from(c.getMission().getDate().format(DATE_FORMAT), c, profiles))
                .toList();
    }

    @Transactional
    public SynklogResponse createSynklog(Long roomId, LocalDate date) {
        User user = getUser();
        Room room = getRoom(roomId);
        validateMember(user, room);

        // 이미 완료된 synklog → 그대로 반환
        Synklog existing = synklogRepository.findByRoomAndDate(room, date).orElse(null);
        if (existing != null && existing.getStatus() == Synklog.SynklogStatus.COMPLETED) {
            List<Mission> dayMissions = missionRepository.findByRoomAndDate(room, date);
            return SynklogResponse.from(existing, dayMissions);
        }
        // PROCESSING(스케줄러 자동 생성) or FAILED → Lambda 재호출
        if (existing != null) {
            invokeSynklogLambda(existing, room, date);
            return SynklogResponse.from(existing, null);
        }

        Synklog synklog = synklogRepository.save(Synklog.builder()
                        .room(room)
                        .date(date)
                        .build());

        invokeSynklogLambda(synklog, room, date);

        return SynklogResponse.from(synklog, null);
    }

    private void invokeSynklogLambda(Synklog synklog, Room room, LocalDate date) {
        try {
            List<Collage> collages = collageRepository.findByRoomAndMission_Date(room, date);
            List<String> videoUrls = collages.stream()
                    .filter(c -> c.getStatus() == Collage.CollageStatus.COMPLETED && c.getCollageVideoUrl() != null)
                    .sorted(java.util.Comparator.comparing(Collage::getId))
                    .map(Collage::getCollageVideoUrl)
                    .toList();

            if (videoUrls.isEmpty()) {
                log.warn("SYNKLOG Lambda 호출 취소 — 완료된 collage 없음: synklogId={}", synklog.getId());
                synklog.fail();
                return;
            }

            Map<String, Object> payload = new HashMap<>();
            payload.put("synklogId", synklog.getId());
            payload.put("videoUrls", videoUrls);
            payload.put("callbackUrl", synklogCallbackUrl);
            payload.put("callbackSecret", callbackSecret);

            String payloadJson = objectMapper.writeValueAsString(payload);
            InvokeRequest request = InvokeRequest.builder()
                    .functionName(synklogLambdaFunctionName)
                    .invocationType(InvocationType.EVENT)
                    .payload(SdkBytes.fromUtf8String(payloadJson))
                    .build();

            lambdaClient.invoke(request);
            log.info("SYNKLOG Lambda 호출: synklogId={}, videos={}", synklog.getId(), videoUrls.size());
        } catch (Exception e) {
            log.error("SYNKLOG Lambda 호출 실패: synklogId={}", synklog.getId(), e);
            synklog.fail();
        }
    }

    @Transactional
    public SynklogResponse handleSynklogCallback(String receivedSecret, SynklogCallbackRequest request) {
        if (!callbackSecret.equals(receivedSecret)) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }

        Synklog synklog = synklogRepository.findById(request.getSynklogId())
                .orElseThrow(() -> new CustomException(ErrorCode.SYNKLOG_NOT_FOUND));

        if (request.isSuccess()) {
            synklog.complete(request.getSynklogVideoUrl(), request.getThumbnailUrl());
            log.info("SYNKLOG 완료: synklogId={}, url={}", synklog.getId(), request.getSynklogVideoUrl());
        } else {
            synklog.fail();
            log.warn("SYNKLOG 실패: synklogId={}, error={}", synklog.getId(), request.getError());
        }

        List<Mission> dayMissions = missionRepository.findByRoomAndDate(synklog.getRoom(), synklog.getDate());
        return SynklogResponse.from(synklog, dayMissions);
    }

    @Transactional(readOnly = true)
    public SynklogResponse getSynklog(Long roomId, LocalDate date) {
        Room room = getRoom(roomId);
        Synklog synklog = synklogRepository.findByRoomAndDate(room, date)
                        .orElseThrow(() -> new CustomException(ErrorCode.SYNKLOG_NOT_FOUND));
        List<Mission> dayMissions = missionRepository.findByRoomAndDate(room, date);
        return SynklogResponse.from(synklog, dayMissions);
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
}
