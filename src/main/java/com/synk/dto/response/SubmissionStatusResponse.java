// 미션의 전체 제출 현황(참여율, 맴버별 상태)을 담는다

package com.synk.dto.response;

import com.synk.entity.Submission;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class SubmissionStatusResponse {

    private long remainingSeconds;
    private List<ParticipantStatus> participants;

    @Getter
    @Builder
    public static class ParticipantStatus {
        private String name;
        private String profileImage;
        private String status;
    }

    public static SubmissionStatusResponse from(List<Submission>
                                                        submissions, long remainingSeconds) {
        List<ParticipantStatus> participants = submissions.stream()
                .map(s -> ParticipantStatus.builder()
                        .name(s.getUser().getName())
                        .profileImage(s.getUser().getProfileImage())
                        .status(s.getStatus() ==
                                Submission.SubmissionStatus.SUBMITTED ? "완료" : "미완료")
                        .build())
                .toList();

        return SubmissionStatusResponse.builder()
                .remainingSeconds(remainingSeconds)
                .participants(participants)
                .build();
    }
}
