package com.synk.entity;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 미션 마감 계산이 제한 시간 상수(Mission.DURATION_MINUTES)를 따르는지 검증한다. 값 자체(5분)는 고정하지 않는다. */
class MissionDeadlineTest {

    private Mission missionAt(LocalDate date, LocalTime slotTime) {
        return Mission.builder()
                .timeSlot(MissionTimeSlot.builder().slotTime(slotTime).build())
                .date(date)
                .build();
    }

    @Test
    void 발동하면_마감은_지금부터_제한_시간_뒤다() {
        Mission mission = missionAt(LocalDate.of(2026, 11, 21), LocalTime.of(13, 30));

        LocalDateTime before = LocalDateTime.now();
        mission.activate();
        LocalDateTime after = LocalDateTime.now();

        LocalDateTime expectedMin = before.plusMinutes(Mission.DURATION_MINUTES);
        LocalDateTime expectedMax = after.plusMinutes(Mission.DURATION_MINUTES);
        assertTrue(!mission.getDeadline().isBefore(expectedMin) && !mission.getDeadline().isAfter(expectedMax),
                "마감 " + mission.getDeadline() + " 은 " + expectedMin + " ~ " + expectedMax + " 사이여야 한다");
        assertEquals(Mission.MissionStatus.ACTIVE, mission.getStatus());
    }

    @Test
    void 저장된_마감이_없는_예전_데이터는_발동_예정_시각에서_제한_시간을_더한다() {
        Mission mission = missionAt(LocalDate.of(2026, 11, 21), LocalTime.of(13, 30)); // activate() 호출 전 → deadline 없음

        assertEquals(LocalDateTime.of(2026, 11, 21, 13, 30).plusMinutes(Mission.DURATION_MINUTES), mission.getDeadline());
    }

    @Test
    void 두_계산은_같은_제한_시간을_쓴다() {
        // 예정 시각 == 발동 시각이라면 두 방식의 마감 간격이 같아야 한다
        Mission legacy = missionAt(LocalDate.now(), LocalTime.of(0, 0));
        Duration legacyGap = Duration.between(legacy.getTargetedAt(), legacy.getDeadline());

        assertEquals(Duration.ofMinutes(Mission.DURATION_MINUTES), legacyGap);
    }
}
