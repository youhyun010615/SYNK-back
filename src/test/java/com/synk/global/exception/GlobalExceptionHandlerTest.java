package com.synk.global.exception;

import com.synk.dto.request.CreateRoomRequest;
import jakarta.validation.Valid;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 클라이언트 오류가 500 이 아니라 알맞은 4xx 로 응답되는지, 예상 못 한 오류와 CustomException 은 기존대로인지 검증한다. */
class GlobalExceptionHandlerTest {

    @RestController
    static class StubController {
        @PostMapping("/rooms")
        String create(@Valid @RequestBody CreateRoomRequest req) {
            return "ok";
        }

        @GetMapping("/boom")
        String boom() {
            throw new IllegalStateException("예상 못 한 오류");
        }

        @GetMapping("/custom")
        String custom() {
            throw new CustomException(ErrorCode.ROOM_NOT_FOUND);
        }
    }

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new StubController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private static String roomJson(int maxMembers) {
        return """
                {"name":"방","maxMembers":%d,"dailyMissionCount":2,
                 "missionStartTime":"10:00","missionEndTime":"22:00"}
                """.formatted(maxMembers);
    }

    @Test
    void 깨진_JSON은_400이다() throws Exception {
        mvc.perform(post("/rooms").contentType(MediaType.APPLICATION_JSON).content("{깨진 json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("요청 형식이 올바르지 않습니다."));
    }

    @Test
    void 입력값_검증_실패는_400이고_DTO에_지정한_메시지를_쓴다() throws Exception {
        mvc.perform(post("/rooms").contentType(MediaType.APPLICATION_JSON).content(roomJson(0)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("최대 인원은 1명 이상이어야 합니다."));
    }

    @Test
    void 허용되지_않은_메서드는_405다() throws Exception {
        mvc.perform(get("/rooms"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void 없는_경로_예외는_404다() {
        ResponseEntity<?> res = new GlobalExceptionHandler()
                .handleNoResource(new NoResourceFoundException(HttpMethod.GET, "/debug", "debug"));
        assertEquals(404, res.getStatusCode().value());
        assertFalse(((com.synk.global.response.ApiResponse<?>) res.getBody()).isSuccess());
    }

    @Test
    void 예상_못_한_오류는_여전히_500이다() throws Exception {
        mvc.perform(get("/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("서버 오류가 발생했습니다."));
    }

    @Test
    void CustomException은_기존대로_ErrorCode_상태를_쓴다() throws Exception {
        mvc.perform(get("/custom"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("존재하지 않는 방입니다."));
    }
}
