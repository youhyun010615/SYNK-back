// 특정 미션의 내 기록 상세를 담는다.

package com.synk.dto.response;

import com.synk.entity.CollectionRecord;
import com.synk.entity.MissionTemplate;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class CollectionDetailResponse {

    private Long missionId;
    private String title;
    private String description;
    private int completedTimes;
    private String lastCompletedDate;
    private List<RecordInfo> records;

    @Getter
    @Builder
    public static class RecordInfo {
        private Long recordId;
        private String roomName;
        private String date;
        private String thumbnail;
        private String videoUrl;

        public static RecordInfo from(CollectionRecord record) {
            return RecordInfo.builder()
                    .recordId(record.getId())
                    .roomName(record.getRoom().getName())
                    .date(record.getDate().toString())
                    .thumbnail(record.getThumbnail())
                    .videoUrl(record.getSubmission().getVideoUrl())
                    .build();
        }
    }

    public static CollectionDetailResponse
    from(MissionTemplate template, List<CollectionRecord> records) {
        List<RecordInfo> recordInfos = records.stream()
                .map(RecordInfo::from)
                .toList();

        String lastDate = records.isEmpty() ? null :
                records.get(0).getDate().toString();

        return CollectionDetailResponse.builder()
                .missionId(template.getId())
                .title(template.getTitle())
                .description(template.getDescription())
                .completedTimes(records.size())
                .lastCompletedDate(lastDate)
                .records(recordInfos)
                .build();
    }
}

