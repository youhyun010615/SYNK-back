// SYNKLOG 상태와 영상 URL을 담는다.

package com.synk.dto.response;

import com.synk.entity.Synklog;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SynklogResponse {

    private Long synklogId;
    private String date;
    private String status;
    private String synklogVideoUrl;
    private String thumbnail;

    public static SynklogResponse from(Synklog synklog) {
        return SynklogResponse.builder()
                .synklogId(synklog.getId())
                .date(synklog.getDate().toString())
                .status(synklog.getStatus().name())

                .synklogVideoUrl(synklog.getSynklogVideoUrl())
                .thumbnail(synklog.getThumbnail())
                .build();
    }
}
