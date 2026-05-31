// 현재 진행 중인 미션 정보와 남은 시간을 담는다.

package com.synk.dto.response;

import com.synk.entity.Mission;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Getter
@Builder
public class ActiveMissionResponse {

    private Long id;
    private Long roomId;
    private String roomName;
    private String title;
    private String description;
    private LocalDate missionDate;
    private String slotTime;
    private LocalDateTime deadline;
    private long remainingSeconds;

    public static ActiveMissionResponse from(Mission mission) {
        LocalDateTime deadline = mission.getDeadline();
        long remainingSeconds =
                ChronoUnit.SECONDS.between(LocalDateTime.now(), deadline);

        return ActiveMissionResponse.builder()
                .id(mission.getId())
                .roomId(mission.getRoom().getId())
                .roomName(mission.getRoom().getName())
                .title(mission.getMissionTemplate().getTitle())

                .description(mission.getMissionTemplate().getDescription())
                .missionDate(mission.getDate())

                .slotTime(mission.getTimeSlot().getSlotTime().toString())
                .deadline(deadline)
                .remainingSeconds(Math.max(remainingSeconds, 0))
                .build();
    }
}

