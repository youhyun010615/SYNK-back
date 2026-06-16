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

    // type: profile / room / video
    @GetMapping("/presigned-url")
    public ResponseEntity<ApiResponse<Map<String, String>>> getPresignedUrl(
            @RequestParam String filename,
            @RequestParam(defaultValue = "video") String type) {
        Map<String, String> result = uploadService.generatePresignedUrl(filename, type);
        return ResponseEntity.ok(ApiResponse.success(result, "Presigned URL 발급 성공"));
    }
}
