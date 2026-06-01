// 앨범 목록 조회, SYNKLOG 생성 요청, SYNKLOG 조회 로직

package com.synk.service;

import com.synk.dto.response.AlbumResponse;
import com.synk.dto.response.SynklogResponse;
import com.synk.entity.*;
import com.synk.global.exception.CustomException;
import com.synk.global.exception.ErrorCode;
import com.synk.repository.*;
import com.synk.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AlbumService {

    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final MissionRepository missionRepository;
    private final CollageRepository collageRepository;
    private final SynklogRepository synklogRepository;
    private final SubmissionRepository submissionRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<AlbumResponse> getAlbums(Long roomId) {
        User user = getUser();
        Room room = getRoom(roomId);
        validateMember(user, room);

        List<Mission> missions = missionRepository.findByRoomAndDate(room, LocalDate.now());

        Map<LocalDate, List<Mission>> missionsByDate = missions.stream()
                        .collect(Collectors.groupingBy(Mission::getDate));

        return missionsByDate.entrySet().stream()
                .sorted(Map.Entry.<LocalDate, List<Mission>>comparingByKey().reversed())
                .map(entry -> {LocalDate date = entry.getKey();
                    List<Mission> dayMissions = entry.getValue();

                    Collage collage = dayMissions.stream()
                            .flatMap(m -> collageRepository.findByMission(m).stream())
                            .findFirst()
                            .orElse(null);

                    List<AlbumResponse.MemberProfile>
                            profiles = roomMemberRepository
                            .findByRoom(room).stream()
                            .map(rm -> AlbumResponse.MemberProfile.builder()
                                    .userId(rm.getUser().getId())
                                    .profileImage(rm.getUser().getProfileImage())
                                    .build())
                            .toList();

                    return AlbumResponse.from(date.toString(), collage, profiles);
                }).toList();
    }

    @Transactional
    public SynklogResponse createSynklog(Long roomId, LocalDate date) {
        User user = getUser();
        Room room = getRoom(roomId);
        validateMember(user, room);

        if (synklogRepository.findByRoomAndDate(room, date).isPresent()) {
            throw new CustomException(ErrorCode.SYNKLOG_ALREADY_EXISTS);
        }

        Synklog synklog = synklogRepository.save(Synklog.builder()
                        .room(room)
                        .date(date)
                        .build());

        return SynklogResponse.from(synklog);
    }

    @Transactional(readOnly = true)
    public SynklogResponse getSynklog(Long roomId, LocalDate date) {
        Room room = getRoom(roomId);
        Synklog synklog = synklogRepository.findByRoomAndDate(room, date)
                        .orElseThrow(() -> new CustomException(ErrorCode.SYNKLOG_NOT_FOUND));
        return SynklogResponse.from(synklog);
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
