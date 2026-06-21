// SYNKLOG 상태와 영상 URL을 담는다.

package com.synk.dto.response;

import com.synk.entity.Mission;
import com.synk.entity.Synklog;
import lombok.Builder;
import lombok.Getter;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Getter
@Builder
public class SynklogResponse {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy.MM.dd");

    private Long synklogId;
    private String date;
    private String status;
    private String synklogVideoUrl;
    private String thumbnail;
    private List<MissionInfo> missions;

    @Getter
    @Builder
    public static class MissionInfo {
        private String missionTitle;
    }

    public static SynklogResponse from(Synklog synklog, List<Mission> dayMissions) {
        List<MissionInfo> missionInfos = null;
        if (synklog.getStatus() == Synklog.SynklogStatus.COMPLETED && dayMissions != null) {
            missionInfos = dayMissions.stream()
                    .map(m -> MissionInfo.builder()
                            .missionTitle(m.getMissionTemplate().getTitle())
                            .build())
                    .toList();
        }

        return SynklogResponse.builder()
                .synklogId(synklog.getId())
                .date(synklog.getDate().format(DATE_FORMAT))
                .status(synklog.getStatus().name())
                .synklogVideoUrl(synklog.getSynklogVideoUrl())
                .thumbnail(synklog.getThumbnail())
                .missions(missionInfos)
                .build();
    }
}
