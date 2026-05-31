// 미션 참여 현황과 맴버별 제출 상태를 담는다.

package com.synk.dto.response;

import com.synk.entity.Mission;
import com.synk.entity.RoomMember;
import com.synk.entity.Submission;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@Getter
@Builder
public class MissionParticipantResponse {

    private Long missionId;
    private String title;
    private String missionDate;
    private String slotTime;
    private LocalDateTime deadline;
    private long remainingSeconds;
    private String status;
    private int totalMembers;
    private int submittedCount;
    private List<ParticipantInfo> participants;

    @Getter
    @Builder
    public static class ParticipantInfo {
        private Long userId;
        private String name;
        private String profileImage;
        private String status;
        private LocalDateTime submittedAt;
    }

    public static MissionParticipantResponse from(Mission mission,
                                                  List<RoomMember>
                                                          members,
                                                  List<Submission>
                                                          submissions) {
        Map<Long, Submission> submissionMap = new
                java.util.HashMap<>();
        for (Submission s : submissions) {
            submissionMap.put(s.getUser().getId(), s);
        }

        List<ParticipantInfo> participants = members.stream()
                .map(m -> {
                    Submission sub =
                            submissionMap.get(m.getUser().getId());
                    return ParticipantInfo.builder()
                            .userId(m.getUser().getId())
                            .name(m.getUser().getName())

                            .profileImage(m.getUser().getProfileImage())
                            .status(sub != null ?
                                    sub.getStatus().name() : "PENDING")
                            .submittedAt(sub != null ?
                                    sub.getSubmittedAt() : null)
                            .build();
                })
                .toList();

        long remainingSeconds = Math.max(
                ChronoUnit.SECONDS.between(LocalDateTime.now(),
                        mission.getDeadline()), 0);

        int submittedCount = (int) submissions.stream()
                .filter(s -> s.getStatus() ==
                        Submission.SubmissionStatus.SUBMITTED)
                .count();

        return MissionParticipantResponse.builder()
                .missionId(mission.getId())
                .title(mission.getMissionTemplate().getTitle())
                .missionDate(mission.getDate().toString())

                .slotTime(mission.getTimeSlot().getSlotTime().toString())
                .deadline(mission.getDeadline())
                .remainingSeconds(remainingSeconds)
                .status(mission.getStatus().name())
                .totalMembers(members.size())
                .submittedCount(submittedCount)
                .participants(participants)
                .build();
    }
}

