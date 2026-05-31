// 콜라주에서 특정 유저의 영상을 조회할 때 쓴다.

package com.synk.dto.response;

import com.synk.entity.Submission;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class SubmissionDetailResponse {

    private Long id;
    private Long userId;
    private String userName;
    private String profileImage;
    private String missionTitle;
    private LocalDateTime submittedAt;
    private String videoUrl;

    public static SubmissionDetailResponse from(Submission submission)
    {
        return SubmissionDetailResponse.builder()
                .id(submission.getId())
                .userId(submission.getUser().getId())
                .userName(submission.getUser().getName())
                .profileImage(submission.getUser().getProfileImage())

                .missionTitle(submission.getMission().getMissionTemplate().getTitle())
                .submittedAt(submission.getSubmittedAt())
                .videoUrl(submission.getVideoUrl())
                .build();
    }
}
