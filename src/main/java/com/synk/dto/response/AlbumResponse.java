// 날짜별 앰범 목록을 담는다.

package com.synk.dto.response;

import com.synk.entity.Collage;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class AlbumResponse {

    private String date;
    private String thumbnail;
    private List<MemberProfile> memberProfiles;

    @Getter
    @Builder
    public static class MemberProfile {
        private Long userId;
        private String profileImage;
    }

    public static AlbumResponse from(String date, Collage collage, List<MemberProfile> profiles) {
        return AlbumResponse.builder()
                .date(date)
                .thumbnail(collage != null ? collage.getThumbnail() : null)
                .memberProfiles(profiles)
                .build();
    }
}
