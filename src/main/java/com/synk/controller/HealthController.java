package com.synk.controller;

import com.synk.global.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
public class HealthController {

    @GetMapping({"/", "/health"})
    public ResponseEntity<ApiResponse<Map<String, Object>>> health() {
        Map<String, Object> response = Map.of(
                "status", "ok",
                "service", "synk",
                "timestamp", Instant.now().toString()
        );

        return ResponseEntity.ok(ApiResponse.success(response, "서버 상태 정상"));
    }
}
