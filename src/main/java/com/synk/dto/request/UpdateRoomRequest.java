// 방장이 방 설정을 수정할 때 사용

package com.synk.dto.request;

import lombok.Getter;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.synk.global.json.EndOfDayTimeDeserializer;

import java.time.LocalTime;

@Getter
public class UpdateRoomRequest {

    private String name;
    private String thumbnail;
    private Integer maxMembers;
    private Integer dailyMissionCount;
    private LocalTime missionStartTime;
    // 프론트가 하루의 끝을 "24:00" 으로 보내므로 허용 (23:59:59 로 해석)
    @JsonDeserialize(using = EndOfDayTimeDeserializer.class)
    private LocalTime missionEndTime;
}

