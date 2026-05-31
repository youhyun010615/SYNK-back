// 제출 완료 후 제출 ID와 시간을 돌려준다.

package com.synk.dto.response;

import com.synk.entity.Submission;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class SubmissionResponse {

    private Long id;
    private LocalDateTime submittedAt;

    public static SubmissionResponse from(Submission submission) {
        return SubmissionResponse.builder()
                .id(submission.getId())
                .submittedAt(submission.getSubmittedAt())
                .build();
    }
}
