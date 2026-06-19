// 클라이언트 요청을 받아서 AuthService에 넘기고 응답을 돌려주는 컨트롤러

package com.synk.controller;

import com.synk.config.JwtProvider;
import com.synk.dto.request.AuthRequest;
import com.synk.dto.response.AuthResponse;
import com.synk.global.exception.CustomException;
import com.synk.global.exception.ErrorCode;
import com.synk.global.response.ApiResponse;
import com.synk.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final JwtProvider jwtProvider;

    @PostMapping("/kakao")
    public ResponseEntity<ApiResponse<AuthResponse>> kakaoLogin(@RequestBody AuthRequest request) {
        AuthResponse response = authService.kakaoLogin(request);
        return ResponseEntity.ok(ApiResponse.success(response, "로그인 성공"));
    }

    @PostMapping("/google")
    public ResponseEntity<ApiResponse<AuthResponse>> googleLogin(@RequestBody AuthRequest request) {
        AuthResponse response = authService.googleLogin(request);
        return ResponseEntity.ok(ApiResponse.success(response, "로그인 성공"));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<Map<String, String>>> refresh(@RequestBody Map<String, String> body) {
        String refreshToken = body.get("refreshToken");
        if (refreshToken == null || !jwtProvider.validateToken(refreshToken) || !jwtProvider.isRefreshToken(refreshToken)) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }
        Long userId = jwtProvider.getUserId(refreshToken);
        String newAccessToken = jwtProvider.generateToken(userId);
        return ResponseEntity.ok(ApiResponse.success(Map.of("token", newAccessToken), "토큰 갱신 성공"));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout() {
        return ResponseEntity.ok(ApiResponse.success("로그아웃 완료"));
    }
}

