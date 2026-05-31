// 방 생성시 필요한 정보를 담는다.

package com.synk.dto.request;

import lombok.Getter;

import java.time.LocalTime;

@Getter
public class CreateRoomRequest {

    private String name;
    private String thumbnail;
    private int maxMembers;
    private int dailyMissionCount;
    private LocalTime missionStartTime;
    private LocalTime missionEndTime;
}

