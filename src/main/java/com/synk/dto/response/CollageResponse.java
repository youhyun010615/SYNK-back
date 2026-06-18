package com.synk.dto.response;

import com.synk.entity.Collage;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class CollageResponse {
    private Long id;
    private Long missionId;
    private String status;
    private String collageVideoUrl;
    private String thumbnail;
    private Integer totalMembers;
    private Integer submittedCount;
    private Integer participationRate;
    private LocalDateTime completedAt;

    public static CollageResponse from(Collage collage) {
        return CollageResponse.builder()
                .id(collage.getId())
                .missionId(collage.getMission().getId())
                .status(collage.getStatus().name())
                .collageVideoUrl(collage.getCollageVideoUrl())
                .thumbnail(collage.getThumbnail())
                .totalMembers(collage.getTotalMembers())
                .submittedCount(collage.getSubmittedCount())
                .participationRate(collage.getParticipationRate())
                .completedAt(collage.getCompletedAt())
                .build();
    }
}
