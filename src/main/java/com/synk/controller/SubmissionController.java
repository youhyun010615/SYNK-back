package com.synk.controller;

import com.synk.dto.request.SubmissionRequest;
import com.synk.dto.response.SubmissionDetailResponse;
import com.synk.dto.response.SubmissionResponse;
import com.synk.dto.response.SubmissionStatusResponse;
import com.synk.global.response.ApiResponse;
import com.synk.service.SubmissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/submissions")
@RequiredArgsConstructor
public class SubmissionController {

    private final SubmissionService submissionService;

    @PostMapping
    public ResponseEntity<ApiResponse<SubmissionResponse>>
    submit(@RequestBody SubmissionRequest request) {
        SubmissionResponse response =
                submissionService.submit(request);
        return ResponseEntity.ok(ApiResponse.success(response, "제출 완료"));
    }

    @GetMapping("/missions/{missionId}")
    public ResponseEntity<ApiResponse<SubmissionStatusResponse>>
    getSubmissionStatus(
            @PathVariable Long missionId) {
        SubmissionStatusResponse response =
                submissionService.getSubmissionStatus(missionId);
        return ResponseEntity.ok(ApiResponse.success(response, "참여 현황 조회 성공"));
    }

    @GetMapping("/{submissionId}")
    public ResponseEntity<ApiResponse<SubmissionDetailResponse>>
    getSubmission(
            @PathVariable Long submissionId) {
        SubmissionDetailResponse response =
                submissionService.getSubmission(submissionId);
        return ResponseEntity.ok(ApiResponse.success(response, "영상 조회 성공"));
    }
}

