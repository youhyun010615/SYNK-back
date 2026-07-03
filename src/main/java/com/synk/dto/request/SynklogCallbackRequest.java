package com.synk.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SynklogCallbackRequest {
    private Long synklogId;
    private boolean success;
    private String synklogVideoUrl;
    private String thumbnailUrl;
    private String error;
}
