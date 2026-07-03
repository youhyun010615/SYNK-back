package com.synk.controller;

import com.synk.dto.request.SynklogCallbackRequest;
import com.synk.dto.response.SynklogResponse;
import com.synk.global.response.ApiResponse;
import com.synk.service.AlbumService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class SynklogCallbackController {

    private final AlbumService albumService;

    @PostMapping("/api/synklogs/callback")
    public ResponseEntity<ApiResponse<SynklogResponse>> callback(
            @RequestHeader("X-Callback-Secret") String secret,
            @RequestBody SynklogCallbackRequest request) {
        SynklogResponse response = albumService.handleSynklogCallback(secret, request);
        return ResponseEntity.ok(ApiResponse.success(response, "SYNKLOG 콜백 처리 완료"));
    }
}
