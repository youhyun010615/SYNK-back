package com.synk.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CollageCallbackRequest {
    private Long missionId;
    private boolean success;
    private String collageVideoUrl;
    private String thumbnailUrl;
    private Integer submittedCount;
    private String error;
}
