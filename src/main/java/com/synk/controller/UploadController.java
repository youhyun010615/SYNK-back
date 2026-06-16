package com.synk.controller;

import com.synk.global.response.ApiResponse;
import com.synk.service.UploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/upload")
@RequiredArgsConstructor
public class UploadController {

    private final UploadService uploadService;

    @GetMapping("/presigned-url")
    public ResponseEntity<ApiResponse<Map<String, String>>> getPresignedUrl(
            @RequestParam String filename) {
        String result = uploadService.generatePresignedUrl(filename);
        String[] parts = result.split("\\|");
        return ResponseEntity.ok(ApiResponse.success(
                Map.of("presignedUrl", parts[0], "videoUrl", parts[1]),
                "Presigned URL 발급 성공"
        ));
    }
}
