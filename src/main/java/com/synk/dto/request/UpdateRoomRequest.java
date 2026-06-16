// 방장이 방 설정을 수정할 때 사용

package com.synk.dto.request;

import lombok.Getter;

import java.time.LocalTime;

@Getter
public class UpdateRoomRequest {

    private String name;
    private String thumbnail;
    private Integer maxMembers;
    private Integer dailyMissionCount;
    private LocalTime missionStartTime;
    private LocalTime missionEndTime;
}

