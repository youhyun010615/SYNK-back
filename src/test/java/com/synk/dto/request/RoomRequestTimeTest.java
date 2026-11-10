package com.synk.dto.request;

import tools.jackson.databind.DatabindException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RoomRequestTimeTest {

    // Spring Boot 4 는 Jackson 3 를 사용한다. Jackson 2(com.fasterxml) 매퍼로 테스트하면 실제 동작과 달라진다.
    private final ObjectMapper mapper = JsonMapper.builder().build();

    private static String createJson(String start, String end) {
        return """
                {"name":"방","maxMembers":3,"dailyMissionCount":2,
                 "missionStartTime":"%s","missionEndTime":"%s"}
                """.formatted(start, end);
    }

    @Test
    void 종료시각_24시는_하루의_끝으로_해석한다() throws Exception {
        CreateRoomRequest req = mapper.readValue(createJson("10:00", "24:00"), CreateRoomRequest.class);
        assertEquals(LocalTime.of(10, 0), req.getMissionStartTime());
        assertEquals(LocalTime.of(23, 59, 59), req.getMissionEndTime());
    }

    @Test
    void 종료시각_일반값은_기존처럼_파싱한다() throws Exception {
        CreateRoomRequest req = mapper.readValue(createJson("09:15", "22:00"), CreateRoomRequest.class);
        assertEquals(LocalTime.of(22, 0), req.getMissionEndTime());
    }

    @Test
    void 방설정_수정도_24시를_허용한다() throws Exception {
        UpdateRoomRequest req = mapper.readValue("{\"missionEndTime\":\"24:00\"}", UpdateRoomRequest.class);
        assertEquals(LocalTime.of(23, 59, 59), req.getMissionEndTime());
    }

    @Test
    void 시작시각_24시는_계속_거부한다() {
        assertThrows(DatabindException.class,
                () -> mapper.readValue(createJson("24:00", "22:00"), CreateRoomRequest.class));
    }

    @Test
    void 종료시각_잘못된_형식은_거부한다() {
        assertThrows(DatabindException.class,
                () -> mapper.readValue(createJson("10:00", "25:00"), CreateRoomRequest.class));
    }
}
