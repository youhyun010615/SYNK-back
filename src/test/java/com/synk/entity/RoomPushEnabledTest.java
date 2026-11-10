package com.synk.entity;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoomPushEnabledTest {

    private Room roomCreatedAt(LocalDateTime createdAt) {
        Room room = Room.builder()
                .name("방").code("ABCDE").maxMembers(3).dailyMissionCount(2)
                .missionStartTime(LocalTime.of(10, 0)).missionEndTime(LocalTime.of(22, 0))
                .build();
        // createdAt 은 JPA Auditing 이 채우므로 테스트에서는 직접 주입
        ReflectionTestUtils.setField(room, "createdAt", createdAt);
        return room;
    }

    @Test
    void 기존_방은_알림_대상이_아니다() {
        assertFalse(roomCreatedAt(LocalDateTime.of(2026, 11, 5, 19, 17, 43)).isPushEnabled()); // 마지막 기존 방
        assertFalse(roomCreatedAt(LocalDateTime.of(2026, 7, 6, 10, 0)).isPushEnabled());
    }

    @Test
    void 시작_시각_직전에_만든_방은_알림_대상이_아니다() {
        assertFalse(roomCreatedAt(Room.PUSH_START_AT.minusSeconds(1)).isPushEnabled());
    }

    @Test
    void 시작_시각_이후에_만든_방은_알림_대상이다() {
        assertTrue(roomCreatedAt(Room.PUSH_START_AT).isPushEnabled());
        assertTrue(roomCreatedAt(LocalDateTime.of(2026, 11, 10, 21, 0)).isPushEnabled());
        assertTrue(roomCreatedAt(LocalDateTime.of(2026, 12, 1, 9, 0)).isPushEnabled());
    }

    @Test
    void 저장_전_방은_신규_방으로_본다() {
        assertTrue(roomCreatedAt(null).isPushEnabled());
    }
}
