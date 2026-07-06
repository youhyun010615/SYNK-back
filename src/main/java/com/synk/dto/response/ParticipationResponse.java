// 주별 참여율 조회 응답
package com.synk.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class ParticipationResponse {

    private int averageRate;       // 방 전체 평균 참여율 (%)
    private int memberCount;       // 방 멤버 수
    private int missionCount;      // 해당 주에 발송된 미션 총 횟수
    private String startDate;      // 주 시작일 (월요일, YYYY-MM-DD)
    private String endDate;        // 주 종료일 (일요일, YYYY-MM-DD)
    private List<MemberParticipation> members;

    @Getter
    @Builder
    public static class MemberParticipation {
        private Long userId;
        private String name;
        private String profileImage;
        private int completed;     // 해당 주 완료한 미션 수
        private int total;         // 해당 주 발송된 미션 수 (= missionCount)
        private int rate;          // 참여율 % (completed/total*100, 반올림)
        private int rank;          // 참여율 기준 순위 (1위부터)
    }
}
