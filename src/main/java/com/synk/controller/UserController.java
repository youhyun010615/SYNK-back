// 유저 관련 API 앤드포인트
// 요청을 받아서 UserService에 넘기고 응답을 돌려준다

package com.synk.controller;

import com.synk.dto.request.UpdateNotificationRequest;
import com.synk.dto.request.UpdateProfileRequest;
import com.synk.dto.response.UserResponse;
import com.synk.global.response.ApiResponse;
import com.synk.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> getMyProfile() {
        UserResponse response = userService.getMyProfile();
        return ResponseEntity.ok(ApiResponse.success(response, "프로필 조회 성공"));
    }

    @PatchMapping("/me")
    public ResponseEntity<ApiResponse<Void>> updateProfile(@RequestBody
                                                           UpdateProfileRequest request) {
        userService.updateProfile(request);
        return ResponseEntity.ok(ApiResponse.success("프로필 수정 완료"));
    }

    @PatchMapping("/me/notifications")
    public ResponseEntity<ApiResponse<Void>> updateNotification(@RequestBody
                                                                UpdateNotificationRequest request) {
        userService.updateNotification(request);
        return ResponseEntity.ok(ApiResponse.success("알림 설정 변경 완료"));
    }

    @DeleteMapping("/me")
    public ResponseEntity<ApiResponse<Void>> withdraw() {
        userService.withdraw();
        return ResponseEntity.ok(ApiResponse.success("회원 탈퇴 완료"));
    }
}
