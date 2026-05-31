// 미션 영상 제출 시 roomId, missionId, 영상 URL을 담는다

package com.synk.dto.request;

import lombok.Getter;

@Getter
public class SubmissionRequest {

    private Long roomId;
    private Long missionId;
    private String videoUrl;
}

