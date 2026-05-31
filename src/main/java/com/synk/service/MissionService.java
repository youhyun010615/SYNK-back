// 진행 중인 미션 조회와 참여 현황 조회 로직

package com.synk.service;

import com.synk.dto.response.ActiveMissionResponse;
import com.synk.dto.response.MissionParticipantResponse;
import com.synk.entity.Mission;
import com.synk.entity.Room;
import com.synk.entity.RoomMember;
import com.synk.entity.Submission;
import com.synk.entity.User;
import com.synk.global.exception.CustomException;
import com.synk.global.exception.ErrorCode;
import com.synk.repository.MissionRepository;
import com.synk.repository.RoomMemberRepository;
import com.synk.repository.RoomRepository;
import com.synk.repository.SubmissionRepository;
import com.synk.repository.UserRepository;
import com.synk.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MissionService {

    private final MissionRepository missionRepository;
    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final SubmissionRepository submissionRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<ActiveMissionResponse> getActiveMissions(Long roomId)
    {
        User user = getUser();

        if (roomId != null) {
            Room room = roomRepository.findById(roomId)
                    .orElseThrow(() -> new
                            CustomException(ErrorCode.ROOM_NOT_FOUND));
            if (!roomMemberRepository.existsByUserAndRoom(user, room))
            {
                throw new
                        CustomException(ErrorCode.ROOM_ACCESS_DENIED);
            }
            return missionRepository.findByRoomAndStatus(room,
                            Mission.MissionStatus.ACTIVE)
                    .stream()
                    .map(ActiveMissionResponse::from)
                    .toList();
        }

        List<RoomMember> myRooms =
                roomMemberRepository.findByUser(user);
        return myRooms.stream()
                .flatMap(rm -> missionRepository
                        .findByRoomAndStatus(rm.getRoom(),
                                Mission.MissionStatus.ACTIVE)
                        .stream())
                .map(ActiveMissionResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public MissionParticipantResponse getMissionParticipants(Long
                                                                     missionId) {
        User user = getUser();

        Mission mission = missionRepository.findById(missionId)
                .orElseThrow(() -> new
                        CustomException(ErrorCode.MISSION_NOT_FOUND));

        Room room = mission.getRoom();
        if (!roomMemberRepository.existsByUserAndRoom(user, room)) {
            throw new CustomException(ErrorCode.ROOM_ACCESS_DENIED);
        }

        List<RoomMember> members =
                roomMemberRepository.findByRoom(room);
        List<Submission> submissions =
                submissionRepository.findByMission(mission);

        return MissionParticipantResponse.from(mission, members,
                submissions);
    }

    private User getUser() {
        Long userId = SecurityUtil.getCurrentUserId();
        return userRepository.findById(userId)
                .orElseThrow(() -> new
                        CustomException(ErrorCode.USER_NOT_FOUND));
    }
}
