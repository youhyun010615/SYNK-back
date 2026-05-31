package com.synk.controller;

import com.synk.dto.response.ActiveMissionResponse;
import com.synk.dto.response.MissionParticipantResponse;
import com.synk.global.response.ApiResponse;
import com.synk.service.MissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/missions")
@RequiredArgsConstructor
public class MissionController {

    private final MissionService missionService;

    @GetMapping("/active")
    public ResponseEntity<ApiResponse<List<ActiveMissionResponse>>>
    getActiveMissions(
            @RequestParam(required = false) Long roomId) {
        List<ActiveMissionResponse> response =
                missionService.getActiveMissions(roomId);
        return ResponseEntity.ok(ApiResponse.success(response, "진행 중인 미션 조회 성공"));
    }

    @GetMapping("/{missionId}/participants")
    public ResponseEntity<ApiResponse<MissionParticipantResponse>>
    getMissionParticipants(
            @PathVariable Long missionId) {
        MissionParticipantResponse response =
                missionService.getMissionParticipants(missionId);
        return ResponseEntity.ok(ApiResponse.success(response, "참여 현황 조회 성공"));
    }
}
