package com.synk.controller;

import com.synk.dto.request.CollageCallbackRequest;
import com.synk.dto.response.CollageResponse;
import com.synk.global.response.ApiResponse;
import com.synk.service.CollageService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class CollageController {

    private final CollageService collageService;

    @PostMapping("/api/collages/callback")
    public ResponseEntity<Void> callback(
            @RequestHeader("X-Callback-Secret") String secret,
            @RequestBody CollageCallbackRequest request) {
        collageService.handleCallback(secret, request);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/api/rooms/{roomId}/albums/{date}/collages")
    public ResponseEntity<ApiResponse<List<CollageResponse>>> getCollages(
            @PathVariable Long roomId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        List<CollageResponse> response = collageService.getCollagesForDate(roomId, date);
        return ResponseEntity.ok(ApiResponse.success(response, "콜라주 목록 조회 성공"));
    }

    @GetMapping("/api/missions/{missionId}/collage")
    public ResponseEntity<ApiResponse<CollageResponse>> getCollageByMission(
            @PathVariable Long missionId) {
        CollageResponse response = collageService.getCollageByMission(missionId);
        return ResponseEntity.ok(ApiResponse.success(response, "콜라주 조회 성공"));
    }
}
