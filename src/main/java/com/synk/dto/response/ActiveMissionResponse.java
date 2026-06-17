package com.synk.dto.response;

import com.synk.entity.Mission;
import com.synk.entity.RoomMember;
import com.synk.entity.Submission;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Getter
@Builder
public class ActiveMissionResponse {

    private Long id;
    private Long roomId;
    private String roomName;
    private String roomThumbnail;
    private String title;
    private String description;
    private LocalDate missionDate;
    private String slotTime;
    private LocalDateTime deadline;
    private long remainingSeconds;
    private int totalMembers;
    private int submittedCount;
    private List<Participant> participants;

    @Getter
    @Builder
    public static class Participant {
        private Long userId;
        private String name;
        private String profileImage;
        private String status;
    }

    public static ActiveMissionResponse from(Mission mission,
                                             List<RoomMember> members,
                                             List<Submission> submissions) {
        LocalDateTime deadline = mission.getDeadline();
        long remainingSeconds = Math.max(
                ChronoUnit.SECONDS.between(LocalDateTime.now(), deadline), 0);

        Map<Long, Submission> submissionMap = submissions.stream()
                .collect(Collectors.toMap(s -> s.getUser().getId(), s -> s));

        int submittedCount = (int) submissions.stream()
                .filter(s -> s.getStatus() == Submission.SubmissionStatus.SUBMITTED)
                .count();

        List<Participant> participantList = members.stream()
                .map(m -> {
                    Submission sub = submissionMap.get(m.getUser().getId());
                    String status = sub != null ? "완료" : "대기";
                    return Participant.builder()
                            .userId(m.getUser().getId())
                            .name(m.getUser().getName())
                            .profileImage(m.getUser().getProfileImage())
                            .status(status)
                            .build();
                })
                .toList();

        return ActiveMissionResponse.builder()
                .id(mission.getId())
                .roomId(mission.getRoom().getId())
                .roomName(mission.getRoom().getName())
                .roomThumbnail(mission.getRoom().getThumbnail())
                .title(mission.getMissionTemplate().getTitle())
                .description(mission.getMissionTemplate().getDescription())
                .missionDate(mission.getDate())
                .slotTime(mission.getTimeSlot().getSlotTime().toString())
                .deadline(deadline)
                .remainingSeconds(remainingSeconds)
                .totalMembers(members.size())
                .submittedCount(submittedCount)
                .participants(participantList)
                .build();
    }
}
