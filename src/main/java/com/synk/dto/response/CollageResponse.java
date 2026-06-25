package com.synk.dto.response;

import com.synk.entity.Collage;
import com.synk.entity.Submission;
import com.synk.entity.RoomMember;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Getter
@Builder
public class CollageResponse {
    private Long id;
    private Long missionId;
    private String missionTitle;
    private String missionStartAt;
    private String status;
    private String collageVideoUrl;
    private String thumbnail;
    private Integer totalMembers;
    private Integer submittedCount;
    private Integer participationRate;
    private LocalDateTime completedAt;
    private List<CollageParticipant> participants;

    @Getter
    @Builder
    public static class CollageParticipant {
        private Long userId;
        private String name;
        private String profileImage;
        private String videoUrl;
        private String submittedAt;
        private String state;
    }

    public static CollageResponse from(Collage collage, List<RoomMember> members, List<Submission> submissions) {
        Map<Long, Submission> submissionByUserId = submissions.stream()
                .collect(Collectors.toMap(s -> s.getUser().getId(), s -> s, (a, b) -> a));

        List<CollageParticipant> participantList = members.stream()
                .map(member -> {
                    Submission sub = submissionByUserId.get(member.getUser().getId());
                    boolean submitted = sub != null && sub.getStatus() == Submission.SubmissionStatus.SUBMITTED;
                    return CollageParticipant.builder()
                            .userId(member.getUser().getId())
                            .name(member.getUser().getName())
                            .profileImage(member.getUser().getProfileImage())
                            .videoUrl(submitted ? sub.getVideoUrl() : null)
                            .submittedAt(submitted && sub.getSubmittedAt() != null ? sub.getSubmittedAt().toString() : null)
                            .state(submitted ? "done" : "waiting")
                            .build();
                })
                .collect(Collectors.toList());

        String missionTitle = collage.getMission().getMissionTemplate() != null
                ? collage.getMission().getMissionTemplate().getTitle()
                : null;

        String missionStartAt = collage.getMission().getTargetedAt() != null
                ? collage.getMission().getTargetedAt().toString()
                : null;

        return CollageResponse.builder()
                .id(collage.getId())
                .missionId(collage.getMission().getId())
                .missionTitle(missionTitle)
                .missionStartAt(missionStartAt)
                .status(collage.getStatus().name())
                .collageVideoUrl(collage.getCollageVideoUrl())
                .thumbnail(collage.getThumbnail())
                .totalMembers(collage.getTotalMembers())
                .submittedCount(collage.getSubmittedCount())
                .participationRate(collage.getParticipationRate())
                .completedAt(collage.getCompletedAt())
                .participants(participantList)
                .build();
    }
}
