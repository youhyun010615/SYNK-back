package com.synk.dto.request;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 서버가 실제로 요청 본문을 읽는 경로(Spring MVC 의 기본 JSON 메시지 컨버터)로 검증한다.
 * 단순 ObjectMapper 단위 테스트는 Jackson 2/3 차이처럼 실제 동작과 달라지는 문제를 놓칠 수 있다.
 */
class RoomRequestHttpTest {

    @RestController
    static class StubController {
        @PostMapping("/api/rooms")
        String create(@RequestBody CreateRoomRequest req) {
            return req.getMissionStartTime() + "~" + req.getMissionEndTime();
        }

        @PatchMapping("/api/rooms/1")
        String update(@RequestBody UpdateRoomRequest req) {
            return String.valueOf(req.getMissionEndTime());
        }
    }

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new StubController()).build();
    }

    private static String body(String start, String end) {
        return """
                {"name":"방","maxMembers":3,"dailyMissionCount":2,
                 "missionStartTime":"%s","missionEndTime":"%s"}
                """.formatted(start, end);
    }

    @Test
    void 방_생성_종료시각_24시는_200이다() throws Exception {
        mvc.perform(post("/api/rooms").contentType(MediaType.APPLICATION_JSON).content(body("10:00", "24:00")))
                .andExpect(status().isOk())
                .andExpect(content().string("10:00~23:59:59"));
    }

    @Test
    void 방_생성_일반_종료시각은_그대로다() throws Exception {
        mvc.perform(post("/api/rooms").contentType(MediaType.APPLICATION_JSON).content(body("09:15", "22:00")))
                .andExpect(status().isOk())
                .andExpect(content().string("09:15~22:00"));
    }

    @Test
    void 방_설정수정_종료시각_24시는_200이다() throws Exception {
        mvc.perform(patch("/api/rooms/1").contentType(MediaType.APPLICATION_JSON).content("{\"missionEndTime\":\"24:00\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string("23:59:59"));
    }

    @Test
    void 시작시각_24시는_거부한다() throws Exception {
        mvc.perform(post("/api/rooms").contentType(MediaType.APPLICATION_JSON).content(body("24:00", "22:00")))
                .andExpect(status().is4xxClientError());
    }
}
