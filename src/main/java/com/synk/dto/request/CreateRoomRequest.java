// 방 생성시 필요한 정보를 담는다.

package com.synk.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

import java.time.LocalTime;

@Getter
public class CreateRoomRequest {

    @NotBlank
    private String name;

    private String thumbnail;

    @Min(value = 1, message = "최대 인원은 1명 이상이어야 합니다.")
    private int maxMembers;

    @Min(value = 1, message = "일일 미션 수는 1개 이상이어야 합니다.")
    private int dailyMissionCount;

    @NotNull
    private LocalTime missionStartTime;

    @NotNull
    private LocalTime missionEndTime;
}

