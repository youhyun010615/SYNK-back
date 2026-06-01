// 도감 전체 목록과 수집률을 담는다.

package com.synk.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class CollectionResponse {

    private int completionRate;
    private int completedCount;
    private int totalCount;
    private List<MissionSummary> missions;

    @Getter
    @Builder
    public static class MissionSummary {
        private Long missionId;
        private String title;
        private String thumbnail;
        private int completedTimes;
        private String lastCompletedDate;
    }
}


